package logbook.internal.fleet;

import java.util.ArrayList;
import java.util.List;

/**
 * 艦の行を、前回のコピーと位置ごとに比較します。
 */
public final class FleetShipRows {

    private FleetShipRows() {
    }

    /**
     * 作り直す行の位置を返します。増えた行も含み、減った行は含みません。
     *
     * @param previous 前回の行。未表示なら null
     * @param next 今回の行
     * @return 作り直す位置
     */
    public static List<Integer> changedIndices(List<FleetShipRow> previous, List<FleetShipRow> next) {
        List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < next.size(); i++) {
            if (previous == null || i >= previous.size() || !previous.get(i).equals(next.get(i))) {
                changed.add(i);
            }
        }
        return changed;
    }
}
