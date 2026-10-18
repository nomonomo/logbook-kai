package logbook.internal.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import logbook.bean.DeckPort;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.bean.ShipMst;
import logbook.bean.ShipMstCollection;
import logbook.internal.fleet.FleetSnapshot;
import logbook.plugin.PluginContainer;

/**
 * {@link FleetModel} の疲労回復通知。MainController は poll を apply より先に呼ぶ。
 */
class FleetModelTest {

    private static final int SHIP_ID = 9501;
    private static final int MST_ID = 8501;

    @BeforeAll
    static void initPluginContainer() {
        PluginContainer.getInstance().init(Collections.emptyList());
    }

    @Test
    void pollCondRecover_beforeApply_notifiesWhenSnapshotChangesAfterRecoverTime() throws Exception {
        Ship ship = installShip(20);
        DeckPort port = port(ship);
        FleetModel model = new FleetModel();
        model.apply(FleetSnapshot.of(port, 1));
        long armed = Instant.now().getEpochSecond() - 10;
        setArmedCondEpoch(model, armed);

        ship.setNowhp(15);
        long now = Instant.now().getEpochSecond();

        assertTrue(model.pollCondRecover(now));
        model.apply(FleetSnapshot.of(port, 1));
        assertFalse(model.pollCondRecover(now));
    }

    @Test
    void pollCondRecover_afterApply_missesWhenSnapshotChangesAfterRecoverTime() throws Exception {
        Ship ship = installShip(20);
        DeckPort port = port(ship);
        FleetModel model = new FleetModel();
        model.apply(FleetSnapshot.of(port, 1));
        long armed = Instant.now().getEpochSecond() - 10;
        setArmedCondEpoch(model, armed);

        ship.setNowhp(15);
        long now = Instant.now().getEpochSecond();

        model.apply(FleetSnapshot.of(port, 1));
        assertFalse(model.pollCondRecover(now));
    }

    private static Ship installShip(int nowhp) {
        ShipMst mst = new ShipMst();
        mst.setId(MST_ID);
        mst.setStype(2);
        mst.setName("テスト駆逐");
        mst.setFuelMax(10);
        mst.setBullMax(10);
        ShipMstCollection.get().getShipMap().put(MST_ID, mst);

        Ship ship = new Ship();
        ship.setId(SHIP_ID);
        ship.setShipId(MST_ID);
        ship.setLv(10);
        ship.setSoku(10);
        ship.setKaryoku(List.of(1, 1));
        ship.setTaiku(List.of(1, 1));
        ship.setTaisen(List.of(1, 1));
        ship.setSakuteki(List.of(1, 1));
        ship.setNowhp(nowhp);
        ship.setMaxhp(30);
        ship.setCond(48);
        ship.setFuel(10);
        ship.setBull(10);
        ship.setSlot(new ArrayList<>());
        ship.setOnslot(new ArrayList<>());
        ship.setSlotEx(0);
        ship.setSlotnum(0);
        ship.setExp(List.of(0, 100));
        ShipCollection.get().getShipMap().put(SHIP_ID, ship);
        return ship;
    }

    private static DeckPort port(Ship ship) {
        DeckPort port = new DeckPort();
        port.setId(1);
        port.setName("第一艦隊");
        port.setShip(List.of(ship.getId()));
        port.setMission(List.of(0L, 0L, 0L));
        return port;
    }

    private static void setArmedCondEpoch(FleetModel model, long epoch) throws Exception {
        Field field = FleetModel.class.getDeclaredField("armedCondEpoch");
        field.setAccessible(true);
        field.setLong(model, epoch);
    }

    @org.junit.jupiter.api.AfterEach
    void cleanup() {
        ShipCollection.get().getShipMap().remove(SHIP_ID);
        ShipMstCollection.get().getShipMap().remove(MST_ID);
    }
}
