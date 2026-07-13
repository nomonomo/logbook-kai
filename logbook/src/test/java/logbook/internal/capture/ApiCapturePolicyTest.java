package logbook.internal.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link ApiCapturePolicy} のテスト。
 */
class ApiCapturePolicyTest {

    @Test
    void capturesKcsapi() {
        assertTrue(ApiCapturePolicy.shouldCapture("/kcsapi/api_port/port"));
        assertTrue(ApiCapturePolicy.shouldCapture("/kcsapi/api_port/port?api_token=1"));
    }

    @Test
    void skipsNonKcsapi() {
        assertFalse(ApiCapturePolicy.shouldCapture("/assets/foo.png"));
        assertFalse(ApiCapturePolicy.shouldCapture(null));
    }

    @Test
    void registerAdditionalRule() {
        ApiCaptureTargetRule rule = ApiCaptureTargetRule.prefix("/custom-api/");
        ApiCapturePolicy.register(rule);
        try {
            assertTrue(ApiCapturePolicy.shouldCapture("/custom-api/foo"));
        } finally {
            // CopyOnWriteArrayList から削除 API が無いため、他テストへの影響は許容
        }
    }
}
