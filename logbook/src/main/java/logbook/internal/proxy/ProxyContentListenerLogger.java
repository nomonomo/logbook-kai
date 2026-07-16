package logbook.internal.proxy;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.MDC;

import logbook.proxy.ContentListenerSpi;
import logbook.proxy.RequestMetaData;

/**
 * ContentListenerSpi / APIListenerSpi の処理時間を MDC 経由で出力する。
 * <p>
 * アクセスログ（{@link ProxyAccessLogger}）とは別ロガー・別ファイルに出力し、
 * {@link ProxyAccessLogger#MDC_REQUEST_ID} でアクセスログと紐づける。
 * Virtual Thread 上で実行されるため、ログ出力のたびに MDC スコープを開閉する。
 * </p>
 * <p>
 * JSON 出力時の数値フィールド（{@link #MDC_ELAPSED_MS} 等）は SLF4J KeyValue で付与する。
 * テキスト出力は MDC の文字列値を使用する。
 * </p>
 */
public final class ProxyContentListenerLogger
{
    /** HTTPメソッド */
    public static final String MDC_METHOD = "method";
    /** リクエストURIのパス部分（クエリ文字列を除く、集計用） */
    public static final String MDC_URI_PATH = "uriPath";
    /** プロキシが付与したリクエスト相関ID */
    public static final String MDC_REQUEST_ID = "requestId";
    /** 処理層（{@link Layer} の値） */
    public static final String MDC_LAYER = "layer";
    /** 処理クラス名（FQCN） */
    public static final String MDC_HANDLER_CLASS = "handlerClass";
    /** accept() の処理時間（ミリ秒） */
    public static final String MDC_ELAPSED_MS = "elapsedMs";
    /** 処理結果 */
    public static final String MDC_OUTCOME = "outcome";
    /** エラー詳細（正常時は空文字） */
    public static final String MDC_ERROR_DETAIL = "errorDetail";

    /**
     * コンテンツリスナー処理の層。
     */
    public enum Layer
    {
        /** ContentListenerSpi（APIListener 等のディスパッチ） */
        DISPATCHER("dispatcher"),
        /** APIListenerSpi（ApiPortPort 等の実処理） */
        HANDLER("handler");

        private final String value;

        Layer(String value)
        {
            this.value = value;
        }

        public String value()
        {
            return value;
        }
    }

    /**
     * リスナー処理の結果。
     */
    public enum Outcome
    {
        /** 正常完了 */
        SUCCESS,
        /** accept() 実行中に例外 */
        ERROR
    }

    private ProxyContentListenerLogger()
    {
    }

    /**
     * ContentListenerSpi の処理ログを MDC 付きで出力する。
     */
    public static void log(
        Logger logger,
        ContentListenerSpi listener,
        RequestMetaData request,
        long elapsedMs,
        Outcome outcome,
        String errorDetail)
    {
        log(logger, listener.getClass().getName(), Layer.DISPATCHER, request, elapsedMs, outcome, errorDetail);
    }

    /**
     * 処理クラス単位のログを MDC 付きで出力する。
     */
    public static void log(
        Logger logger,
        String handlerClass,
        Layer layer,
        RequestMetaData request,
        long elapsedMs,
        Outcome outcome,
        String errorDetail)
    {
        if (!logger.isDebugEnabled())
        {
            return;
        }

        Map<String, String> context = buildContext(handlerClass, layer, request, elapsedMs, outcome, errorDetail);
        try (ContentListenerMdcScope ignored = ContentListenerMdcScope.open(context))
        {
            logger.atDebug()
                .addKeyValue(MDC_ELAPSED_MS, elapsedMs)
                .log("proxy content listener");
        }
    }

    /**
     * Throwable からエラー詳細文字列を生成する。
     */
    public static String formatCause(Throwable cause)
    {
        return ProxyAccessLogger.formatCause(cause);
    }

    private static final class ContentListenerMdcScope implements AutoCloseable
    {
        private final Map<String, String> previousContext;

        private ContentListenerMdcScope(Map<String, String> previousContext)
        {
            this.previousContext = previousContext;
        }

        static ContentListenerMdcScope open(Map<String, String> context)
        {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            context.forEach(MDC::put);
            return new ContentListenerMdcScope(previous);
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

    private static Map<String, String> buildContext(
        String handlerClass,
        Layer layer,
        RequestMetaData request,
        long elapsedMs,
        Outcome outcome,
        String errorDetail)
    {
        Map<String, String> context = new HashMap<>();
        context.put(MDC_METHOD, nullToDefault(request.getMethod(), "UNKNOWN"));
        context.put(MDC_URI_PATH, nullToDefault(request.getUriPath(), "/"));
        context.put(ProxyAccessLogger.MDC_REQUEST_ID, nullToEmpty(request.getRequestId()));
        context.put(MDC_LAYER, layer.value());
        context.put(MDC_HANDLER_CLASS, handlerClass);
        context.put(MDC_ELAPSED_MS, String.valueOf(elapsedMs));
        context.put(MDC_OUTCOME, outcome.name());
        context.put(MDC_ERROR_DETAIL, nullToEmpty(errorDetail));
        return context;
    }

    private static String nullToEmpty(String value)
    {
        return value != null ? value : "";
    }

    private static String nullToDefault(String value, String defaultValue)
    {
        return value != null ? value : defaultValue;
    }
}
