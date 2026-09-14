package logbook.internal.capture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
        assertNull(ApiCaptureBodies.readRequestBody(stubRequest("GET", body, "application/x-www-form-urlencoded")));
    }

    @Test
    void readRequestBodyReturnsNullForEmptyPostBody() {
        assertNull(ApiCaptureBodies.readRequestBody(
                stubRequest("POST", new byte[0], "application/x-www-form-urlencoded")));
        assertNull(ApiCaptureBodies.readRequestBody(
                stubRequest("POST", null, "application/x-www-form-urlencoded")));
    }

    @Test
    void readRequestBodyReturnsPostBodyAsUtf8() {
        String body = "api_token=abc&api_id=1";
        ApiCaptureBodies.CapturedBody captured = ApiCaptureBodies.readRequestBody(
                stubRequest("POST", body.getBytes(StandardCharsets.UTF_8), "application/x-www-form-urlencoded"));
        assertEquals(ApiCaptureBodies.ENCODING_UTF8, captured.encoding());
        assertEquals(body, captured.content());
    }

    @Test
    void readResponseBodyReturnsEmptyWhenMissing() {
        assertEquals("", ApiCaptureBodies.readResponseBody(null).content());
        assertEquals(ApiCaptureBodies.ENCODING_UTF8, ApiCaptureBodies.readResponseBody(null).encoding());
        assertEquals("", ApiCaptureBodies.readResponseBody(stubResponse(null, "text/plain")).content());
    }

    @Test
    void readResponseBodyPreservesRawTextIncludingSvdataPrefix() {
        String body = "svdata={\"api_result\":1}";
        ApiCaptureBodies.CapturedBody captured = ApiCaptureBodies.readResponseBody(
                stubResponse(body.getBytes(StandardCharsets.UTF_8), "text/plain"));
        assertEquals(ApiCaptureBodies.ENCODING_UTF8, captured.encoding());
        assertEquals(body, captured.content());
    }

    @Test
    void encodeUsesBase64ForImagePng() {
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
        };
        ApiCaptureBodies.CapturedBody captured = ApiCaptureBodies.encode(png, "image/png");
        assertEquals(ApiCaptureBodies.ENCODING_BASE64, captured.encoding());
        assertArrayEquals(png, Base64.getDecoder().decode(captured.content()));
    }

    @Test
    void encodeUsesUtf8ForApplicationJson() {
        byte[] json = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        ApiCaptureBodies.CapturedBody captured = ApiCaptureBodies.encode(json, "application/json; charset=UTF-8");
        assertEquals(ApiCaptureBodies.ENCODING_UTF8, captured.encoding());
        assertEquals("{\"a\":1}", captured.content());
    }

    @Test
    void encodeSniffsBinaryWhenContentTypeMissing() {
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };
        ApiCaptureBodies.CapturedBody captured = ApiCaptureBodies.encode(png, null);
        assertEquals(ApiCaptureBodies.ENCODING_BASE64, captured.encoding());
        assertArrayEquals(png, Base64.getDecoder().decode(captured.content()));
    }

    @Test
    void encodeSniffsUtf8WhenContentTypeMissing() {
        byte[] text = "hello".getBytes(StandardCharsets.UTF_8);
        ApiCaptureBodies.CapturedBody captured = ApiCaptureBodies.encode(text, null);
        assertEquals(ApiCaptureBodies.ENCODING_UTF8, captured.encoding());
        assertEquals("hello", captured.content());
    }

    private static RequestMetaData stubRequest(String method, byte[] body, String contentType) {
        return new RequestMetaData() {
            @Override
            public String getContentType() {
                return contentType;
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

    private static ResponseMetaData stubResponse(byte[] bytes, String contentType) {
        return new ResponseMetaData() {
            @Override
            public int getStatus() {
                return 200;
            }

            @Override
            public String getContentType() {
                return contentType;
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
