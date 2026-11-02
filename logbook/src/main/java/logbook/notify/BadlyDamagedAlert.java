package logbook.notify;

import java.util.List;

/**
 * 出撃または進撃の時点で大破艦がいる。
 * 第二艦隊旗艦を含む。旗艦を通知から外すかは受け手が決める。
 *
 * @param phase 出撃か進撃か
 * @param ships 大破艦
 */
public record BadlyDamagedAlert(Phase phase, List<ShipNotice> ships) implements Notification {

    /**
     * 大破を検知した時点。
     */
    public enum Phase {
        /** 出撃 */
        SORTIE,
        /** 進撃 */
        ADVANCE
    }

    /**
     * 大破艦の表示に使う事実。
     *
     * @param shipId 艦ID
     * @param name 艦名。不明なら空文字
     * @param yomi 読み。不明なら空文字
     * @param level レベル
     * @param secondFleetFlagship 連合艦隊の第二艦隊旗艦なら true
     */
    public record ShipNotice(int shipId, String name, String yomi, int level, boolean secondFleetFlagship) {
    }

    public BadlyDamagedAlert {
        ships = List.copyOf(ships);
    }
}
