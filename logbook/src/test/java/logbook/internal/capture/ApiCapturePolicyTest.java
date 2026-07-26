package logbook.internal.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link ApiCapturePolicy} のテスト。
 */
class ApiCapturePolicyTest {

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/logbook/capture/api-capture-rules-test.properties")) {
            if (in == null) {
                throw new IllegalStateException("test rules resource missing");
            }
            ApiCapturePolicy.replaceRulesForTest(ApiCaptureRulesLoader.parse(in));
        }
    }

    @Test
    void capturesKcsapi() {
        assertTrue(ApiCapturePolicy.shouldCapture("/kcsapi/api_port/port"));
        assertTrue(ApiCapturePolicy.shouldCapture("/kcsapi/api_port/port?api_token=1"));
    }

    @Test
    void capturesKcs2ClientAssets() {
        assertTrue(ApiCapturePolicy.shouldCapture("/kcs2/js/main.js?version=6.3.2.0"));
        assertTrue(ApiCapturePolicy.shouldCapture("/kcs2/version.json"));
    }

    @Test
    void skipsNonTargetPaths() {
        assertFalse(ApiCapturePolicy.shouldCapture("/assets/foo.png"));
        assertFalse(ApiCapturePolicy.shouldCapture(null));
        assertFalse(ApiCapturePolicy.shouldCapture("/kcs2/hc.html"));
        assertFalse(ApiCapturePolicy.shouldCapture(
                "/kcs2/index.php?version=6.3.2.0&api_token=secret"));
        assertFalse(ApiCapturePolicy.shouldCapture("/kcs2/img/common/common_main.json?version=6.3.2.0"));
        assertFalse(ApiCapturePolicy.shouldCapture("/kcs2/img/battle/battle_main.png?version=6.3.2.0"));
        assertFalse(ApiCapturePolicy.shouldCapture("/kcs2/resources/ship/banner/0593_6277.png"));
    }

    @Test
    void registerAdditionalRule() {
        ApiCaptureTargetRule rule = ApiCaptureTargetRule.prefix("/custom-api/");
        ApiCapturePolicy.register(rule);
        assertTrue(ApiCapturePolicy.shouldCapture("/custom-api/foo"));
    }

    @Test
    void emptyRulesCaptureNothing() {
        ApiCapturePolicy.replaceRulesForTest(List.of());
        assertFalse(ApiCapturePolicy.shouldCapture("/kcsapi/api_port/port"));
        assertFalse(ApiCapturePolicy.shouldCapture("/kcs2/js/main.js"));
    }
}
