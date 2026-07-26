package logbook.internal.capture;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * API キャプチャ対象 URI の判定。
 * <p>
 * ルール本文はプロパティ（{@link ApiCaptureRulesLoader}）から読む。
 * 配布ビルドは空ルールのため、記録を ON にしてもキャプチャされない。
 * 開発ビルドは {@code mvn -Pdev} で {@code dev/api-capture-rules.properties} を同梱する。
 * {@link #register(ApiCaptureTargetRule)} でプロセス内にルールを追加できる。
 * </p>
 */
public final class ApiCapturePolicy {

    private static final List<ApiCaptureTargetRule> RULES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private ApiCapturePolicy() {
    }

    /**
     * キャプチャ対象ルールを末尾に追加する。
     */
    public static void register(ApiCaptureTargetRule rule) {
        ensureLoaded();
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
        ensureLoaded();
        for (ApiCaptureTargetRule rule : RULES) {
            if (rule.matches(uri)) {
                return true;
            }
        }
        return false;
    }

    private static void ensureLoaded() {
        if (LOADED.compareAndSet(false, true)) {
            RULES.addAll(ApiCaptureRulesLoader.load());
        }
    }

    /**
     * テスト用にルールを差し替える。
     */
    static void replaceRulesForTest(List<ApiCaptureTargetRule> rules) {
        RULES.clear();
        RULES.addAll(Objects.requireNonNull(rules));
        LOADED.set(true);
    }
}
