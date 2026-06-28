package logbook.internal.proxy;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jetty.io.EndPoint;
import org.slf4j.Logger;
import org.slf4j.MDC;

/**
 * プロキシのアクセスログをMDC経由で出力する。
 * <p>
 * テキスト形式・JSON形式のいずれも logback.xml の appender 設定で切り替え可能。
 * JSON 出力は {@code LogstashEncoder}（{@code includeMdc=true}）で MDC キーをトップレベルフィールドに展開する。
 * Java側では {@link #MDC_*} キーに値を設定し、メッセージは固定文字列とする。
 * </p>
 * <p><b>JettyスレッドプールとMDC:</b></p>
 * <ul>
 * <li>ReverseConnectHandler の I/O コールバックは Jetty Executor の再利用スレッド上で実行される</li>
 * <li>MDC はスレッドローカルのため、downstream / upstream で別スレッドから同時に
 *     {@code recordAccessLog} が呼ばれる可能性がある（アクセスログ本体は各スレッドで独立した MDC スコープ）</li>
 * <li>MDC は {@code logger.debug()} 呼び出しの瞬間だけ設定し、終了時に呼び出し前の MDC 状態を復元する。
 *     トランザクション全体に MDC を載せない（スレッド返却後の漏洩を防ぐ）</li>
 * </ul>
 * <p><b>keep-alive:</b> 接続再利用時の重複ログ防止は {@link ReverseConnectHandler.HttpClientConnectionListener}
 * 側の {@code accessLogged} フラグが担当する。MDC 自体はログ出力のたびに開閉する。</p>
 */
public final class ProxyAccessLogger
{
    /** 接続元IPアドレス */
    public static final String MDC_CLIENT_ADDR = "clientAddr";
    /** 接続元ポート */
    public static final String MDC_CLIENT_PORT = "clientPort";
    /** HTTPメソッド */
    public static final String MDC_METHOD = "method";
    /** リクエストURI（パスおよびクエリ） */
    public static final String MDC_URI = "uri";
    /** リクエストURIのパス部分（クエリ文字列を除く、集計用） */
    public static final String MDC_URI_PATH = "uriPath";
    /** Hostヘッダー値 */
    public static final String MDC_HOST = "host";
    /** HTTPステータスコード（レスポンス未受信時は0） */
    public static final String MDC_STATUS = "status";
    /** リクエストボディサイズ（バイト） */
    public static final String MDC_REQUEST_SIZE = "requestSize";
    /** レスポンスボディサイズ（圧縮後、バイト） */
    public static final String MDC_RESPONSE_SIZE = "responseSize";
    /** Content-Lengthヘッダー値（未設定時は-1） */
    public static final String MDC_CONTENT_LENGTH = "contentLength";
    /** Content-Encodingヘッダー値（未設定時は空文字） */
    public static final String MDC_CONTENT_ENCODING = "contentEncoding";
    /** レスポンスContent-Type（未設定時は空文字） */
    public static final String MDC_CONTENT_TYPE = "contentType";
    /** Transfer-Encodingヘッダー値（未設定時は空文字） */
    public static final String MDC_TRANSFER_ENCODING = "transferEncoding";
    /** HTTPバージョン（例: HTTP/1.1） */
    public static final String MDC_HTTP_VERSION = "httpVersion";
    /** 処理時間（ミリ秒、リクエスト行受信からアクセスログ記録まで） */
    public static final String MDC_ELAPSED_MS = "elapsedMs";
    /**
     * アップストリーム応答開始までの時間（ミリ秒、TTFB相当）。
     * リクエスト行受信からアップストリームのステータス行受信まで。
     */
    public static final String MDC_UPSTREAM_LATENCY_MS = "upstreamLatencyMs";
    /**
     * アップストリームからのレスポンスボディ受信時間（ミリ秒）。
     * ステータス行受信からレスポンス全体受信完了まで。
     */
    public static final String MDC_UPSTREAM_BODY_MS = "upstreamBodyMs";
    /**
     * プロキシ側オーバーヘッド（ミリ秒）。
     * elapsedMs - (responseComplete - requestStart)。解凍・リスナー処理等。
     */
    public static final String MDC_PROXY_OVERHEAD_MS = "proxyOverheadMs";
    /** トランザクション結果 */
    public static final String MDC_OUTCOME = "outcome";
    /** エラー詳細（正常時は空文字） */
    public static final String MDC_ERROR_DETAIL = "errorDetail";

