package logbook.internal.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import logbook.bean.AppCondition;
import logbook.bean.DeckPort;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.bean.ShipMst;
import logbook.bean.ShipMstCollection;
import logbook.bean.SlotItem;
import logbook.bean.SlotItemCollection;
import logbook.bean.SlotitemMst;
import logbook.bean.SlotitemMstCollection;
import logbook.plugin.PluginContainer;

/**
 * {@link FleetSnapshot} が値のコピーであることと、装備の熟練度・改修値・退避で等価でなくなることを検証する。
 */
class FleetSnapshotTest {

    private static final int SHIP_ID = 9401;
    private static final int MST_ID = 8401;
    private static final int ITEM_ID = 7401;
    private static final int ITEM_MST_ID = 6401;

    @BeforeAll
    static void initPluginContainer() {
        PluginContainer.getInstance().init(Collections.emptyList());
    }

    @Test
    void of_copiesValuesSoLaterMutationDoesNotChangePreviousSnapshot() {
        Ship ship = ship();
        List<Integer> slot = new ArrayList<>();
        slot.add(ITEM_ID);
        ship.setSlot(slot);
        install(ship, 0, 0);
        try {
            DeckPort port = port(ship);
            FleetSnapshot before = FleetSnapshot.of(port, 1);
            ship.setNowhp(1);
            slot.set(0, ITEM_ID + 1);
            assertEquals(20, before.getRows().get(0).getNowhp());
            assertEquals(List.of(ITEM_ID), before.getRows().get(0).getSlot());
            assertNotEquals(before, FleetSnapshot.of(port, 1));
        } finally {
            cleanup();
        }
    }

    @Test
    void of_escapeOnlyChangeIsNotEqual() {
        Ship ship = ship();
        install(ship, 0, 0);
        boolean added = AppCondition.get().getEscape().add(SHIP_ID);
        try {
            DeckPort port = port(ship);
            FleetSnapshot escaped = FleetSnapshot.of(port, 1);
            AppCondition.get().getEscape().remove(SHIP_ID);
            added = false;
            FleetSnapshot active = FleetSnapshot.of(port, 1);
            assertNotEquals(escaped, active);
            assertEquals(0, escaped.getStats().getAirSuperiority());
        } finally {
            if (added) {
                AppCondition.get().getEscape().remove(SHIP_ID);
            }
            cleanup();
        }
    }

    @Test
    void of_alvOrLevelOnlyChangeIsNotEqualButRowStaysEqual() {
        Ship ship = ship();
        install(ship, 0, 0);
        try {
            DeckPort port = port(ship);
            FleetSnapshot before = FleetSnapshot.of(port, 1);
            SlotItemCollection.get().getSlotitemMap().get(ITEM_ID).setAlv(7);
            FleetSnapshot alvChanged = FleetSnapshot.of(port, 1);
            assertEquals(before.getRows(), alvChanged.getRows());
            assertNotEquals(before, alvChanged);

            SlotItemCollection.get().getSlotitemMap().get(ITEM_ID).setLevel(4);
            FleetSnapshot levelChanged = FleetSnapshot.of(port, 1);
            assertEquals(alvChanged.getRows(), levelChanged.getRows());
            assertNotEquals(alvChanged, levelChanged);
        } finally {
            cleanup();
        }
    }

    private static DeckPort port(Ship ship) {
        DeckPort port = new DeckPort();
        port.setId(1);
        port.setName("第一艦隊");
        port.setShip(List.of(ship.getId()));
        port.setMission(List.of(0L, 0L, 0L));
        return port;
    }

    private static Ship ship() {
        Ship ship = new Ship();
        ship.setId(SHIP_ID);
        ship.setShipId(MST_ID);
        ship.setLv(10);
        ship.setSoku(10);
        ship.setKaryoku(List.of(1, 1));
        ship.setTaiku(List.of(1, 1));
        ship.setTaisen(List.of(1, 1));
        ship.setSakuteki(List.of(1, 1));
        ship.setNowhp(20);
        ship.setMaxhp(30);
        ship.setCond(49);
        ship.setFuel(10);
        ship.setBull(10);
        ship.setSlot(new ArrayList<>(List.of(ITEM_ID)));
        ship.setOnslot(new ArrayList<>(List.of(18)));
        ship.setSlotEx(0);
        ship.setSlotnum(1);
        ship.setExp(List.of(0, 100));
        return ship;
    }

    private static void install(Ship ship, int alv, int level) {
        ShipMst mst = new ShipMst();
        mst.setId(MST_ID);
        mst.setStype(2);
        mst.setName("テスト駆逐");
        mst.setFuelMax(10);
        mst.setBullMax(10);
        ShipMstCollection.get().getShipMap().put(MST_ID, mst);

        SlotitemMst itemMst = new SlotitemMst();
        itemMst.setId(ITEM_MST_ID);
        itemMst.setType(List.of(0, 0, 6, 6));
        itemMst.setTyku(10);
        itemMst.setSaku(0);
        SlotitemMstCollection.get().getSlotitemMap().put(ITEM_MST_ID, itemMst);

        SlotItem item = new SlotItem();
        item.setId(ITEM_ID);
        item.setSlotitemId(ITEM_MST_ID);
        item.setAlv(alv);
        item.setLevel(level);
        SlotItemCollection.get().getSlotitemMap().put(ITEM_ID, item);

        ShipCollection.get().getShipMap().put(ship.getId(), ship);
    }

    private static void cleanup() {
        ShipCollection.get().getShipMap().remove(SHIP_ID);
        ShipMstCollection.get().getShipMap().remove(MST_ID);
        SlotItemCollection.get().getSlotitemMap().remove(ITEM_ID);
        SlotitemMstCollection.get().getSlotitemMap().remove(ITEM_MST_ID);
    }
}
