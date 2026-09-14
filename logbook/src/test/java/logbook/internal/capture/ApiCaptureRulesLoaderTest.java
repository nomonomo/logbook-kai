package logbook.internal.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * {@link ApiCaptureRulesLoader} のテスト。
 */
class ApiCaptureRulesLoaderTest {

    @Test
    void parsesPrefixKeysInNumericOrder() throws Exception {
        String text = """
                prefix.10=/later/
                prefix.2=/kcs2/js/
                prefix.1=/kcsapi/
                """;
        List<ApiCaptureTargetRule> rules = ApiCaptureRulesLoader.parse(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(3, rules.size());
        assertTrue(rules.get(0).matches("/kcsapi/api_port/port"));
        assertTrue(rules.get(1).matches("/kcs2/js/main.js"));
        assertTrue(rules.get(2).matches("/later/x"));
        assertFalse(rules.get(0).matches("/kcs2/js/main.js"));
    }

    @Test
    void emptyPropertiesYieldNoRules() {
        assertTrue(ApiCaptureRulesLoader.parse(new Properties()).isEmpty());
    }

    @Test
    void ignoresBlankPrefixValues() throws Exception {
        String text = """
                prefix.1=/kcsapi/
                prefix.2=
                """;
        List<ApiCaptureTargetRule> rules = ApiCaptureRulesLoader.parse(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, rules.size());
        assertTrue(rules.get(0).matches("/kcsapi/x"));
    }
}