    /**
     * アクセスログのトランザクション結果。
     */
    public enum Outcome
    {
        /** 正常完了 */
        COMPLETE,
        /** クライアント（ブラウザ）側からの切断 */
        CLIENT_DISCONNECT,
        /** アップストリーム（ゲームサーバー）側からの切断 */
        UPSTREAM_DISCONNECT,
        /** Content-Lengthと実受信サイズの不一致 */
        CONTENT_LENGTH_MISMATCH,
        /** レスポンス解析中の早期EOF */
        INCOMPLETE_RESPONSE,
        /** レスポンスボディの解凍失敗 */
        DECOMPRESS_ERROR
    }

    private ProxyAccessLogger()
    {
    }

    /**
     * アクセスログをMDC付きで出力する。
     *
     * @param logger アクセスログ用Logger（logbook.internal.proxy.AccessLog）
     * @param endPoint クライアント接続のEndPoint
     * @param request HTTPリクエスト
     * @param response HTTPレスポンス
     * @param transaction 現在のHTTPトランザクション
     * @param connectionStartTimeMillis 接続開始時刻（elapsedMsフォールバック用）
     * @param outcome トランザクション結果
     * @param errorDetail エラー詳細（正常時はnull可）
     */
    public static void log(
        Logger logger,
        EndPoint endPoint,
        CaptureHolder2.HttpRequest request,
        CaptureHolder2.HttpResponse response,
        CaptureHolder2.HttpTransaction transaction,
        long connectionStartTimeMillis,
        Outcome outcome,
        String errorDetail)
    {
        if (!logger.isDebugEnabled())
        {
            return;
        }

        ClientAddress clientAddress = resolveClientAddress(endPoint);
        long elapsedMs = resolveElapsedMs(transaction, connectionStartTimeMillis);
        TimingMetrics timing = resolveTimingMetrics(transaction, elapsedMs);
        Map<String, String> accessLogContext = buildAccessLogContext(
            clientAddress, request, response, elapsedMs, timing, outcome, errorDetail);

        try (AccessLogMdcScope ignored = AccessLogMdcScope.open(accessLogContext))
        {
            logger.debug("proxy access");
        }
    }

    /**
     * Throwableからエラー詳細文字列を生成する。
     *
     * @param cause 原因（null可）
     * @return エラー詳細文字列
     */
    public static String formatCause(Throwable cause)
    {
        if (cause == null)
        {
            return "";
        }
        String message = cause.getMessage();
        if (message == null || message.isEmpty())
        {
            return cause.getClass().getSimpleName();
        }
        return cause.getClass().getSimpleName() + ": " + message;
    }

    /**
     * アクセスログ用MDCスコープ。
     * 呼び出し前の MDC 状態を保存し、close 時に復元する（Jetty スレッドプール再利用対策）。
     */
    private static final class AccessLogMdcScope implements AutoCloseable
    {
        private final Map<String, String> previousContext;

        private AccessLogMdcScope(Map<String, String> previousContext)
        {
            this.previousContext = previousContext;
        }

        static AccessLogMdcScope open(Map<String, String> accessLogContext)
        {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            accessLogContext.forEach(MDC::put);
            return new AccessLogMdcScope(previous);
        }

        @Override
        public void close()
        {
            if (previousContext == null)
            {
                MDC.clear();
            }
            else
            {
                MDC.setContextMap(previousContext);
            }
        }
    }

    private static Map<String, String> buildAccessLogContext(
        ClientAddress clientAddress,
        CaptureHolder2.HttpRequest request,
        CaptureHolder2.HttpResponse response,
        long elapsedMs,
        TimingMetrics timing,
        Outcome outcome,
        String errorDetail)
    {
        String uri = nullToDefault(request.getUri(), "/");
        Map<String, String> context = new HashMap<>();
        context.put(MDC_CLIENT_ADDR, clientAddress.host());
        context.put(MDC_CLIENT_PORT, String.valueOf(clientAddress.port()));
        context.put(MDC_METHOD, nullToDefault(request.getMethod(), "UNKNOWN"));
        context.put(MDC_URI, uri);
        context.put(MDC_URI_PATH, extractUriPath(uri));
        context.put(MDC_HOST, nullToEmpty(request.getHeaderIgnoreCase("Host")));
        context.put(MDC_STATUS, String.valueOf(response.getStatus()));
        context.put(MDC_REQUEST_SIZE, String.valueOf(request.getBodySize()));
        context.put(MDC_RESPONSE_SIZE, String.valueOf(response.getBodySize()));
        context.put(MDC_CONTENT_LENGTH, String.valueOf(response.getContentLength()));
        context.put(MDC_CONTENT_ENCODING, nullToEmpty(response.getHeaderIgnoreCase("Content-Encoding")));
        context.put(MDC_CONTENT_TYPE, nullToEmpty(response.getContentType()));
        context.put(MDC_TRANSFER_ENCODING, nullToEmpty(response.getHeaderIgnoreCase("Transfer-Encoding")));
        context.put(MDC_HTTP_VERSION, nullToEmpty(request.getVersion()));
        context.put(MDC_ELAPSED_MS, String.valueOf(elapsedMs));
        context.put(MDC_UPSTREAM_LATENCY_MS, String.valueOf(timing.upstreamLatencyMs()));
        context.put(MDC_UPSTREAM_BODY_MS, String.valueOf(timing.upstreamBodyMs()));
        context.put(MDC_PROXY_OVERHEAD_MS, String.valueOf(timing.proxyOverheadMs()));
        context.put(MDC_OUTCOME, outcome.name());
        context.put(MDC_ERROR_DETAIL, nullToEmpty(errorDetail));
        return context;
    }

