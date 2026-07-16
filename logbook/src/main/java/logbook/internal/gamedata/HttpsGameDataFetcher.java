package logbook.internal.gamedata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalLong;

/**
 * HTTPS のみを許可する {@link GameDataFetcher} 実装。
 */
final class HttpsGameDataFetcher implements GameDataFetcher {

    /** リクエスト待機 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    /** コピー用バッファサイズ */
    private static final int BUFFER_SIZE = 8192;

    private final HttpClient httpClient;

    HttpsGameDataFetcher() {
        this(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    HttpsGameDataFetcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void downloadTo(String url, long maxBytes, Path dest) throws IOException, InterruptedException {
        if (!url.startsWith("https://")) {
            throw new IOException("HTTPS 以外の URL は許可されていません: " + url);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<InputStream> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        URI responseUri = response.uri();
        if (responseUri == null || !"https".equalsIgnoreCase(responseUri.getScheme())) {
            throw new IOException("HTTPS 以外へのリダイレクトは許可されていません: " + responseUri);
        }
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + ": " + url);
        }
        rejectIfContentLengthExceeds(
            response.headers().firstValueAsLong("Content-Length"), maxBytes);
        if (dest.getParent() != null) {
            Files.createDirectories(dest.getParent());
        }
        try (InputStream in = response.body();
                OutputStream out = Files.newOutputStream(dest)) {
            copyLimited(in, out, maxBytes);
        } catch (IOException e) {
            Files.deleteIfExists(dest);
            throw e;
        }
    }

    /**
     * Content-Length が上限を超えている場合に拒否します（ヘッダ無し・不正は無視）。
     *
     * @param contentLength Content-Length
     * @param maxBytes 上限
     * @throws IOException 上限超過
     */
    static void rejectIfContentLengthExceeds(OptionalLong contentLength, long maxBytes)
            throws IOException {
        if (contentLength.isEmpty()) {
            return;
        }
        long length = contentLength.getAsLong();
        if (length < 0) {
            return;
        }
        if (length > maxBytes) {
            throw new IOException("Content-Length が上限を超えています（" + length + " > " + maxBytes + "）");
        }
    }

    /**
     * ストリームを最大サイズ制限付きでコピーします。
     * 読み取り中にカウントし、上限超過の時点で中止します。
     *
     * @param in 入力
     * @param out 出力
     * @param maxBytes 上限
     * @throws IOException 超過または読み取り失敗
     */
    static void copyLimited(InputStream in, OutputStream out, long maxBytes) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            if (total + n > maxBytes) {
                throw new IOException("レスポンスが大きすぎます（上限 " + maxBytes + " bytes）");
            }
            out.write(buf, 0, n);
            total += n;
        }
    }
}
