package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link Version} の表示形式のテスト。
 */
class VersionDisplayTest {

    @AfterEach
    void tearDown() {
        System.clearProperty(DevMode.PROPERTY);
    }

    @Test
    void toBaseStringReturnsSemanticVersion() {
        Version version = new Version(26, 6, 3, "2026-02-24T06:01:23Z", "航海日誌改", "nomonomo");
        assertEquals("26.6.3", version.toBaseString());
    }

    @Test
    void toStringReturnsSemanticVersionOnly() {
        System.setProperty(DevMode.PROPERTY, "true");
        Version version = new Version(26, 6, 3, "2026-02-24T06:01:23Z", null, null);
        assertEquals("26.6.3", version.toString());
    }

    @Test
    void legacyImplementationVersionKeepsTimestamp() {
        Version version = new Version("26.6.3-2026-02-24T06:01:23Z");
        assertEquals("2026-02-24T06:01:23Z", version.getBuildTimestamp());
        assertEquals("26.6.3", version.toBaseString());
    }

    @Test
    void fromManifestPrefersBuildTimestampAttribute() {
        Version legacyParsed = new Version("26.6.3", null, null);
        Version version = new Version(
                legacyParsed.getMajor(),
                legacyParsed.getMinor(),
                legacyParsed.getRevision(),
                "2026-02-24T06:01:23Z",
                null,
                null);
        assertEquals("26.6.3", version.toBaseString());
        assertEquals("2026-02-24T06:01:23Z", version.getBuildTimestamp());
    }
}