    private static long resolveElapsedMs(CaptureHolder2.HttpTransaction transaction, long connectionStartTimeMillis)
    {
        long requestStartTime = transaction.getRequestStartTime();
        if (requestStartTime > 0)
        {
            return System.currentTimeMillis() - requestStartTime;
        }
        return System.currentTimeMillis() - connectionStartTimeMillis;
    }

    /**
     * URIからクエリ文字列を除いたパス部分を抽出する（Grafana/Loki集計用）。
     *
     * @param uri リクエストURI
     * @return パス部分
     */
    static String extractUriPath(String uri)
    {
        if (uri == null || uri.isEmpty())
        {
            return "/";
        }

        if (uri.startsWith("/") || !uri.contains("://"))
        {
            return stripQueryAndFragment(uri);
        }

        try
        {
            String path = URI.create(uri).getPath();
            if (path == null || path.isEmpty())
            {
                return "/";
            }
            return path;
        }
        catch (IllegalArgumentException e)
        {
            return stripQueryAndFragment(uri);
        }
    }

    private static String stripQueryAndFragment(String uri)
    {
        int end = uri.length();
        int queryIndex = uri.indexOf('?');
        if (queryIndex >= 0)
        {
            end = queryIndex;
        }
        int fragmentIndex = uri.indexOf('#');
        if (fragmentIndex >= 0 && fragmentIndex < end)
        {
            end = fragmentIndex;
        }
        String path = uri.substring(0, end);
        return path.isEmpty() ? "/" : path;
    }

    private static TimingMetrics resolveTimingMetrics(CaptureHolder2.HttpTransaction transaction, long elapsedMs)
    {
        long requestStart = transaction.getRequestStartTime();
        long responseStart = transaction.getResponseStartTime();
        long responseComplete = transaction.getResponseCompleteTime();

        long upstreamLatencyMs = -1;
        long upstreamBodyMs = -1;
        long proxyOverheadMs = -1;

        if (requestStart > 0 && responseStart > requestStart)
        {
            upstreamLatencyMs = responseStart - requestStart;
        }
        if (responseStart > 0 && responseComplete > responseStart)
        {
            upstreamBodyMs = responseComplete - responseStart;
        }
        if (requestStart > 0 && responseComplete > requestStart)
        {
            long upstreamTotalMs = responseComplete - requestStart;
            proxyOverheadMs = Math.max(0, elapsedMs - upstreamTotalMs);
        }

        return new TimingMetrics(upstreamLatencyMs, upstreamBodyMs, proxyOverheadMs);
    }

    private static ClientAddress resolveClientAddress(EndPoint endPoint)
    {
        if (endPoint == null)
        {
            return new ClientAddress("unknown", 0);
        }

        SocketAddress remote = endPoint.getRemoteSocketAddress();
        if (remote instanceof InetSocketAddress inet)
        {
            String host = inet.getHostString();
            if (host == null || host.isEmpty())
            {
                host = "unknown";
            }
            return new ClientAddress(host, inet.getPort());
        }

        String addr = remote != null ? remote.toString() : "unknown";
        if (addr.startsWith("/"))
        {
            addr = addr.substring(1);
        }
        int colonIndex = addr.indexOf(':');
        if (colonIndex > 0)
        {
            return new ClientAddress(addr.substring(0, colonIndex), parsePort(addr.substring(colonIndex + 1)));
        }
        return new ClientAddress(addr, 0);
    }

    private static int parsePort(String portText)
    {
        try
        {
            return Integer.parseInt(portText);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private static String nullToEmpty(String value)
    {
        return value != null ? value : "";
    }

    private static String nullToDefault(String value, String defaultValue)
    {
        return value != null ? value : defaultValue;
    }

    private record ClientAddress(String host, int port)
    {
    }

    private record TimingMetrics(long upstreamLatencyMs, long upstreamBodyMs, long proxyOverheadMs)
    {
    }
}
