package logbook.bean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link AppConfig#applyDefaults()} のテスト。
 */
class AppConfigApplyDefaultsTest {

    @Test
    void applyDefaults_proxySslUseRootCertificate_nullAndServerPathEmpty_setsTrue() {
        AppConfig config = new AppConfig();
        config.setProxySslUseRootCertificate(null);
        config.setServerCertificatePath(null);

        config.applyDefaults();

        assertTrue(Boolean.TRUE.equals(config.getProxySslUseRootCertificate()));
    }

    @Test
    void applyDefaults_proxySslUseRootCertificate_nullAndServerPathSet_setsFalse() {
        AppConfig config = new AppConfig();
        config.setProxySslUseRootCertificate(null);
        config.setServerCertificatePath("kancolle.p12");

        config.applyDefaults();

        assertFalse(Boolean.TRUE.equals(config.getProxySslUseRootCertificate()));
    }

    @Test
    void applyDefaults_proxySslUseRootCertificate_explicitTrue_keepsTrue() {
        AppConfig config = new AppConfig();
        config.setProxySslUseRootCertificate(Boolean.TRUE);
        config.setServerCertificatePath("kancolle.p12");

        config.applyDefaults();

        assertTrue(Boolean.TRUE.equals(config.getProxySslUseRootCertificate()));
    }

    @Test
    void applyDefaults_proxySslUseRootCertificate_explicitFalse_keepsFalse() {
        AppConfig config = new AppConfig();
        config.setProxySslUseRootCertificate(Boolean.FALSE);
        config.setServerCertificatePath(null);

        config.applyDefaults();

        assertFalse(Boolean.TRUE.equals(config.getProxySslUseRootCertificate()));
    }
}
