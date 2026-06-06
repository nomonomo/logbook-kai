package logbook.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import logbook.internal.AppQuestConditionLoader;
import logbook.plugin.PluginContainer;

/**
 * 艦隊条件の Lv 判定を検証する。
 */
class AppQuestConditionFleetConditionLvTest {

    /**
     * {@link logbook.internal.Ships} の static 初期化が {@link PluginContainer} 経由で
     * リソースを読むため、テスト前に初期化する（{@link AppQuestDurationTest} と同様）。
     */
    @BeforeAll
    static void initPluginContainer() {
        PluginContainer.getInstance().init(Collections.emptyList());
    }

    @Test
    void load_lvField_deserializesFromJson() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/logbook/quest/lv_test.json")) {
            AppQuestCondition root = AppQuestConditionLoader.load(in);
            AppQuestCondition.FleetCondition fleet = root.getFilter().getFleet();
            assertNotNull(fleet);
            AppQuestCondition.FleetCondition nameLv = fleet.getConditions().get(0);
            assertEquals(88, nameLv.getLv());
            assertNull(nameLv.getOperator());
            AppQuestCondition.FleetCondition stypeLv = fleet.getConditions().get(1);
            assertEquals(50, stypeLv.getLv());
            assertEquals(2, stypeLv.getCount());
        }
    }

    @Test
    void test_nameAndLv_matchesWhenLvMeetsDefaultGe() {
        AppQuestCondition.FleetCondition condition = new AppQuestCondition.FleetCondition();
        condition.setName(new LinkedHashSet<>(List.of("テスト艦")));
        condition.setLv(88);

        Ship ship = new Ship();
        ship.setLv(90);
        ship.setShipId(1);

        ShipMst mst = new ShipMst();
        mst.setId(1);
        mst.setName("テスト艦改");
        ShipMstCollection.get().getShipMap().put(1, mst);

        assertTrue(condition.test(new ArrayList<>(List.of(ship))));
    }

    @Test
    void test_nameAndLv_ignoresOperatorAndUsesDefaultGe() {
        AppQuestCondition.FleetCondition condition = new AppQuestCondition.FleetCondition();
        condition.setName(new LinkedHashSet<>(List.of("テスト艦")));
        condition.setLv(88);
        condition.setOperator("LE");

        Ship ship = new Ship();
        ship.setLv(90);
        ship.setShipId(2);

        ShipMst mst = new ShipMst();
        mst.setId(2);
        mst.setName("テスト艦");
        ShipMstCollection.get().getShipMap().put(2, mst);

        // operator は Lv に効かない。Lv88以上として Lv90 は一致する
        assertTrue(condition.test(new ArrayList<>(List.of(ship))));
    }

    @Test
    void test_nameAndLv_rejectsWhenLvBelowThreshold() {
        AppQuestCondition.FleetCondition condition = new AppQuestCondition.FleetCondition();
        condition.setName(new LinkedHashSet<>(List.of("テスト艦")));
        condition.setLv(88);

        Ship ship = new Ship();
        ship.setLv(50);
        ship.setShipId(2);

        ShipMst mst = new ShipMst();
        mst.setId(2);
        mst.setName("テスト艦");
        ShipMstCollection.get().getShipMap().put(2, mst);

        assertFalse(condition.test(new ArrayList<>(List.of(ship))));
    }

    @Test
    void toString_stypeCountAndLv_displaysLvBeforeShipType() {
        AppQuestCondition.FleetCondition condition = new AppQuestCondition.FleetCondition();
        condition.setStype(new LinkedHashSet<>(List.of("戦艦")));
        condition.setCount(2);
        condition.setOperator("GE");
        condition.setLv(88);

        assertEquals("Lv88以上の戦艦が2隻以上", condition.toString());
    }

    @Test
    void test_countAndLv_countsOnlyShipsMatchingLv() {
        AppQuestCondition.FleetCondition condition = new AppQuestCondition.FleetCondition();
        condition.setStype(new LinkedHashSet<>(List.of("駆逐艦")));
        condition.setCount(2);
        condition.setOperator("GE");
        condition.setLv(50);

        ShipMst mst = new ShipMst();
        mst.setId(3);
        mst.setName("駆逐");
        mst.setStype(2);
        ShipMstCollection.get().getShipMap().put(3, mst);
        Stype stype = new Stype();
        stype.setId(2);
        stype.setName("駆逐艦");
        StypeCollection.get().getStypeMap().put(2, stype);

        Ship high = new Ship();
        high.setShipId(3);
        high.setLv(60);
        Ship low = new Ship();
        low.setShipId(3);
        low.setLv(40);

        assertTrue(condition.test(new ArrayList<>(List.of(high, high, low))));
        assertFalse(condition.test(new ArrayList<>(List.of(high, low, low))));
    }

    @Test
    void test_lvOperator_le_allowsLvBelowThreshold() {
        AppQuestCondition.FleetCondition condition = new AppQuestCondition.FleetCondition();
        condition.setName(new LinkedHashSet<>(List.of("テスト艦")));
        condition.setLv(10);
        condition.setLvOperator("LE");

        Ship ship = new Ship();
        ship.setLv(5);
        ship.setShipId(4);

        ShipMst mst = new ShipMst();
        mst.setId(4);
        mst.setName("テスト艦");
        ShipMstCollection.get().getShipMap().put(4, mst);

        assertTrue(condition.test(new ArrayList<>(List.of(ship))));
    }

    @Test
    void toString_lvOperator_displaysLe() {
        AppQuestCondition.FleetCondition condition = new AppQuestCondition.FleetCondition();
        condition.setStype(new LinkedHashSet<>(List.of("駆逐艦")));
        condition.setCount(1);
        condition.setOperator("GE");
        condition.setLv(10);
        condition.setLvOperator("LE");

        assertEquals("Lv10以下の駆逐艦が1隻以上", condition.toString());
    }
}
