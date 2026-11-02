package logbook.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import logbook.bean.AppConfig.NotifyKind;
import logbook.internal.JsonMappers;

/**
 * 通知集合の初期化と、旧フラグからの移行。
 */
class AppConfigNotifyChannelsTest {

    @Test
    void applyDefaults_fillsChannelsFromLegacyFlags() {
        AppConfig config = new AppConfig();
        config.setUseToast(false);
        config.setUseSound(true);
        config.setUseNotification(true);
        config.setUseCondRecoverToast(true);
        config.applyDefaults();

        assertFalse(config.notifiesByToast(NotifyKind.MISSION));
        assertFalse(config.notifiesByToast(NotifyKind.NDOCK));
        assertFalse(config.notifiesByToast(NotifyKind.PORT_SUPPLY));
        assertFalse(config.notifiesByToast(NotifyKind.COND));
        assertTrue(config.notifiesByToast(NotifyKind.BADLY_SORTIE));
        assertTrue(config.notifiesByToast(NotifyKind.BADLY_ADVANCE));
        assertTrue(config.notifiesBySound(NotifyKind.MISSION));
        assertTrue(config.notifiesBySound(NotifyKind.NDOCK));
        assertTrue(config.notifiesBySound(NotifyKind.MISSION_CONDITION));
        assertTrue(config.notifiesBySound(NotifyKind.GIMMICK));
        assertFalse(config.notifiesBySound(NotifyKind.PORT_SUPPLY));
        assertFalse(config.notifiesBySound(NotifyKind.COND));
        assertTrue(config.notifiesBySound(NotifyKind.BADLY_SORTIE));
    }

    @Test
    void applyDefaults_keepsSavedChannelsAndForcesBadly() {
        AppConfig config = new AppConfig();
        config.setToastNotify(EnumSet.of(NotifyKind.GIMMICK));
        config.setSoundNotify(EnumSet.noneOf(NotifyKind.class));
        config.setUseToast(true);
        config.setUseNotification(true);
        config.applyDefaults();

        assertTrue(config.notifiesByToast(NotifyKind.GIMMICK));
        assertFalse(config.notifiesByToast(NotifyKind.MISSION));
        assertTrue(config.notifiesByToast(NotifyKind.BADLY_SORTIE));
        assertTrue(config.notifiesBySound(NotifyKind.BADLY_ADVANCE));
        assertFalse(config.notifiesBySound(NotifyKind.GIMMICK));
    }

    @Test
    void jsonRoundTrip_keepsChannels() throws Exception {
        AppConfig config = new AppConfig();
        config.applyDefaults();
        config.setToastNotify(EnumSet.of(NotifyKind.MISSION, NotifyKind.BADLY_SORTIE, NotifyKind.BADLY_ADVANCE));
        config.setSoundNotify(EnumSet.of(NotifyKind.NDOCK, NotifyKind.BADLY_SORTIE, NotifyKind.BADLY_ADVANCE));

        String json = JsonMappers.MAPPER.writeValueAsString(config);
        AppConfig loaded = JsonMappers.LENIENT_READER.forType(AppConfig.class).readValue(json);
        loaded.applyDefaults();

        assertTrue(loaded.notifiesByToast(NotifyKind.MISSION));
        assertFalse(loaded.notifiesByToast(NotifyKind.NDOCK));
        assertFalse(loaded.notifiesBySound(NotifyKind.MISSION));
        assertTrue(loaded.notifiesBySound(NotifyKind.NDOCK));
        assertTrue(loaded.notifiesByToast(NotifyKind.BADLY_ADVANCE));
    }

    @Test
    void applyDefaults_fillsSoundDirsAndKeepsWrittenPath() {
        AppConfig fresh = new AppConfig();
        fresh.applyDefaults();
        for (NotifyKind kind : NotifyKind.values()) {
            assertEquals(kind.defaultSoundDir(), fresh.soundDir(kind));
        }

        AppConfig config = new AppConfig();
        EnumMap<NotifyKind, String> dirs = new EnumMap<>(NotifyKind.class);
        dirs.put(NotifyKind.MISSION, "D:/sounds/mission/");
        dirs.put(NotifyKind.GIMMICK, "");
        config.setNotifySoundDir(dirs);
        config.applyDefaults();

        assertEquals("D:/sounds/mission/", config.soundDir(NotifyKind.MISSION));
        assertEquals(NotifyKind.NDOCK.defaultSoundDir(), config.soundDir(NotifyKind.NDOCK));
        assertEquals(NotifyKind.GIMMICK.defaultSoundDir(), config.soundDir(NotifyKind.GIMMICK));
        assertEquals(NotifyKind.BADLY_SORTIE.defaultSoundDir(), config.soundDir(NotifyKind.BADLY_ADVANCE));
    }

    @Test
    void applyDefaults_copiesRemindAndKeepsWrittenChannel() {
        AppConfig copied = new AppConfig();
        copied.setUseRemind(false);
        copied.applyDefaults();
        assertEquals(Boolean.FALSE, copied.getUseToastRemind());
        assertEquals(Boolean.FALSE, copied.getUseSoundRemind());

        AppConfig kept = new AppConfig();
        kept.setUseRemind(true);
        kept.setUseToastRemind(false);
        kept.applyDefaults();
        assertEquals(Boolean.FALSE, kept.getUseToastRemind());
        assertEquals(Boolean.TRUE, kept.getUseSoundRemind());
    }
}
