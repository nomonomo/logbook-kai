package logbook.internal.notify;

import java.util.ArrayList;
import java.util.List;

import logbook.bean.DeckPort;
import logbook.bean.DeckPortCollection;
import logbook.bean.Ship;
import logbook.bean.ShipMst;
import logbook.internal.Ships;
import logbook.notify.BadlyDamagedAlert;
import logbook.notify.BadlyDamagedAlert.Phase;
import logbook.notify.BadlyDamagedAlert.ShipNotice;
import logbook.notify.Notifications;

/**
 * 艦の状態から通知の事実を組み立てる。
 */
public final class NotificationFacts {

    private NotificationFacts() {
    }

    /**
     * 出撃・進撃艦隊の大破艦がいれば通知する。設定では除外しない。
     *
     * @param phase 出撃か進撃か
     * @param deckId 出撃中の艦隊
     * @param combined 連合艦隊で、第1艦隊の出撃なら第2艦隊も見る
     */
    public static void publishBadlyDamaged(Phase phase, int deckId, boolean combined) {
        DeckPort port = DeckPortCollection.get().getDeckPortMap().get(deckId);
        if (port == null) {
            return;
        }
        List<Ship> badlyShips = new ArrayList<>(port.getBadlyShips());
        int secondFleetFlagshipId = 0;
        if (combined && deckId == 1) {
            DeckPort second = DeckPortCollection.get().getDeckPortMap().get(2);
            if (second != null) {
                badlyShips.addAll(second.getBadlyShips());
                secondFleetFlagshipId = flagshipId(second);
            }
        }
        if (badlyShips.isEmpty()) {
            return;
        }
        Notifications.publish(badlyDamaged(phase, badlyShips, secondFleetFlagshipId));
    }

    /**
     * 大破艦のリストから通知を作る。
     *
     * @param phase 出撃か進撃か
     * @param ships 大破艦
     * @param secondFleetFlagshipId 第二艦隊旗艦の艦ID。該当が無ければ 0
     * @return 通知
     */
    public static BadlyDamagedAlert badlyDamaged(Phase phase, List<Ship> ships, int secondFleetFlagshipId) {
        List<ShipNotice> notices = new ArrayList<>();
        if (ships != null) {
            for (Ship ship : ships) {
                if (ship == null) {
                    continue;
                }
                var mst = Ships.shipMst(ship);
                int shipId = ship.getId() == null ? 0 : ship.getId();
                int level = ship.getLv() == null ? 0 : ship.getLv();
                notices.add(new ShipNotice(
                        shipId,
                        mst.map(ShipMst::getName).orElse(""),
                        mst.map(ShipMst::getYomi).orElse(""),
                        level,
                        secondFleetFlagshipId > 0 && shipId == secondFleetFlagshipId));
            }
        }
        return new BadlyDamagedAlert(phase, notices);
    }

    private static int flagshipId(DeckPort port) {
        List<Integer> shipIds = port.getShip();
        if (shipIds == null || shipIds.isEmpty() || shipIds.get(0) == null) {
            return 0;
        }
        return shipIds.get(0);
    }
}
