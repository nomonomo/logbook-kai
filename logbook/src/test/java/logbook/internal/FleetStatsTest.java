package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import logbook.bean.AppCondition;
import logbook.bean.Ship;
import logbook.bean.ShipMst;
import logbook.bean.ShipMstCollection;
import logbook.plugin.PluginContainer;

/**
 * {@link FleetStats} が艦隊タブの合計と同じであることを検証する。
 */
class FleetStatsTest {

    @BeforeAll
    static void initPluginContainer() {
        PluginContainer.getInstance().init(Collections.emptyList());
    }

    @Test
    void of_excludesEscapedShipFromSums() {
        Ship active = ship(9101, 8101, 10, 10, 20, 30, 40, 50);
        Ship escaped = ship(9102, 8101, 7, 10, 100, 100, 100, 100);
        installMst(8101);
        boolean added = AppCondition.get().getEscape().add(9102);
        try {
            FleetStats stats = FleetStats.of(List.of(active, escaped), 1);
            assertEquals(Ships.airSuperiority(active), stats.getAirSuperiority());
            assertEquals(10, stats.getLvSum());
            assertEquals(20, stats.getKaryokuSum());
            assertEquals(30, stats.getTaikuSum());
            assertEquals(40, stats.getTaisenSum());
            assertEquals(50, stats.getSakutekiSum());
            assertEquals(5, stats.getTransportPoint());
            assertEquals(3, stats.getTransportPoint70());
            assertEquals("5/3", stats.transportPointText());
            assertEquals(Ships.decision33(List.of(active, escaped), 1).get(), stats.decision33(), 0.0000001D);
        } finally {
            if (added) {
                AppCondition.get().getEscape().remove(9102);
            }
            ShipMstCollection.get().getShipMap().remove(8101);
        }
    }

    @Test
    void of_speedLabelBoundaries() {
        assertEquals("低速", FleetStats.of(List.of(ship(9201, 8201, 1, 5, 0, 0, 0, 0)), 1).getSpeedLabel());
        assertEquals("高速", FleetStats.of(List.of(ship(9202, 8201, 1, 10, 0, 0, 0, 0)), 1).getSpeedLabel());
        assertEquals("高速+", FleetStats.of(List.of(ship(9203, 8201, 1, 15, 0, 0, 0, 0)), 1).getSpeedLabel());
        assertEquals(FleetStats.SPEED_STYLE_FAST_PLUS,
                FleetStats.of(List.of(ship(9203, 8201, 1, 15, 0, 0, 0, 0)), 1).getSpeedStyle());
        assertEquals("最速", FleetStats.of(List.of(ship(9204, 8201, 1, 20, 0, 0, 0, 0)), 1).getSpeedLabel());
        assertEquals(FleetStats.SPEED_STYLE_FASTEST,
                FleetStats.of(List.of(ship(9204, 8201, 1, 20, 0, 0, 0, 0)), 1).getSpeedStyle());
        assertEquals("(不明)", FleetStats.of(List.of(ship(9205, 8201, 1, 4, 0, 0, 0, 0)), 1).getSpeedLabel());
        FleetStats slowest = FleetStats.of(List.of(
                ship(9206, 8201, 1, 20, 0, 0, 0, 0),
                ship(9207, 8201, 1, 10, 0, 0, 0, 0)), 1);
        assertEquals("高速", slowest.getSpeedLabel());
    }

    @Test
    void of_transportPointUsesSevenTenthsTruncation() {
        installMst(8301);
        try {
            Ship first = ship(9301, 8301, 1, 10, 0, 0, 0, 0);
            Ship second = ship(9302, 8301, 1, 10, 0, 0, 0, 0);
            FleetStats stats = FleetStats.of(List.of(first, second), 1);
            assertEquals(10, stats.getTransportPoint());
            assertEquals(7, stats.getTransportPoint70());
            assertEquals("10/7", stats.transportPointText());
        } finally {
            ShipMstCollection.get().getShipMap().remove(8301);
        }
    }

    private static void installMst(int mstId) {
        ShipMst mst = new ShipMst();
        mst.setId(mstId);
        mst.setStype(2);
        mst.setName("テスト駆逐");
        mst.setFuelMax(10);
        mst.setBullMax(10);
        ShipMstCollection.get().getShipMap().put(mstId, mst);
    }

    private static Ship ship(int id, int mstId, int lv, int soku, int karyoku, int taiku, int taisen, int sakuteki) {
        Ship ship = new Ship();
        ship.setId(id);
        ship.setShipId(mstId);
        ship.setLv(lv);
        ship.setSoku(soku);
        ship.setKaryoku(List.of(karyoku, karyoku));
        ship.setTaiku(List.of(taiku, taiku));
        ship.setTaisen(List.of(taisen, taisen));
        ship.setSakuteki(List.of(sakuteki, sakuteki));
        ship.setNowhp(20);
        ship.setMaxhp(30);
        ship.setCond(49);
        ship.setFuel(10);
        ship.setBull(10);
        ship.setSlot(new ArrayList<>());
        ship.setOnslot(new ArrayList<>());
        ship.setSlotEx(0);
        ship.setSlotnum(0);
        ship.setExp(List.of(0, 100));
        return ship;
    }
}
