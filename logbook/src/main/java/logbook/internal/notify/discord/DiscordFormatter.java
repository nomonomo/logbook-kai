package logbook.internal.notify.discord;

import java.util.ArrayList;
import java.util.List;

import logbook.Messages;
import logbook.bean.DiscordConfig;
import logbook.internal.notify.discord.DiscordWebhookPayload.EmbedField;
import logbook.notify.BadlyDamagedAlert.ShipNotice;
import logbook.notify.CondRecovered;
import logbook.notify.GimmickAchieved;
import logbook.notify.MissionConditionAlert;
import logbook.notify.MissionReturning;
import logbook.notify.MissionStarted;
import logbook.notify.MissionStarted.FleetMission;
import logbook.notify.NdockCompleted;
import logbook.notify.PortSupplyCompleted;
import tools.jackson.core.JacksonException;

/**
 * Discord Incoming Webhook 向けの Embed 本文。
 */
public final class DiscordFormatter {

    static final int EXPECTED_STATUS = 204;

    /**
     * テスト送信の本文を作ります。
     *
     * @param webhookUrl Webhook URL
     * @return リクエスト
     */
    public DiscordWebhookRequest formatTest(String webhookUrl) {
        return request(webhookUrl, DiscordWebhookJson.embed("テスト通知", "テスト通知です。", DiscordConfig.COLOR_TEST));
    }

    /**
     * 遠征完了の本文を作ります。
     *
     * @param webhookUrl Webhook URL
     * @param event 通知
     * @param color 色
     * @return リクエスト
     */
    public DiscordWebhookRequest missionReturning(String webhookUrl, MissionReturning event, int color) {
        String title = String.format("遠征完了 #%d", event.fleetId());
        String message = Messages.getString("mission.complete", event.fleetName());
        return request(webhookUrl, DiscordWebhookJson.embed(title, message, color));
    }

    /**
     * 入渠完了の本文を作ります。
     *
     * @param webhookUrl Webhook URL
     * @param event 通知
     * @param color 色
     * @return リクエスト
     */
    public DiscordWebhookRequest ndock(String webhookUrl, NdockCompleted event, int color) {
        String title = String.format("修復完了 #%d", event.dockId());
        String message = Messages.getString("ship.ndock", event.shipName(), event.level());
        return request(webhookUrl, DiscordWebhookJson.embed(title, message, color));
    }

    /**
     * 遠征開始の本文を作ります。
     *
     * @param webhookUrl Webhook URL
     * @param event 通知
     * @param color 色
     * @return リクエスト
     */
    public DiscordWebhookRequest missionStarted(String webhookUrl, MissionStarted event, int color) {
        List<EmbedField> fields = new ArrayList<>();
        for (FleetMission fleet : event.fleets()) {
            fields.add(toField(fleet));
        }
        return request(webhookUrl, DiscordWebhookJson.embedWithFields("遠征開始", fields, color));
    }

    /**
     * 母港給糧の本文を作ります。
     *
     * @param webhookUrl Webhook URL
     * @param event 通知
     * @param color 色
     * @return リクエスト
     */
    public DiscordWebhookRequest portSupply(String webhookUrl, PortSupplyCompleted event, int color) {
        String title = String.format("母港給糧 #%d", event.fleetId());
        String message = Messages.getString("portsupply.complete", event.fleetName());
        return request(webhookUrl, DiscordWebhookJson.embed(title, message, color));
    }

    /**
     * 遠征条件の本文を作ります。
     *
     * @param webhookUrl Webhook URL
     * @param event 通知
     * @param color 色
     * @return リクエスト
     */
    public DiscordWebhookRequest missionCondition(String webhookUrl, MissionConditionAlert event, int color) {
        String message = "「" + event.fleetName() + "」は「" + event.missionName() + "」の条件を満たしていません";
        return request(webhookUrl, DiscordWebhookJson.embed("遠征警告", message, color));
    }

    /**
     * 疲労回復の本文を作ります。
     *
     * @param webhookUrl Webhook URL
     * @param event 通知
     * @param color 色
     * @return リクエスト
     */
    public DiscordWebhookRequest cond(String webhookUrl, CondRecovered event, int color) {
        String message = Messages.getString("cond.recover", event.fleetName());
        return request(webhookUrl, DiscordWebhookJson.embed("疲労抜け", message, color));
    }

    /**
     * 大破の本文を作ります。艦ごとの文を1つの Embed にまとめます。
     *
     * @param webhookUrl Webhook URL
     * @param ships 通知する艦
     * @param color 色
     * @return リクエスト
     */
    public DiscordWebhookRequest badly(String webhookUrl, List<ShipNotice> ships, int color) {
        StringBuilder message = new StringBuilder();
        for (ShipNotice ship : ships) {
            if (!message.isEmpty()) {
                message.append('\n');
            }
            message.append(Messages.getString("ship.badly", ship.name(), ship.level()));
        }
        return request(webhookUrl, DiscordWebhookJson.embed("大破警告", message.toString(), color));
    }

    /**
     * ギミックの本文を作ります。
     *
     * @param webhookUrl Webhook URL
     * @param event 通知
     * @param color 色
     * @return リクエスト
     */
    public DiscordWebhookRequest gimmick(String webhookUrl, GimmickAchieved event, int color) {
        String message = event.kind() == GimmickAchieved.Kind.SEA_AREA_CHANGE
                ? "海域に変化が確認されました。"
                : "ギミックの達成を確認しました。";
        return request(webhookUrl, DiscordWebhookJson.embed("ギミック解除", message, color));
    }

    /**
     * 帰還予定の Discord 時刻記法です。
     *
     * @param returnAtMillis 帰還予定時刻（epoch milli）。0 以下は不明
     * @return 表示文字列
     */
    static String formatDiscordTimestamps(long returnAtMillis) {
        if (returnAtMillis <= 0) {
            return "終了予定: —";
        }
        long unixSeconds = returnAtMillis / 1000;
        return "終了予定: <t:" + unixSeconds + ":f>（<t:" + unixSeconds + ":R>）";
    }

    private static EmbedField toField(FleetMission fleet) {
        if (!fleet.underway()) {
            return new EmbedField(fleet.fleetName(), "未遠征", false);
        }
        return new EmbedField(
                fleet.fleetName(),
                fleet.missionName() + "\n" + formatDiscordTimestamps(fleet.returnAtMillis()),
                false);
    }

    private static DiscordWebhookRequest request(String webhookUrl, DiscordWebhookPayload payload) {
        try {
            return new DiscordWebhookRequest(webhookUrl, DiscordWebhookJson.write(payload), EXPECTED_STATUS, "Discord");
        } catch (JacksonException e) {
            throw new IllegalStateException("Discord 通知 JSON の生成に失敗しました", e);
        }
    }
}
