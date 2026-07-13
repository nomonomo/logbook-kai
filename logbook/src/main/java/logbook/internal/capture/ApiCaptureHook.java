package logbook.internal.capture;

import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * プロキシ {@code invoke()} 入口から API キャプチャを行う。
 * <p>
 * 記録可否（{@link ApiCaptureGate}）と URI 対象（{@link ApiCapturePolicy}）の判定はここだけで行う。
 * </p>
 */
public final class ApiCaptureHook {

    private ApiCaptureHook() {
    }

    /**
     * 記録が有効かつ対象 URI の場合、ボディ原文をキューへ追加する。
     */
    public static void captureIfNeeded(RequestMetaData request, ResponseMetaData response) {
        if (!ApiCaptureGate.isCaptureActive()) {
            return;
        }
        String uri = request.getRequestURI();
        if (!ApiCapturePolicy.shouldCapture(uri)) {
            return;
        }
        String requestBody = ApiCaptureBodies.readRequestBody(request);
        String responseBody = ApiCaptureBodies.readResponseBody(response);
        ApiCaptureRecord record = new ApiCaptureRecord(
                request.getRequestId(),
                request.getMethod(),
                request.getUriPath(),
                requestBody,
                responseBody);
        ApiCaptureWriter.enqueue(record);
    }
}
