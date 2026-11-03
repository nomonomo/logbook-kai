package logbook.internal.notify;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import logbook.bean.AppConfig;
import logbook.bean.DiscordConfig;
import logbook.bean.DiscordConfig.Kind;
import logbook.internal.Config;
import logbook.internal.notify.discord.DiscordWebhookRequest;
import logbook.notify.BadlyDamagedAlert;
import logbook.notify.BadlyDamagedAlert.Phase;
import logbook.notify.BadlyDamagedAlert.ShipNotice;
import logbook.notify.CondRecovered;
import logbook.notify.GimmickAchieved;
import logbook.notify.MissionReturning;

class DiscordNotificationListenerTest {

    @TempDir
    Path tempDir;

    private Config previous;

    private final DiscordNotificationListener listener = new DiscordNotificationListener();

    @BeforeEach
    void isolateConfig() {
        this.previous = Config.getDefault();
        Config.setDefaultForTest(new Config(this.tempDir));
    }

    @AfterEach
    void restoreConfig() {
        Config.setDefaultForTest(this.previous);
    }

    @Test
    void disabledDoesNotSend() {
        DiscordConfig config = this.ready();
        config.setEnable(false);
        assertNull(this.listener.toRequest(config, new MissionReturning(1, "第一艦隊", "遠征", false)));
    }

    @Test
    void blankUrlDoesNotSend() {
        DiscordConfig config = this.ready();
        config.setIncomingWebhookUrl("  ");
        assertNull(this.listener.toRequest(config, new MissionReturning(1, "第一艦隊", "遠征", false)));
    }

    @Test
    void disabledKindDoesNotSend() {
        DiscordConfig config = this.ready();
        config.setNotifies(Kind.MISSION, false);
        assertNull(this.listener.toRequest(config, new MissionReturning(1, "第一艦隊", "遠征", false)));
        config.setNotifies(Kind.COND, false);
        assertNull(this.listener.toRequest(config, new CondRecovered(1, "第一艦隊")));
        config.setNotifies(Kind.GIMMICK, false);
        assertNull(this.listener.toRequest(config, new GimmickAchieved(GimmickAchieved.Kind.ACHIEVED)));
    }

    @Test
    void remindDoesNotSend() {
        DiscordConfig config = this.ready();
        assertNull(this.listener.toRequest(config, new MissionReturning(2, "第二艦隊", "海上護衛任務", true)));
    }

    @Test
    void enabledMissionSends() {
        DiscordConfig config = this.ready();
        DiscordWebhookRequest request = this.listener.toRequest(config,
                new MissionReturning(2, "第二艦隊", "海上護衛任務", false));
        assertTrue(request.body().contains("遠征完了 #2"));
        assertTrue(request.body().contains("\"color\":" + Kind.MISSION.defaultColor()));
    }

    @Test
    void flagshipAloneIsDroppedWhenIgnored() {
        AppConfig.get().setIgnoreSecondFlagship(true);
        DiscordConfig config = this.ready();
        config.setNotifies(Kind.BADLY_SORTIE, true);
        BadlyDamagedAlert event = new BadlyDamagedAlert(Phase.SORTIE, List.of(
                new ShipNotice(1, "旗艦", "きかん", 99, true)));
        assertNull(this.listener.toRequest(config, event));
    }

    @Test
    void flagshipIsRemovedAndTheOtherShipIsSent() {
        AppConfig.get().setIgnoreSecondFlagship(true);
        DiscordConfig config = this.ready();
        config.setNotifies(Kind.BADLY_ADVANCE, true);
        BadlyDamagedAlert event = new BadlyDamagedAlert(Phase.ADVANCE, List.of(
                new ShipNotice(1, "旗艦", "きかん", 99, true),
                new ShipNotice(2, "随伴", "ずいはん", 20, false)));
        DiscordWebhookRequest request = this.listener.toRequest(config, event);
        assertTrue(request.body().contains("随伴(Lv20)が大破しました。"));
        assertFalse(request.body().contains("旗艦"));
        assertTrue(request.body().contains("\"color\":" + Kind.BADLY_ADVANCE.defaultColor()));
    }

    private DiscordConfig ready() {
        DiscordConfig config = new DiscordConfig();
        config.setIncomingWebhookUrl("https://example.invalid/hook");
        return config;
    }
}
