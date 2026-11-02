package logbook.internal.notify;

import java.util.List;

import logbook.bean.AppConfig;
import logbook.notify.BadlyDamagedAlert;
import logbook.notify.BadlyDamagedAlert.ShipNotice;

/**
 * 内蔵リスナーが大破通知の艦一覧に適用する設定。発生元では除外しない。
 */
final class BadlyDamageNotices {

    private BadlyDamageNotices() {
    }

    /**
     * 第二艦隊旗艦の除外を適用した艦一覧。
     *
     * @param event 大破通知
     * @return 通知に載せる艦
     */
    static List<ShipNotice> ships(BadlyDamagedAlert event) {
        if (!AppConfig.get().isIgnoreSecondFlagship()) {
            return event.ships();
        }
        return event.ships().stream()
                .filter(ship -> !ship.secondFleetFlagship())
                .toList();
    }
}
