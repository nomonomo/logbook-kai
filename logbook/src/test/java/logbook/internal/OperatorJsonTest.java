package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;


/**
 * Jackson 2 -> 3 移行で壊れやすい Enum の既定入出力仕様を固定する回帰テスト。
 * <p>
 * {@link Operator} は {@link Operator#toString()} で日本語ラベルを返すため、JSON では
 * {@link Enum#name()}（GE/LE/...）を使う仕様を明示的に守る必要がある。
 * この仕様は {@link JsonMappers#MAPPER} の EnumFeature 設定で実現している。
 */
class OperatorJsonTest {

    @Test
    void jsonUsesEnumConstantName() throws Exception {
        assertEquals("\"GE\"", JsonMappers.MAPPER.writeValueAsString(Operator.GE));
        assertEquals("\"LE\"", JsonMappers.MAPPER.writeValueAsString(Operator.LE));
    }

    @Test
    void deserializeFromConstantName() throws Exception {
        assertEquals(Operator.GE, JsonMappers.LENIENT_READER.forType(Operator.class).readValue("\"GE\""));
        assertEquals(Operator.EQ, JsonMappers.LENIENT_READER.forType(Operator.class).readValue("\"EQ\""));
    }

    @Test
    void deserializeFromJapaneseLabelShouldFail() {
        // toString() の日本語ラベルを受け入れると旧データ互換を壊すため、失敗させる。
        assertThrows(Exception.class,
                () -> JsonMappers.LENIENT_READER.forType(Operator.class).readValue("\"以上\""));
        assertThrows(Exception.class,
                () -> JsonMappers.LENIENT_READER.forType(Operator.class).readValue("\"以下\""));
    }

    @Test
    void roundTripMatchesConfigJsonConvention() throws Exception {
        String original = "\"GT\"";
        Operator parsed = JsonMappers.LENIENT_READER.forType(Operator.class).readValue(original);
        String out = JsonMappers.MAPPER.writeValueAsString(parsed);
        assertEquals(original, out);
    }
}
