package logbook.internal.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import logbook.proxy.ResponseMetaData;

/**
 * {@link ApiCaptureHook} のテスト。
 */
class ApiCaptureHookTest {

    @Test
    void shouldCaptureResponseSkips304() {
        assertFalse(ApiCaptureHook.shouldCaptureResponse(
                stubResponse(HttpURLConnection.HTTP_NOT_MODIFIED, null)));
        assertTrue(ApiCaptureHook.shouldCaptureResponse(
                stubResponse(HttpURLConnection.HTTP_OK, "")));
        assertTrue(ApiCaptureHook.shouldCaptureResponse(
                stubResponse(HttpURLConnection.HTTP_OK, "body")));
        assertTrue(ApiCaptureHook.shouldCaptureResponse(null));
    }

    private static ResponseMetaData stubResponse(int status, String body) {
        byte[] bytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : null;
        return new ResponseMetaData() {
            @Override
            public int getStatus() {
                return status;
            }

            @Override
            public String getContentType() {
                return null;
            }

            @Override
            public Optional<java.io.InputStream> getResponseBody() {
                if (bytes == null) {
                    return Optional.empty();
                }
                return Optional.of(new java.io.ByteArrayInputStream(bytes));
            }
        };
    }
}
