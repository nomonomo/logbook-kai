package logbook.internal.fleet;

import java.util.ArrayList;
import java.util.List;

import logbook.bean.DeckPortCollection;
import logbook.bean.NdockCollection;
import logbook.bean.Ship;
import logbook.internal.Ships;
import lombok.Value;

/**
 * 艦の行を作り直すかの比較に使う値のコピー。
 * 行の文字と、艦画像（損傷、疲労、補給、装備アイコン、退避、入渠、遠征、出撃札、経験値ゲージ）に効く項目だけを持つ。
 */
@Value
public class FleetShipRow {

    /** 艦 ID */
    int shipId;

    /** 艦のマスタ ID */
    int shipMstId;

    /** レベル */
    int lv;

    /** 現在 HP */
    int nowhp;

    /** 最大 HP */
    int maxhp;

    /** コンディション */
    int cond;

    /** 燃料 */
    int fuel;

    /** 弾薬 */
    int bull;

    /** 装備 ID */
    List<Integer> slot;

    /** 補強増設 */
    int slotEx;

    /** スロット数 */
    int slotnum;

    /** 退避 */
    boolean escape;

    /** 入渠中 */
    boolean onNdock;

    /** 遠征中 */
    boolean onMission;

    /** 出撃海域 */
    int sallyArea;

    /** 経験値 */
    int exp;

    /** 次のレベルまでの経験値 */
    int expNext;

    /** 特殊効果の種別 */
    List<Integer> spEffectKinds;

    /**
     * 艦の現在値から行の比較用コピーを作ります。
     *
     * @param ship 艦
     * @return 行
     */
    public static FleetShipRow of(Ship ship) {
        List<Integer> exp = ship.getExp();
        int expNow = 0;
        int expNext = 0;
        if (exp != null) {
            if (!exp.isEmpty() && exp.get(0) != null) {
                expNow = exp.get(0);
            }
            if (exp.size() > 1 && exp.get(1) != null) {
                expNext = exp.get(1);
            }
        }
        return new FleetShipRow(
                zero(ship.getId()),
                zero(ship.getShipId()),
                zero(ship.getLv()),
                zero(ship.getNowhp()),
                zero(ship.getMaxhp()),
                zero(ship.getCond()),
                zero(ship.getFuel()),
                zero(ship.getBull()),
                copyIds(ship.getSlot()),
                zero(ship.getSlotEx()),
                zero(ship.getSlotnum()),
                Ships.isEscape(ship),
                NdockCollection.get().getNdockSet().contains(ship.getId()),
                DeckPortCollection.get().getMissionShips().contains(ship.getId()),
                zero(ship.getSallyArea()),
                expNow,
                expNext,
                effectKinds(ship));
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private static List<Integer> copyIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Integer> copy = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            copy.add(id == null ? 0 : id);
        }
        return List.copyOf(copy);
    }

    private static List<Integer> effectKinds(Ship ship) {
        if (ship.getSpEffectItems() == null || ship.getSpEffectItems().isEmpty()) {
            return List.of();
        }
        List<Integer> kinds = new ArrayList<>();
        for (Ship.SpEffectItem item : ship.getSpEffectItems()) {
            kinds.add(item.getKind() == null ? 0 : item.getKind());
        }
        return List.copyOf(kinds);
    }
}
