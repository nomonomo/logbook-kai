package logbook.internal.notify;

import java.util.List;

import logbook.bean.DiscordConfig;
import logbook.bean.DiscordConfig.Kind;
import logbook.internal.notify.discord.DiscordFormatter;
import logbook.internal.notify.discord.DiscordWebhookClient;
import logbook.internal.notify.discord.DiscordWebhookRequest;
import logbook.notify.BadlyDamagedAlert;
import logbook.notify.BadlyDamagedAlert.ShipNotice;
import logbook.notify.CondRecovered;
import logbook.notify.GimmickAchieved;
import logbook.notify.MissionConditionAlert;
import logbook.notify.MissionReturning;
import logbook.notify.MissionStarted;
import logbook.notify.NdockCompleted;
import logbook.notify.Notification;
import logbook.notify.NotificationListener;
import logbook.notify.PortSupplyCompleted;

/**
 * Discord の Incoming Webhook へ通知を送ります。遠征完了の再通知は送りません。
 */
public final class DiscordNotificationListener implements NotificationListener {

    private final DiscordFormatter formatter = new DiscordFormatter();

    @Override
    public void onNotify(Notification event) {
        DiscordWebhookRequest request = this.toRequest(DiscordConfig.get(), event);
        if (request != null) {
            DiscordWebhookClient.shared().send(request, null, null);
        }
    }

    /**
     * この通知を送るならリクエストを返します。送らないときは null です。
     *
     * @param config 設定
     * @param event 通知
     * @return リクエスト。送らないときは null
     */
    DiscordWebhookRequest toRequest(DiscordConfig config, Notification event) {
        if (config == null || event == null || !config.isEnable() || !hasUrl(config)) {
            return null;
        }
        Kind kind = kind(event);
        if (kind == null || !config.notifies(kind)) {
            return null;
        }
        String url = config.getIncomingWebhookUrl().trim();
        int color = config.color(kind);
        return switch (event) {
            case MissionReturning e -> this.formatter.missionReturning(url, e, color);
            case MissionStarted e -> this.formatter.missionStarted(url, e, color);
            case NdockCompleted e -> this.formatter.ndock(url, e, color);
            case MissionConditionAlert e -> this.formatter.missionCondition(url, e, color);
            case PortSupplyCompleted e -> this.formatter.portSupply(url, e, color);
            case CondRecovered e -> this.formatter.cond(url, e, color);
            case BadlyDamagedAlert e -> this.badly(url, e, color);
            case GimmickAchieved e -> this.formatter.gimmick(url, e, color);
        };
    }

    /**
     * 通知に対応する設定種別を返します。送らない通知は null です。
     *
     * @param event 通知
     * @return 種別。送らないときは null
     */
    private static Kind kind(Notification event) {
        return switch (event) {
            case MissionReturning e -> e.remind() ? null : Kind.MISSION;
            case MissionStarted _ -> Kind.MISSION_STARTED;
            case NdockCompleted _ -> Kind.NDOCK;
            case MissionConditionAlert _ -> Kind.MISSION_CONDITION;
            case PortSupplyCompleted _ -> Kind.PORT_SUPPLY;
            case CondRecovered _ -> Kind.COND;
            case BadlyDamagedAlert e -> badlyKind(e);
            case GimmickAchieved e -> e.kind() == null ? null : Kind.GIMMICK;
        };
    }

    private static Kind badlyKind(BadlyDamagedAlert event) {
        if (event.phase() == null) {
            return null;
        }
        return switch (event.phase()) {
            case SORTIE -> Kind.BADLY_SORTIE;
            case ADVANCE -> Kind.BADLY_ADVANCE;
        };
    }

    private static boolean hasUrl(DiscordConfig config) {
        String url = config.getIncomingWebhookUrl();
        return url != null && !url.isBlank();
    }

    private DiscordWebhookRequest badly(String url, BadlyDamagedAlert event, int color) {
        List<ShipNotice> ships = BadlyDamageNotices.ships(event);
        if (ships.isEmpty()) {
            return null;
        }
        return this.formatter.badly(url, ships, color);
    }
}
