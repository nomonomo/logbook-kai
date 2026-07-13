package logbook.internal.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * {@link ApiCaptureBodies} のテスト。
 */
class ApiCaptureBodiesTest {

    @Test
    void readRequestBodySkipsNonPostEvenWhenBodyPresent() {
        // body が非空でも POST 以外は null（空 Optional 由来の null と区別するため）
        byte[] body = "api_token=abc".getBytes(StandardCharsets.UTF_8);
        assertNull(ApiCaptureBodies.readRequestBody(stubRequest("GET", body)));
    }

    @Test
    void readRequestBodyReturnsNullForEmptyPostBody() {
        assertNull(ApiCaptureBodies.readRequestBody(
                stubRequest("POST", new byte[0])));
        assertNull(ApiCaptureBodies.readRequestBody(
                stubRequest("POST", null)));
    }

    @Test
    void readRequestBodyReturnsPostBody() {
        String body = "api_token=abc&api_id=1";
        assertEquals(body, ApiCaptureBodies.readRequestBody(
                stubRequest("POST", body.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void readResponseBodyReturnsEmptyWhenMissing() {
        assertEquals("", ApiCaptureBodies.readResponseBody(null));
        assertEquals("", ApiCaptureBodies.readResponseBody(stubResponse(null)));
    }

    @Test
    void readResponseBodyPreservesRawTextIncludingSvdataPrefix() {
        String body = "svdata={\"api_result\":1}";
        assertEquals(body, ApiCaptureBodies.readResponseBody(stubResponse(body)));
    }

    private static RequestMetaData stubRequest(String method, byte[] body) {
        return new RequestMetaData() {
            @Override
            public String getContentType() {
                return "application/x-www-form-urlencoded";
            }

            @Override
            public String getMethod() {
                return method;
            }

            @Override
            public Map<String, List<String>> getParameterMap() {
                return Map.of();
            }

            @Override
            public String getQueryString() {
                return "";
            }

            @Override
            public String getRequestURI() {
                return "/kcsapi/api_port/port";
            }

            @Override
            public Optional<java.io.InputStream> getRequestBody() {
                if (body == null) {
                    return Optional.empty();
                }
                return Optional.of(new java.io.ByteArrayInputStream(body));
            }
        };
    }

    private static ResponseMetaData stubResponse(String body) {
        byte[] bytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : null;
        return new ResponseMetaData() {
            @Override
            public int getStatus() {
                return 200;
            }

            @Override
            public String getContentType() {
                return "text/plain";
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
