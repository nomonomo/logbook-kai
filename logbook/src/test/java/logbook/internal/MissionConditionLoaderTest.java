package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Set;

import org.junit.jupiter.api.Test;

import logbook.bean.MissionCondition;
import tools.jackson.core.JacksonException;

/**
 * {@link MissionConditionLoader#load} の読み込み結果と record → MissionCondition 変換を検証する。
 */
class MissionConditionLoaderTest {

    private static final String RESOURCE_01_MISSING_BRACKET = "logbook/mission/1/01.json";
    private static final String RESOURCE_99 = "logbook/mission/5/99.json";

    @Test
    void load_throwsWhenJsonHasMissingBrackets() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_01_MISSING_BRACKET)) {
            assertNotNull(in, "テスト用 1/01.json（括弧不足）が存在すること");

            assertThrows(JacksonException.class, () -> MissionConditionLoader.load(in),
                    "括弧が足りない JSON を読むと JacksonException がスローされること");
        }
    }

    @Test
    void load_5_99_returnsMissionConditionWithNestedAndGreatSuccess() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_99)) {
            assertNotNull(in, "テスト用 5/99.json が存在すること");

            MissionCondition root = MissionConditionLoader.load(in);

            assertNotNull(root);
            assertEquals("AND", root.getOperator(), "ルート operator");
            assertEquals("テスト", root.getDescription(), "ルート description");
            assertNotNull(root.getConditions());
            assertEquals(1, root.getConditions().size(), "ルート conditions 件数");

            MissionCondition orBranch = root.getConditions().get(0);
            assertEquals("OR", orBranch.getOperator(), "OR 枝 operator");
            assertNotNull(orBranch.getConditions());
            assertEquals(2, orBranch.getConditions().size(), "OR 枝 conditions 件数");
            MissionCondition first = orBranch.getConditions().get(0);
            assertEquals("艦娘", first.getType(), "先頭 condition type");
            assertEquals(20, first.getLevel(), "先頭 condition level");
            assertEquals(1, first.getOrder(), "先頭 condition order");

            // ship_type が Set<String> に正しく格納されていること
            MissionCondition second = orBranch.getConditions().get(1);
            assertEquals("艦娘", second.getType(), "2 件目 condition type");
            Set<String> shipType = second.getShipType();
            assertNotNull(shipType, "2 件目 ship_type");
            assertEquals(2, shipType.size(), "ship_type 件数");
            assertTrue(shipType.contains("海防艦"), "ship_type に海防艦が含まれること");
            assertTrue(shipType.contains("駆逐艦"), "ship_type に駆逐艦が含まれること");

            // greatSuccessCondition が from() で正しく展開されていること（value と item）
            MissionCondition greatSuccess = root.getGreatSuccessCondition();
            assertNotNull(greatSuccess, "greatSuccessCondition が存在すること");
            assertEquals("AND", greatSuccess.getOperator(), "greatSuccessCondition operator");
            assertEquals(2, greatSuccess.getConditions().size(), "greatSuccessCondition conditions 件数");
            MissionCondition gsFirst = greatSuccess.getConditions().get(0);
            assertEquals("艦隊", gsFirst.getType(), "greatSuccessCondition 先頭 type");
            assertEquals("キラキラ", gsFirst.getCountType(), "greatSuccessCondition 先頭 count_type");
            assertEquals(4, gsFirst.getValue(), "greatSuccessCondition 先頭 value");
            assertNull(gsFirst.getItem(), "greatSuccessCondition 先頭 item は null");
            MissionCondition gsSecond = greatSuccess.getConditions().get(1);
            assertEquals("艦隊", gsSecond.getType(), "greatSuccessCondition 2 件目 type");
            assertEquals("装備", gsSecond.getCountType(), "greatSuccessCondition 2 件目 count_type");
            assertEquals("ドラム缶(輸送用)", gsSecond.getItem(), "greatSuccessCondition 2 件目 item");
            assertEquals(3, gsSecond.getValue(), "greatSuccessCondition 2 件目 value");
        }
    }
}
