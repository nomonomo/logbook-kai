package logbook.internal.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 艦の行は変わった位置だけ作り直す。
 */
class FleetShipRowsTest {

    @Test
    void changedIndices_rebuildsOnlyTheChangedRow() {
        FleetShipRow first = row(1, 10);
        FleetShipRow second = row(2, 20);
        FleetShipRow secondDamaged = row(2, 5);
        List<Integer> changed = FleetShipRows.changedIndices(
                List.of(first, second),
                List.of(first, secondDamaged));
        assertEquals(List.of(1), changed);
    }

    @Test
    void changedIndices_includesAddedRowAndDropsRemovedRow() {
        FleetShipRow first = row(1, 10);
        FleetShipRow second = row(2, 20);
        assertEquals(List.of(1), FleetShipRows.changedIndices(List.of(first), List.of(first, second)));
        assertEquals(List.of(), FleetShipRows.changedIndices(List.of(first, second), List.of(first)));
    }

    private static FleetShipRow row(int shipId, int nowhp) {
        return new FleetShipRow(shipId, 1, 1, nowhp, 30, 49, 10, 10,
                List.of(), 0, 0, false, false, false, 0, 0, 0, List.of());
    }
}
