package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link DevMode} のテスト。
 */
class DevModeTest {

    @AfterEach
    void tearDown() {
        System.clearProperty(DevMode.PROPERTY);
    }

    @Test
    void configureEnablesDevModeFromArgument() {
        DevMode.configure(new String[] { "--dev" });
        assertTrue(DevMode.isEnabled());
    }

    @Test
    void isEnabledFromSystemProperty() {
        System.setProperty(DevMode.PROPERTY, "true");
        assertTrue(DevMode.isEnabled());
    }

    @Test
    void isDisabledByDefault() {
        DevMode.configure(new String[] {});
        assertFalse(DevMode.isEnabled());
    }

    @Test
    void formatVersionDisplayWithoutDevModeOmitsBuildTimestamp() {
        Version version = new Version(26, 6, 3, "2026-02-24T06:01:23Z", null, null);
        assertEquals("26.6.3", DevMode.formatVersionDisplay(version));
    }

    @Test
    void formatVersionDisplayWithDevModeIncludesBuildTimestamp() {
        System.setProperty(DevMode.PROPERTY, "true");
        Version version = new Version(26, 6, 3, "2026-02-24T06:01:23Z", null, null);
        assertEquals("26.6.3-2026-02-24T06:01:23Z", DevMode.formatVersionDisplay(version));
    }
}
