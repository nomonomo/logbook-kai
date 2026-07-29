package logbook.internal.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * API レスポンス JSON の未知フィールドを専用ロガーへ出力する。
 * <p>
 * リクエスト文脈（{@code uriPath} / {@code requestId} / {@code handlerClass}）は
 * {@link #openRequest(String, String, String)} で MDC に載せる。
 * 一般向け logback では appender を付けない（{@code additivity=false} かつ参照なし）。
 * </p>
 */
public final class ApiSchemaLog {

    /** ログイベント種別 */
    public static final String EVENT_API_UNKNOWN_FIELD = "api_unknown_field";

    /** イベント種別 */
    public static final String MDC_EVENT = "event";
    /** リクエスト URI パス */
    public static final String MDC_URI_PATH = "uriPath";
    /** JSON 上のオブジェクト位置 */
    public static final String MDC_JSON_PATH = "jsonPath";
    /** 未知のキー名 */
    public static final String MDC_FIELD = "field";
    /** ハンドラ FQCN */
    public static final String MDC_HANDLER_CLASS = "handlerClass";
    /** プロキシ相関 ID */
    public static final String MDC_REQUEST_ID = "requestId";

    private static final Logger LOG = LoggerFactory.getLogger("logbook.internal.api.ApiSchemaLog");

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private ApiSchemaLog() {
    }

    /**
     * ハンドラ処理中のリクエスト文脈を MDC に載せる。
     * Virtual Thread / Executor 上で実行されるため、try-with-resources で閉じる。
     *
     * @param uriPath リクエスト URI パス
     * @param requestId プロキシ相関 ID（null 可）
     * @param handlerClass ハンドラ FQCN
     * @return 閉じると MDC を復元するスコープ
     */
    public static Scope openRequest(String uriPath, String requestId, String handlerClass) {
        Objects.requireNonNull(handlerClass, "handlerClass");
        Map<String, String> context = new HashMap<>();
        context.put(MDC_URI_PATH, nullToEmpty(uriPath));
        context.put(MDC_REQUEST_ID, nullToEmpty(requestId));
        context.put(MDC_HANDLER_CLASS, handlerClass);
        return MdcScope.open(context);
    }

    /**
     * 未知フィールドを DEBUG で 1 回だけ報告する（uriPath + jsonPath + field で重複抑制）。
     * {@link #openRequest(String, String, String)} で載せた MDC を参照する。
     *
     * @param jsonPath JSON オブジェクト位置（例: {@code api_data.api_mst_ship[]}）
     * @param field 未知キー名
     */
    public static void unknownField(String jsonPath, String field) {
        if (!LOG.isDebugEnabled() || jsonPath == null || field == null) {
            return;
        }

        String uriPath = nullToEmpty(MDC.get(MDC_URI_PATH));

        String dedupKey = uriPath + '\0' + jsonPath + '\0' + field;
        if (!REPORTED.add(dedupKey)) {
            return;
        }

        Map<String, String> mdc = new HashMap<>();
        mdc.put(MDC_EVENT, EVENT_API_UNKNOWN_FIELD);
        mdc.put(MDC_JSON_PATH, jsonPath);
        mdc.put(MDC_FIELD, field);

        try (Scope ignored = MdcScope.open(mdc)) {
            LOG.debug("api unknown field");
        }
    }

    /**
     * テスト用に重複抑制キャッシュをクリアする。
     */
    static void clearReportedForTest() {
        REPORTED.clear();
    }

    /**
     * MDC スコープ。
     */
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    private static final class MdcScope implements Scope {

        private final Map<String, String> previousContext;

        private MdcScope(Map<String, String> previousContext) {
            this.previousContext = previousContext;
        }

        static Scope open(Map<String, String> context) {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            context.forEach(MDC::put);
            return new MdcScope(previous);
        }

        @Override
        public void close() {
            if (previousContext == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previousContext);
            }
        }
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
