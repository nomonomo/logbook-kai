package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import logbook.bean.AppQuestCondition;

/**
 * {@link AppQuestConditionLoader#load} の読み込み結果と record → AppQuestCondition 変換を検証する。
 */
class AppQuestConditionLoaderTest {

    private static final String RESOURCE_0 = "logbook/quest/0.json";

    @Test
    void load_0_returnsAppQuestConditionWithExpectedFields() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_0)) {
            assertNotNull(in, "テスト用 logbook/quest/0.json が存在すること");

            AppQuestCondition root = AppQuestConditionLoader.load(in);

            assertNotNull(root);
            assertEquals(AppQuestCondition.Type.出撃, root.getType(), "type");
            assertEquals("イヤリー", root.getResetType(), "resetType");
            assertEquals(5, root.getYearlyResetMonth(), "yearlyResetMonth");

            assertNotNull(root.getFilter(), "filter");
            assertNotNull(root.getFilter().getArea(), "filter.area");
            assertEquals(4, root.getFilter().getArea().size(), "filter.area 件数");
            assertTrue(root.getFilter().getArea().contains("3-1"), "filter.area に 3-1 が含まれること");

            assertNotNull(root.getFilter().getFleet(), "filter.fleet");
            AppQuestCondition.FleetCondition fleet = root.getFilter().getFleet();
            assertEquals("テストクエストの説明", fleet.getDescription(), "filter.fleet.description");
            assertEquals("AND", fleet.getOperator(), "filter.fleet.operator");
            assertFalse(fleet.isDifference(), "filter.fleet.difference が JSON に無い場合は false に正規化されること");

            assertNotNull(fleet.getConditions(), "filter.fleet.conditions");
            assertEquals(2, fleet.getConditions().size(), "filter.fleet.conditions 件数");
            assertFalse(fleet.getConditions().get(0).isDifference(), "fleet.conditions[0].difference が JSON に無い場合は false");
            assertTrue(fleet.getConditions().get(1).isDifference(), "fleet.conditions[1].difference が true の場合は true のまま");

            assertNotNull(root.getConditions(), "conditions");
            assertEquals(5, root.getConditions().size(), "conditions 件数");

            AppQuestCondition.Condition cond0 = root.getConditions().get(0);
            assertTrue(cond0.isStart(), "conditions[0].start が true の場合は true のまま");
            assertFalse(cond0.isBoss(), "conditions[0].boss が JSON に無い場合は false に正規化されること");
            assertEquals(36, cond0.getCount(), "conditions[0].count");

            AppQuestCondition.Condition cond1 = root.getConditions().get(1);
            assertFalse(cond1.isStart(), "conditions[1].start が JSON に無い場合は false に正規化されること");
            assertTrue(cond1.isBoss(), "conditions[1].boss が true の場合は true のまま");
            assertNotNull(cond1.getArea(), "conditions[1].area");
            assertEquals(1, cond1.getArea().size(), "conditions[1].area 件数");
            assertTrue(cond1.getArea().contains("3-1"), "conditions[1].area に 3-1 が含まれること");
            assertNotNull(cond1.getRank(), "conditions[1].rank");
            assertEquals(2, cond1.getRank().size(), "conditions[1].rank 件数");
            assertEquals(1, cond1.getCount(), "conditions[1].count");

            AppQuestCondition.Condition cond3 = root.getConditions().get(3);
            assertFalse(cond3.isStart(), "conditions[3].start が JSON に無い場合は false");
            assertTrue(cond3.isBoss(), "conditions[3].boss が true の場合は true のまま");
            assertEquals("P", cond3.getCell(), "4 件目 condition cell");
        }
    }
}
