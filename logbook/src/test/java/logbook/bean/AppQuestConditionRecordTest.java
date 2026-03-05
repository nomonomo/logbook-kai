package logbook.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import logbook.bean.FleetConditionRecord;
import logbook.internal.AppQuestConditionLoader;

/**
 * {@link AppQuestConditionRecord} のデシリアライズを READER_WITH_COMMENTS で検証する。
 */
class AppQuestConditionRecordTest {

    private static final String RESOURCE_0 = "logbook/quest/0.json";

    @Test
    void readerWithComments_deserializes_0_json_fieldByField() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_0)) {
            assertNotNull(in, "テスト用 logbook/quest/0.json が存在すること");

            AppQuestConditionRecord root = AppQuestConditionLoader.readRecord(in);

            assertEquals(AppQuestCondition.Type.出撃, root.type(), "type");
            assertEquals("イヤリー", root.resetType(), "resetType");
            assertEquals(5, root.yearlyResetMonth(), "yearlyResetMonth");

            assertNotNull(root.filter(), "filter");
            assertNotNull(root.filter().area(), "filter.area");
            assertEquals(4, root.filter().area().size(), "filter.area 件数");
            assertTrue(root.filter().area().contains("3-1"), "filter.area に 3-1 が含まれること");

            assertNotNull(root.filter().fleet(), "filter.fleet");
            FleetConditionRecord fleet = root.filter().fleet();
            assertEquals("テストクエストの説明", fleet.description(), "filter.fleet.description");
            assertEquals("AND", fleet.operator(), "filter.fleet.operator");
            assertFalse(fleet.difference(), "filter.fleet.difference が JSON に無い場合は false に正規化されること");

            assertNotNull(fleet.conditions(), "filter.fleet.conditions");
            assertEquals(2, fleet.conditions().size(), "filter.fleet.conditions 件数");
            assertFalse(fleet.conditions().get(0).difference(), "fleet.conditions[0].difference が JSON に無い場合は false");
            assertTrue(fleet.conditions().get(1).difference(), "fleet.conditions[1].difference が true の場合は true のまま");

            assertNotNull(root.conditions(), "conditions");
            assertEquals(5, root.conditions().size(), "conditions 件数");

            ConditionRecord cond0 = root.conditions().get(0);
            assertTrue(cond0.start(), "conditions[0].start が true の場合は true のまま");
            assertFalse(cond0.boss(), "conditions[0].boss が JSON に無い場合は false に正規化されること");
            assertEquals(36, cond0.count(), "conditions[0].count");

            ConditionRecord cond1 = root.conditions().get(1);
            assertFalse(cond1.start(), "conditions[1].start が JSON に無い場合は false に正規化されること");
            assertTrue(cond1.boss(), "conditions[1].boss が true の場合は true のまま");
            assertNotNull(cond1.area(), "conditions[1].area");
            assertEquals(1, cond1.area().size(), "conditions[1].area 件数");
            assertEquals("3-1", cond1.area().get(0), "conditions[1].area[0]");
            assertNotNull(cond1.rank(), "conditions[1].rank");
            assertEquals(2, cond1.rank().size(), "conditions[1].rank 件数");
            assertEquals(1, cond1.count(), "conditions[1].count");

            ConditionRecord cond3 = root.conditions().get(3);
            assertFalse(cond3.start(), "conditions[3].start が JSON に無い場合は false");
            assertTrue(cond3.boss(), "conditions[3].boss が true の場合は true のまま");
            assertEquals("P", cond3.cell(), "4 件目 condition cell");
        }
    }
}
