package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.core.JacksonException;

/**
 * {@link JsonMappers#STRICT_CREATOR_READER}（FAIL_ON_MISSING_CREATOR_PROPERTIES 有効）の挙動を検証する。
 * <ul>
 *   <li>Creator あり（@JsonCreator + @JsonProperty 付きクラス）: JSON に項目が無いと例外が発生することを確認</li>
 *   <li>Creator なし（引数なしコンストラクタ + setter）: 同じ JSON でも例外は発生せず、欠損項目は null になることを確認</li>
 *   <li>record（キーとコンポーネント名一致・@JsonProperty なし）: 項目欠けで例外、全項目ありで成功</li>
 * </ul>
 */
class FailOnMissingCreatorPropertiesTest {

    /** value が無い JSON（項目欠けの検証用）。 */
    private static final String JSON_MISSING_VALUE = "{\"name\":\"a\"}";

    // --- Creator あり: @JsonCreator + @JsonProperty 付き class ---

    /** テスト用 DTO。Creator 経由でデシリアライズ。JSON に項目が無いと例外になる想定。 */
    @SuppressWarnings("unused")
    private static final class NameValueCreatorBean {
        private final String name;
        private final Integer value;

        @JsonCreator
        NameValueCreatorBean(
                @JsonProperty("name") String name,
                @JsonProperty("value") Integer value) {
            this.name = name;
            this.value = value;
        }

        String name() { return name; }
        Integer value() { return value; }
    }

    @Test
    void failOnMissingCreatorProperties_withCreatorBean_missingProperty_throws() {
        assertThrows(JacksonException.class, () ->
                JsonMappers.STRICT_CREATOR_READER.forType(NameValueCreatorBean.class).readValue(JSON_MISSING_VALUE));
    }

    @Test
    void failOnMissingCreatorProperties_withCreatorBean_allPropertiesPresent_succeeds() {
        String json = "{\"name\":\"a\",\"value\":1}";
        NameValueCreatorBean bean = JsonMappers.STRICT_CREATOR_READER
                .forType(NameValueCreatorBean.class)
                .readValue(json);
        assertEquals("a", bean.name());
        assertEquals(1, bean.value());
    }

    // --- Creator なし: 引数なしコンストラクタ + setter（同じ name/value 構造）---

    /** テスト用 DTO。setter 経由でデシリアライズ。FAIL_ON_MISSING_CREATOR_PROPERTIES の対象外。 */
    @SuppressWarnings("unused")
    private static final class NameValueSetterBean {
        private String name;
        private Integer value;

        NameValueSetterBean() {}

        String getName() { return name; }
        void setName(String name) { this.name = name; }
        Integer getValue() { return value; }
        void setValue(Integer value) { this.value = value; }
    }

    @Test
    void failOnMissingCreatorProperties_withSetterBean_missingProperty_doesNotThrow() {
        NameValueSetterBean bean = JsonMappers.STRICT_CREATOR_READER
                .forType(NameValueSetterBean.class)
                .readValue(JSON_MISSING_VALUE);
        assertEquals("a", bean.getName());
        assertNull(bean.getValue(), "JSON に存在しない項目は null");
    }

    @Test
    void failOnMissingCreatorProperties_withSetterBean_allPropertiesPresent_succeeds() {
        String json = "{\"name\":\"a\",\"value\":1}";
        NameValueSetterBean bean = JsonMappers.STRICT_CREATOR_READER
                .forType(NameValueSetterBean.class)
                .readValue(json);
        assertEquals("a", bean.getName());
        assertEquals(1, bean.getValue());
    }

    // --- record: キーとコンポーネント名一致（@JsonProperty なし）---

    /** テスト用 record。JSON キーとコンポーネント名が一致するため @JsonProperty は付けない。 */
    private static record NameValueRecord(String name, Integer value) {
    }

    @Test
    void failOnMissingCreatorProperties_withRecord_missingProperty_throws() {
        assertThrows(JacksonException.class, () ->
                JsonMappers.STRICT_CREATOR_READER.forType(NameValueRecord.class).readValue(JSON_MISSING_VALUE));
    }

    @Test
    void failOnMissingCreatorProperties_withRecord_allPropertiesPresent_succeeds() {
        String json = "{\"name\":\"a\",\"value\":1}";
        NameValueRecord bean = JsonMappers.STRICT_CREATOR_READER
                .forType(NameValueRecord.class)
                .readValue(json);
        assertEquals("a", bean.name());
        assertEquals(1, bean.value());
    }
}
