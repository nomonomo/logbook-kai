package logbook.internal.notify.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import logbook.bean.DiscordConfig;
import logbook.notify.BadlyDamagedAlert.ShipNotice;
import logbook.notify.CondRecovered;
import logbook.notify.GimmickAchieved;
import logbook.notify.MissionConditionAlert;
import logbook.notify.MissionReturning;
import logbook.notify.MissionStarted;
import logbook.notify.MissionStarted.FleetMission;
import logbook.notify.NdockCompleted;
import logbook.notify.PortSupplyCompleted;

class DiscordFormatterTest {

    private final DiscordFormatter formatter = new DiscordFormatter();

    @Test
    void testEmbedUsesFixedColor() {
        DiscordWebhookRequest request = this.formatter.formatTest("https://example.invalid/hook");
        assertEquals(204, request.expectedStatus());
        assertEquals("Discord", request.channelName());
        assertTrue(request.body().contains("テスト通知です。"));
        assertEmbedChrome(request, DiscordConfig.COLOR_TEST);
    }

    @Test
    void missionCompletedUsesHostMessage() {
        DiscordWebhookRequest request = this.formatter.missionReturning(
                "https://example.invalid/hook",
                new MissionReturning(2, "第二艦隊", "海上護衛任務", false),
                DiscordConfig.Kind.MISSION.defaultColor());
        assertTrue(request.body().contains("遠征完了 #2"));
        assertTrue(request.body().contains("第二艦隊がまもなく帰投します。"));
        assertEmbedChrome(request, DiscordConfig.Kind.MISSION.defaultColor());
    }

    @Test
    void ndockCompletedUsesHostMessage() {
        DiscordWebhookRequest request = this.formatter.ndock(
                "https://example.invalid/hook",
                new NdockCompleted(1, 10, "島風", "しまかぜ", 50),
                DiscordConfig.Kind.NDOCK.defaultColor());
        assertTrue(request.body().contains("修復完了 #1"));
        assertTrue(request.body().contains("島風"));
        assertEmbedChrome(request, DiscordConfig.Kind.NDOCK.defaultColor());
    }

    @Test
    void portSupplyUsesExpectedTimeMessage() {
        DiscordWebhookRequest request = this.formatter.portSupply(
                "https://example.invalid/hook",
                new PortSupplyCompleted(1, "第一艦隊"),
                DiscordConfig.Kind.PORT_SUPPLY.defaultColor());
        assertTrue(request.body().contains("母港給糧 #1"));
        assertTrue(request.body().contains("第一艦隊の給糧完了予想時刻になりました"));
        assertEmbedChrome(request, DiscordConfig.Kind.PORT_SUPPLY.defaultColor());
    }

    @Test
    void missionStartedUsesDiscordTimestamps() {
        long returnAt = 1_700_000_000_000L;
        MissionStarted started = new MissionStarted(List.of(
                new FleetMission(2, "第二艦隊", "海上護衛任務", true, returnAt),
                new FleetMission(3, "第三艦隊", "", false, 0L),
                new FleetMission(4, "第四艦隊", "", false, 0L)));
        DiscordWebhookRequest request = this.formatter.missionStarted(
                "https://example.invalid/hook", started, DiscordConfig.Kind.MISSION_STARTED.defaultColor());
        assertTrue(request.body().contains("遠征開始"));
        assertTrue(request.body().contains("海上護衛任務"));
        assertTrue(request.body().contains("未遠征"));
        assertTrue(request.body().contains("<t:1700000000:f>"));
        assertTrue(request.body().contains("<t:1700000000:R>"));
        assertEquals("終了予定: —", DiscordFormatter.formatDiscordTimestamps(0L));
        assertEmbedChrome(request, DiscordConfig.Kind.MISSION_STARTED.defaultColor());
    }

    @Test
    void jsonEscapesQuotesInFields() {
        MissionStarted started = new MissionStarted(List.of(
                new FleetMission(2, "第二\"艦隊\"", "任務\"A\"", true, 0L),
                new FleetMission(3, "第三艦隊", "", false, 0L),
                new FleetMission(4, "第四艦隊", "", false, 0L)));
        DiscordWebhookRequest request = this.formatter.missionStarted(
                "https://example.invalid/hook", started, DiscordConfig.Kind.MISSION_STARTED.defaultColor());
        assertTrue(request.body().contains("\\\""));
    }

    @Test
    void addedKindsUseToastWording() {
        DiscordWebhookRequest condition = this.formatter.missionCondition(
                "https://example.invalid/hook",
                new MissionConditionAlert(2, "第二艦隊", "海上護衛任務"),
                DiscordConfig.Kind.MISSION_CONDITION.defaultColor());
        assertTrue(condition.body().contains("「第二艦隊」は「海上護衛任務」の条件を満たしていません"));
        assertEmbedChrome(condition, DiscordConfig.Kind.MISSION_CONDITION.defaultColor());

        DiscordWebhookRequest cond = this.formatter.cond(
                "https://example.invalid/hook",
                new CondRecovered(1, "第一艦隊"),
                DiscordConfig.Kind.COND.defaultColor());
        assertTrue(cond.body().contains("第一艦隊の疲労抜け予想時刻になりました"));
        assertEmbedChrome(cond, DiscordConfig.Kind.COND.defaultColor());

        DiscordWebhookRequest badly = this.formatter.badly(
                "https://example.invalid/hook",
                List.of(new ShipNotice(1, "島風", "しまかぜ", 50, false)),
                DiscordConfig.Kind.BADLY_SORTIE.defaultColor());
        assertTrue(badly.body().contains("島風(Lv50)が大破しました。"));
        assertEmbedChrome(badly, DiscordConfig.Kind.BADLY_SORTIE.defaultColor());

        DiscordWebhookRequest gimmick = this.formatter.gimmick(
                "https://example.invalid/hook",
                new GimmickAchieved(GimmickAchieved.Kind.ACHIEVED),
                DiscordConfig.Kind.GIMMICK.defaultColor());
        assertTrue(gimmick.body().contains("ギミックの達成を確認しました。"));
        assertEmbedChrome(gimmick, DiscordConfig.Kind.GIMMICK.defaultColor());

        DiscordWebhookRequest sea = this.formatter.gimmick(
                "https://example.invalid/hook",
                new GimmickAchieved(GimmickAchieved.Kind.SEA_AREA_CHANGE),
                DiscordConfig.Kind.GIMMICK.defaultColor());
        assertTrue(sea.body().contains("海域に変化が確認されました。"));
    }

    private static void assertEmbedChrome(DiscordWebhookRequest request, int color) {
        assertTrue(request.body().contains("\"color\":" + color));
        assertTrue(request.body().contains("\"text\":\"" + DiscordWebhookJson.footerText()));
        assertTrue(request.body().contains("\"icon_url\":\"" + DiscordWebhookJson.FOOTER_ICON_URL + "\""));
        assertFalse(request.body().contains("\"iconUrl\""));
    }
}
