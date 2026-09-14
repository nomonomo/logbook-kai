package logbook.internal.capture;

import java.net.HttpURLConnection;

import lombok.extern.slf4j.Slf4j;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * プロキシ {@code invoke()} 入口から API キャプチャを行う。
 * <p>
 * 記録可否（{@link ApiCaptureGate}）と URI 対象（{@link ApiCapturePolicy}）の判定はここだけで行う。
 * </p>
 */
@Slf4j
public final class ApiCaptureHook {

    private ApiCaptureHook() {
    }

    /**
     * 記録が有効かつ対象 URI の場合、ボディをキューへ追加する。
     * <p>
     * {@code 304 Not Modified} はボディを持たないため記録しない（アクセスログの {@code requestId} で突合可能）。
     * </p>
     */
    public static void captureIfNeeded(RequestMetaData request, ResponseMetaData response) {
        if (!ApiCaptureGate.isCaptureActive()) {
            return;
        }
        String uri = request.getRequestURI();
        if (!ApiCapturePolicy.shouldCapture(uri)) {
            return;
        }
        if (!shouldCaptureResponse(response)) {
            log.debug("APIキャプチャをスキップ（304 Not Modified）: uriPath={}", request.getUriPath());
            return;
        }
        ApiCaptureBodies.CapturedBody requestBody = ApiCaptureBodies.readRequestBody(request);
        ApiCaptureBodies.CapturedBody responseBody = ApiCaptureBodies.readResponseBody(response);
        ApiCaptureRecord record = new ApiCaptureRecord(
                request.getRequestId(),
                request.getMethod(),
                request.getUriPath(),
                uri,
                blankToNull(response != null ? response.getContentType() : null),
                requestBody != null ? requestBody.content() : null,
                requestBody != null ? requestBody.encoding() : null,
                responseBody.content(),
                responseBody.encoding());
        ApiCaptureWriter.enqueue(record);
    }

    /**
     * レスポンスをキャプチャ対象とするか。{@code 304} はボディを持たないため除外する。
     */
    static boolean shouldCaptureResponse(ResponseMetaData response) {
        return response == null || response.getStatus() != HttpURLConnection.HTTP_NOT_MODIFIED;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
