package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;

/**
 * record のプリミティブ型（boolean）で、JSON に項目が無い場合の Jackson の挙動を検証する。
 * <ul>
 *   <li>デフォルト（FAIL_ON_NULL_FOR_PRIMITIVES 有効）: 項目欠けは null として扱われ、プリミティブにマップできず例外となる。</li>
 *   <li>FAIL_ON_NULL_FOR_PRIMITIVES を無効にした Reader: 項目欠け時はプリミティブのデフォルト値（boolean → false）が入る。</li>
 *   <li>Jackson 3 では @JsonProperty(defaultValue = "false") は record の Creator 引数で項目が欠けている場合には適用されず、やはり例外となる。項目欠けを許容するには Reader で FAIL_ON_NULL_FOR_PRIMITIVES を無効にする必要がある。</li>
 * </ul>
 */
class FailOnNullForPrimitivesTest {

    /** boolean に項目が無い JSON。 */
    private static final String JSON_MISSING_BOOLEAN = "{\"name\":\"a\"}";

    /** boolean を含む JSON。 */
    private static final String JSON_WITH_BOOLEAN = "{\"name\":\"a\",\"flag\":true}";

    /** テスト用 record。プリミティブ boolean にアノテーションなし。 */
    private static record NameAndFlag(String name, boolean flag) {
    }

    /**
     * デフォルトの Reader（FAIL_ON_NULL_FOR_PRIMITIVES 有効）で、record の boolean が JSON に無い場合に例外が発生することを確認する。
     */
    @Test
    void defaultReader_recordWithPrimitiveBoolean_missingKey_throws() {
        assertThrows(JacksonException.class, () ->
                JsonMappers.READER_WITH_COMMENTS
                        .forType(NameAndFlag.class)
                        .readValue(JSON_MISSING_BOOLEAN),
                "boolean 項目が無い JSON をデフォルト Reader で読むと MismatchedInputException（null を boolean にマップできない）がスローされる");
    }

    /**
     * FAIL_ON_NULL_FOR_PRIMITIVES を無効にした Reader では、項目欠け時にプリミティブのデフォルト値（false）が入ることを確認する。
     */
    @Test
    void readerWithoutFailOnNullForPrimitives_recordWithPrimitiveBoolean_missingKey_deserializesWithFalse() throws Exception {
        ObjectReader reader = JsonMappers.MAPPER.reader()
                .without(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

        NameAndFlag bean = reader.forType(NameAndFlag.class).readValue(JSON_MISSING_BOOLEAN);

        assertEquals("a", bean.name());
        assertFalse(bean.flag(), "項目が無い場合、boolean には false が入る");
    }

    /**
     * FAIL_ON_NULL_FOR_PRIMITIVES 無効の Reader で、boolean が JSON に含まれる場合はその値が使われることを確認する。
     */
    @Test
    void readerWithoutFailOnNullForPrimitives_recordWithPrimitiveBoolean_keyPresent_usesValue() throws Exception {
        ObjectReader reader = JsonMappers.MAPPER.reader()
                .without(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

        NameAndFlag bean = reader.forType(NameAndFlag.class).readValue(JSON_WITH_BOOLEAN);

        assertEquals("a", bean.name());
        assertEquals(true, bean.flag());
    }

    /**
     * デフォルト Reader では @JsonProperty(defaultValue = "false") を付けても、record の Creator で項目が欠けていると例外になることを確認する。
     * （Jackson 3 では Creator 経由デシリアライズ時に defaultValue が欠損プロパティに適用されないため。項目欠けを許容するには Reader で FAIL_ON_NULL_FOR_PRIMITIVES を無効にする。）
     */
    @Test
    void defaultReader_recordWithDefaultValueAnnotation_missingKey_stillThrows() {
        assertThrows(JacksonException.class, () ->
                JsonMappers.READER_WITH_COMMENTS
                        .forType(NameAndFlagWithDefault.class)
                        .readValue(JSON_MISSING_BOOLEAN));
    }

    /** テスト用 record。@JsonProperty(defaultValue = "false") 付き（項目欠け時はデフォルト Reader では仍々例外となることを検証する用）。 */
    private static record NameAndFlagWithDefault(
            String name,
            @JsonProperty(defaultValue = "false") boolean flag) {
    }
}
