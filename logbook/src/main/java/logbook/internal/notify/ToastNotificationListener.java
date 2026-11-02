package logbook.internal.notify;

import java.util.Optional;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.util.Duration;
import logbook.Messages;
import logbook.bean.AppConfig;
import logbook.bean.AppConfig.NotifyKind;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.internal.Ships;
import logbook.internal.gui.Tools;
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
 * トーストを表示する。遠征開始は捨てる。
 */
public final class ToastNotificationListener implements NotificationListener {

    @Override
    public void onNotify(Notification event) {
        Platform.runLater(() -> show(event));
    }

    @Override
    public Optional<java.time.Duration> missionRemindInterval() {
        AppConfig config = AppConfig.get();
        if (!config.notifiesByToast(NotifyKind.MISSION) || !Boolean.TRUE.equals(config.getUseToastRemind())) {
            return Optional.empty();
        }
        if (config.getRemind() < 1) {
            return Optional.empty();
        }
        return Optional.of(java.time.Duration.ofSeconds(config.getRemind()));
    }

    private static void show(Notification event) {
        if (!visible(event)) {
            return;
        }
        switch (event) {
            case MissionReturning e -> showMission(e);
            case NdockCompleted e -> showNdock(e);
            case CondRecovered e -> showCond(e);
            case BadlyDamagedAlert e -> showBadly(e);
            case MissionConditionAlert e -> showMissionCondition(e);
            case GimmickAchieved e -> showGimmick(e);
            case PortSupplyCompleted e -> showPortSupply(e);
            case MissionStarted _ -> {
            }
        }
    }

    /**
     * この回をトーストへ出すかを返します。
     *
     * @param event 通知
     * @return 出すなら true
     */
    static boolean visible(Notification event) {
        return NotifyChannels.toast(event);
    }

    private static void showMission(MissionReturning event) {
        String message = Messages.getString("mission.complete", event.fleetName());
        Tools.Controls.showNotify(null, "遠征完了", message);
    }

    private static void showNdock(NdockCompleted event) {
        String message = Messages.getString("ship.ndock", event.shipName(), event.level());
        Tools.Controls.showNotify(graphic(event.shipId()), "修復完了", message);
    }

    private static void showCond(CondRecovered event) {
        String message = Messages.getString("cond.recover", event.fleetName());
        Tools.Controls.showNotify(null, "疲労抜け", message);
    }

    private static void showBadly(BadlyDamagedAlert event) {
        for (ShipNotice ship : BadlyDamageNotices.ships(event)) {
            String message = Messages.getString("ship.badly", ship.name(), ship.level());
            Tools.Controls.showNotify(graphic(ship.shipId()), "大破警告", message, Duration.seconds(30));
        }
    }

    private static void showMissionCondition(MissionConditionAlert event) {
        String message = "「" + event.fleetName() + "」は「" + event.missionName() + "」の条件を満たしていません";
        Tools.Controls.showNotify(null, "遠征警告", message, Duration.seconds(10));
    }

    private static void showPortSupply(PortSupplyCompleted event) {
        String message = Messages.getString("portsupply.complete", event.fleetName());
        Tools.Controls.showNotify(null, "母港給糧", message);
    }

    private static void showGimmick(GimmickAchieved event) {
        String message = event.kind() == GimmickAchieved.Kind.SEA_AREA_CHANGE
                ? "海域に変化が確認されました。"
                : "ギミックの達成を確認しました。";
        Tools.Controls.showNotify(null, "ギミック解除", message, Duration.seconds(15));
    }

    private static Node graphic(int shipId) {
        if (shipId <= 0) {
            return null;
        }
        Ship ship = ShipCollection.get().getShipMap().get(shipId);
        if (ship == null) {
            return null;
        }
        return new ImageView(Ships.shipWithItemImage(ship));
    }
}
