package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import logbook.bean.EquipShip;
import logbook.bean.EquipShipCollection;
import logbook.bean.ShipMst;
import logbook.bean.Stype;
import logbook.bean.StypeCollection;
import logbook.plugin.PluginContainer;

/**
 * {@link Ships#canEquip} の艦種デフォルトと艦個別上書きを検証する。
 */
class ShipsCanEquipTest {

    private static final int SHIP_ID = 9_900_001;

    private static final int STYPE_ID = 9_900_001;

    private static final int EQUIP_TYPE_ID = 11;

    @BeforeAll
    static void initPluginContainer() {
        PluginContainer.getInstance().init(Collections.emptyList());
    }

    @AfterEach
    void cleanup() {
        StypeCollection.get().getStypeMap().remove(STYPE_ID);
        EquipShipCollection.get().getEquipShipMap().remove(SHIP_ID);
    }

    @Test
    void canEquip_stypeOnly_followsFlag() {
        this.putStype(Map.of(EQUIP_TYPE_ID, 1, 10, 0));

        ShipMst mst = this.ship();

        assertTrue(Ships.canEquip(mst, EQUIP_TYPE_ID));
        assertFalse(Ships.canEquip(mst, 10));
        assertFalse(Ships.canEquip(mst, 99));
    }

    @Test
    void canEquip_nullValue_allowsWhenKeyIsPresent() {
        this.putStype(Map.of(EQUIP_TYPE_ID, 0, 10, 1));
        Map<Integer, List<Integer>> override = new LinkedHashMap<>();
        override.put(EQUIP_TYPE_ID, null);
        this.putEquipShip(override);

        assertTrue(Ships.canEquip(this.ship(), EQUIP_TYPE_ID));
        assertTrue(Ships.canEquip(this.ship(), 10));
        assertFalse(Ships.canEquip(this.ship(), 99));
    }

    @Test
    void canEquip_nonNullValue_allowsWhenKeyIsPresent() {
        this.putStype(Map.of(EQUIP_TYPE_ID, 0));
        this.putEquipShip(Map.of(EQUIP_TYPE_ID, List.of(194)));

        assertTrue(Ships.canEquip(this.ship(), EQUIP_TYPE_ID));
    }

    @Test
    void equipTypeMap_nullValue_roundTrips() throws Exception {
        Map<Integer, List<Integer>> override = new LinkedHashMap<>();
        override.put(EQUIP_TYPE_ID, null);
        override.put(27, List.of(268));
        EquipShipCollection bean = new EquipShipCollection();
        EquipShip equipShip = new EquipShip();
        equipShip.setEquipType(override);
        bean.getEquipShipMap().put(SHIP_ID, equipShip);

        String json = JsonMappers.MAPPER.writeValueAsString(bean);
        EquipShipCollection restored = JsonMappers.MAPPER.readValue(json, EquipShipCollection.class);
        Map<Integer, List<Integer>> back = restored.getEquipShipMap().get(SHIP_ID).getEquipType();

        assertTrue(back.containsKey(EQUIP_TYPE_ID));
        assertNull(back.get(EQUIP_TYPE_ID));
        assertEquals(List.of(268), back.get(27));
    }

    private void putEquipShip(Map<Integer, List<Integer>> equipType) {
        EquipShip equipShip = new EquipShip();
        equipShip.setEquipType(new LinkedHashMap<>(equipType));
        EquipShipCollection.get().getEquipShipMap().put(SHIP_ID, equipShip);
    }

    private void putStype(Map<Integer, Integer> equipType) {
        Stype stype = new Stype();
        stype.setId(STYPE_ID);
        stype.setEquipType(new LinkedHashMap<>(equipType));
        StypeCollection.get().getStypeMap().put(STYPE_ID, stype);
    }

    private ShipMst ship() {
        ShipMst mst = new ShipMst();
        mst.setId(SHIP_ID);
        mst.setStype(STYPE_ID);
        return mst;
    }
}
