package logbook.internal.capture;

/**
 * API キャプチャ対象 URI の判定ルール。
 */
@FunctionalInterface
public interface ApiCaptureTargetRule {

    /**
     * 指定 URI がキャプチャ対象か。
     *
     * @param uri リクエスト URI（クエリ含む場合あり）
     */
    boolean matches(String uri);

    /**
     * パス prefix 一致ルール。
     */
    static ApiCaptureTargetRule prefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        return uri -> uri != null && uri.startsWith(prefix);
    }
}
