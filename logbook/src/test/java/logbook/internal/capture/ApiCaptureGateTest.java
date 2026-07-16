package logbook.internal.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import logbook.bean.AppConfig;
import logbook.internal.DevMode;

/**
 * {@link ApiCaptureGate} のテスト。
 */
class ApiCaptureGateTest {

    @BeforeEach
    @AfterEach
    void clearFlags() {
        System.clearProperty(ApiCaptureGate.UI_PROPERTY);
        System.clearProperty(ApiCaptureGate.DEV_GATE_PROPERTY);
        System.clearProperty(ApiCaptureGate.FORCE_PROPERTY);
        System.clearProperty(DevMode.PROPERTY);
    }

    @Test
    void uiHiddenWithoutUiFlag() {
        // 環境変数はテストから消せないため、未設定のときだけ検証する
        assumeTrue(System.getenv(ApiCaptureGate.UI_ENV) == null
                || System.getenv(ApiCaptureGate.UI_ENV).isBlank());
        assertFalse(ApiCaptureGate.isUiAvailable());
    }

    @Test
    void uiVisibleWithUiProperty() {
        System.setProperty(ApiCaptureGate.UI_PROPERTY, "true");
        assertTrue(ApiCaptureGate.isUiAvailable());
    }

    @Test
    void uiHiddenWhenUiPropertyIsNotTrue() {
        System.setProperty(ApiCaptureGate.UI_PROPERTY, "false");
        assumeTrue(System.getenv(ApiCaptureGate.UI_ENV) == null
                || System.getenv(ApiCaptureGate.UI_ENV).isBlank());
        assertFalse(ApiCaptureGate.isUiAvailable());
    }

    @Test
    void devGateHidesUiWithoutDevModeOrForce() {
        System.setProperty(ApiCaptureGate.UI_PROPERTY, "true");
        System.setProperty(ApiCaptureGate.DEV_GATE_PROPERTY, "true");
        assertFalse(ApiCaptureGate.isUiAvailable());
    }

    @Test
    void devGateAllowsUiWithDevMode() {
        System.setProperty(ApiCaptureGate.UI_PROPERTY, "true");
        System.setProperty(ApiCaptureGate.DEV_GATE_PROPERTY, "true");
        System.setProperty(DevMode.PROPERTY, "true");
        assertTrue(ApiCaptureGate.isUiAvailable());
    }

    @Test
    void devGateBypassWithForce() {
        System.setProperty(ApiCaptureGate.UI_PROPERTY, "true");
        System.setProperty(ApiCaptureGate.DEV_GATE_PROPERTY, "true");
        System.setProperty(ApiCaptureGate.FORCE_PROPERTY, "true");
        assertTrue(ApiCaptureGate.isUiAvailable());
    }

    @Test
    void captureInactiveWhenDisabled() {
        AppConfig config = activeConfig();
        config.setApiCaptureEnabled(false);
        assertFalse(ApiCaptureGate.isCaptureActive(config));
    }

    @Test
    void captureInactiveWithoutConsent() {
        AppConfig config = activeConfig();
        config.setApiCaptureConsentAccepted(false);
        assertFalse(ApiCaptureGate.isCaptureActive(config));
    }

    @Test
    void captureInactiveWhenDirBlank() {
        AppConfig config = activeConfig();
        config.setApiCaptureDir("  ");
        assertFalse(ApiCaptureGate.isCaptureActive(config));
    }

    @Test
    void captureInactiveWhenDirNull() {
        AppConfig config = activeConfig();
        config.setApiCaptureDir(null);
        assertFalse(ApiCaptureGate.isCaptureActive(config));
    }

    @Test
    void captureActiveWhenConfigured() {
        assertTrue(ApiCaptureGate.isCaptureActive(activeConfig()));
    }

    private static AppConfig activeConfig() {
        AppConfig config = new AppConfig();
        config.setApiCaptureEnabled(true);
        config.setApiCaptureConsentAccepted(true);
        config.setApiCaptureDir("./captures");
        return config;
    }
}
