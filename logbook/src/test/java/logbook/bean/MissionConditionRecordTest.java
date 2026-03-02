package logbook.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import logbook.internal.JsonMappers;
import tools.jackson.core.JacksonException;

/**
 * {@link MissionConditionRecord} のデシリアライズを READER_WITH_COMMENTS で検証する。
 */
class MissionConditionRecordTest {

    private static final String RESOURCE_01_MISSING_BRACKET = "logbook/mission/1/01.json";
    private static final String RESOURCE_99 = "logbook/mission/5/99.json";

    @Test
    void readerWithComments_throwsWhenJsonHasMissingBrackets() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_01_MISSING_BRACKET)) {
            assertNotNull(in, "テスト用 1/01.json（括弧不足）が存在すること");

            assertThrows(JacksonException.class, () ->
                    JsonMappers.READER_WITH_COMMENTS
                            .forType(MissionConditionRecord.class)
                            .readValue(in),
                    "括弧が足りない JSON を読むと JacksonException がスローされること");
        }
    }

    @Test
    void readerWithComments_deserializes_5_99_json_structureAndDescription() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_99)) {
            assertNotNull(in, "テスト用 5/99.json が存在すること");

            MissionConditionRecord root = JsonMappers.READER_WITH_COMMENTS
                    .forType(MissionConditionRecord.class)
                    .readValue(in);

            assertEquals("AND", root.operator(), "ルート operator");
            assertEquals("テスト", root.description(), "ルート description");
            assertNotNull(root.conditions());
            assertEquals(1, root.conditions().size(), "ルート conditions 件数");

            // ネスト: conditions[0] は OR、その下に艦娘条件が2件
            MissionConditionRecord orBranch = root.conditions().get(0);
            assertEquals("OR", orBranch.operator(), "OR 枝 operator");
            assertNotNull(orBranch.conditions());
            assertEquals(2, orBranch.conditions().size(), "OR 枝 conditions 件数");
            MissionConditionRecord first = orBranch.conditions().get(0);
            assertEquals("艦娘", first.type(), "先頭 condition type");
            assertEquals(20, first.level(), "先頭 condition level");
            assertEquals(1, first.order(), "先頭 condition order");
            MissionConditionRecord second = orBranch.conditions().get(1);
            assertEquals("艦娘", second.type(), "2 件目 condition type");
            assertNotNull(second.ship_type());
            assertEquals(2, second.ship_type().size(), "2 件目 ship_type 件数");

            // greatSuccessCondition の検証
            assertNotNull(root.greatSuccessCondition(), "greatSuccessCondition が存在すること");
            assertEquals("AND", root.greatSuccessCondition().operator(), "greatSuccessCondition operator");
            assertEquals(2, root.greatSuccessCondition().conditions().size(), "greatSuccessCondition conditions 件数");
        }
    }
}
