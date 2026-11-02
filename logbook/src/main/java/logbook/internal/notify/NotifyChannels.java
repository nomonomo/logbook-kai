package logbook.internal.notify;

import logbook.bean.AppConfig;
import logbook.bean.AppConfig.NotifyKind;
import logbook.notify.BadlyDamagedAlert;
import logbook.notify.CondRecovered;
import logbook.notify.GimmickAchieved;
import logbook.notify.MissionConditionAlert;
import logbook.notify.MissionReturning;
import logbook.notify.MissionStarted;
import logbook.notify.NdockCompleted;
import logbook.notify.Notification;
import logbook.notify.PortSupplyCompleted;

/**
 * 通知イベントと、トースト・サウンドの集合の対応。
 */
final class NotifyChannels {

    private NotifyChannels() {
    }

    /**
     * トースト集合に入っているかを返します。
     *
     * @param event 通知
     * @return 出すなら true
     */
    static boolean toast(Notification event) {
        NotifyKind kind = kind(event);
        return kind != null && AppConfig.get().notifiesByToast(kind);
    }

    /**
     * サウンド集合に入っているかを返します。
     *
     * @param event 通知
     * @return 鳴らすなら true
     */
    static boolean sound(Notification event) {
        NotifyKind kind = kind(event);
        return kind != null && AppConfig.get().notifiesBySound(kind);
    }

    private static NotifyKind kind(Notification event) {
        return switch (event) {
            case MissionReturning _ -> NotifyKind.MISSION;
            case NdockCompleted _ -> NotifyKind.NDOCK;
            case MissionConditionAlert _ -> NotifyKind.MISSION_CONDITION;
            case PortSupplyCompleted _ -> NotifyKind.PORT_SUPPLY;
            case CondRecovered _ -> NotifyKind.COND;
            case BadlyDamagedAlert e -> e.phase() == BadlyDamagedAlert.Phase.SORTIE
                    ? NotifyKind.BADLY_SORTIE
                    : NotifyKind.BADLY_ADVANCE;
            case GimmickAchieved _ -> NotifyKind.GIMMICK;
            case MissionStarted _ -> null;
        };
    }
}
