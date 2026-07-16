package logbook.internal.capture;

/**
 * キャプチャキューに載せる 1 件分のデータ。
 *
 * @param requestId アクセスログと同一の相関 ID
 * @param method HTTP メソッド
 * @param uriPath パスのみ（{@link logbook.internal.proxy.UriPaths#normalize} と同一規則）
 * @param requestBody POST ボディ原文（POST 以外または空の場合は null）
 * @param responseBody レスポンスボディ原文
 */
public record ApiCaptureRecord(
        String requestId,
        String method,
        String uriPath,
        String requestBody,
        String responseBody) {
}
