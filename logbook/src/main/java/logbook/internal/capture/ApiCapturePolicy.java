package logbook.internal.capture;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * API キャプチャ対象 URI の判定。
 * <p>
 * 既定では {@code /kcsapi/} を対象とする。
 * {@link #register(ApiCaptureTargetRule)} でルールを追加できる。
 * </p>
 */
public final class ApiCapturePolicy {

    private static final List<ApiCaptureTargetRule> RULES = new CopyOnWriteArrayList<>(
            List.of(ApiCaptureTargetRule.prefix("/kcsapi/")));

    private ApiCapturePolicy() {
    }

    /**
     * キャプチャ対象ルールを末尾に追加する。
     */
    public static void register(ApiCaptureTargetRule rule) {
        RULES.add(Objects.requireNonNull(rule));
    }

    /**
     * いずれかのルールに一致する URI をキャプチャする。
     *
     * @param uri リクエスト URI（クエリ含む場合あり）
     */
    public static boolean shouldCapture(String uri) {
        if (uri == null) {
            return false;
        }
        for (ApiCaptureTargetRule rule : RULES) {
            if (rule.matches(uri)) {
                return true;
            }
        }
        return false;
    }
}
