package logbook.internal.capture;

/**
 * キャプチャキューに載せる 1 件分のデータ。
 *
 * @param requestId アクセスログと同一の相関 ID
 * @param method HTTP メソッド
 * @param uriPath パスのみ（{@link logbook.internal.proxy.UriPaths#normalize} と同一規則）
 * @param uri クエリを含むリクエスト URI
 * @param contentType レスポンス Content-Type（無い場合は null）
 * @param requestBody POST ボディ（エンコーディング済み文字列。POST 以外または空は null）
 * @param requestEncoding {@code utf8} / {@code base64}（requestBody が null のとき null）
 * @param responseBody レスポンスボディ（エンコーディング済み文字列）
 * @param responseEncoding {@code utf8} / {@code base64}
 */
public record ApiCaptureRecord(
        String requestId,
        String method,
        String uriPath,
        String uri,
        String contentType,
        String requestBody,
        String requestEncoding,
        String responseBody,
        String responseEncoding) {
}
