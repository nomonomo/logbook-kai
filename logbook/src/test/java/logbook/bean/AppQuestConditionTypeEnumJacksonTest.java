package logbook.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import logbook.bean.AppQuestCondition.Type;
import logbook.internal.JsonMappers;
import tools.jackson.core.JacksonException;

/**
 * Jackson 3 で JSON から enum（{@link AppQuestCondition.Type}）を record に読み込む際の挙動を検証する。
 * <ul>
 *   <li>通常読み込み: 宣言した値（出撃, 遠征）以外が来た場合に例外となることを確認</li>
 *   <li>@JsonProperty(required = true) を付けた場合: 必須プロパティ欠損時および不正な enum 値時の挙動を確認</li>
 * </ul>
 */
class AppQuestConditionTypeEnumJacksonTest {

    /** 型のみを持つ record（通常読み込み用）。required なし。 */
    private static record TypeOnlyRecord(Type type) {
    }

    /** 型を必須にした record。@JsonProperty(required = true) 使用。 */
    private static record TypeRequiredRecord(
            @JsonProperty(value = "type", required = true) Type type) {
    }

    @Nested
    @DisplayName("通常読み込み（MAPPER.reader / LENIENT_READER）")
    class NormalRead {

        @Test
        @DisplayName("有効な enum 値「出撃」ならデシリアライズ成功")
        void validEnum_出撃_succeeds() throws Exception {
            String json = "{\"type\":\"出撃\"}";
            TypeOnlyRecord r = JsonMappers.MAPPER.reader()
                    .forType(TypeOnlyRecord.class)
                    .readValue(json);
            assertEquals(Type.出撃, r.type());
        }

        @Test
        @DisplayName("有効な enum 値「遠征」ならデシリアライズ成功")
        void validEnum_遠征_succeeds() throws Exception {
            String json = "{\"type\":\"遠征\"}";
            TypeOnlyRecord r = JsonMappers.MAPPER.reader()
                    .forType(TypeOnlyRecord.class)
                    .readValue(json);
            assertEquals(Type.遠征, r.type());
        }

        @Test
        @DisplayName("宣言外の値が来た場合は JacksonException")
        void unknownEnumValue_throws() {
            String json = "{\"type\":\"不明\"}";
            assertThrows(JacksonException.class, () ->
                    JsonMappers.MAPPER.reader()
                            .forType(TypeOnlyRecord.class)
                            .readValue(json));
        }

        @Test
        @DisplayName("type キーが無い場合は null になる（Creator 必須の対象外）")
        void missingType_keyAbsent_typeIsNull() throws Exception {
            String json = "{}";
            TypeOnlyRecord r = JsonMappers.MAPPER.reader()
                    .forType(TypeOnlyRecord.class)
                    .readValue(json);
            assertNull(r.type());
        }
    }

    @Nested
    @DisplayName("@JsonProperty(required = true) + STRICT_CREATOR_READER")
    class RequiredPropertyWithStrictCreator {

        @Test
        @DisplayName("有効な enum 値ならデシリアライズ成功")
        void validEnum_succeeds() throws Exception {
            String json = "{\"type\":\"出撃\"}";
            TypeRequiredRecord r = JsonMappers.STRICT_CREATOR_READER
                    .forType(TypeRequiredRecord.class)
                    .readValue(json);
            assertEquals(Type.出撃, r.type());
        }

        @Test
        @DisplayName("必須プロパティ type が無い場合は JacksonException")
        void missingRequiredProperty_throws() {
            String json = "{}";
            assertThrows(JacksonException.class, () ->
                    JsonMappers.STRICT_CREATOR_READER
                            .forType(TypeRequiredRecord.class)
                            .readValue(json));
        }

        @Test
        @DisplayName("宣言外の enum 値が来た場合も JacksonException（必須とは別の失敗）")
        void unknownEnumValue_throws() {
            String json = "{\"type\":\"第三\"}";
            assertThrows(JacksonException.class, () ->
                    JsonMappers.STRICT_CREATOR_READER
                            .forType(TypeRequiredRecord.class)
                            .readValue(json));
        }
    }

    @Nested
    @DisplayName("通常読み込み（mapper.reader().forType()） + @JsonProperty(required = true)")
    class NormalReadWithRequiredProperty {

        @Test
        @DisplayName("有効な enum 値ならデシリアライズ成功")
        void validEnum_succeeds() throws Exception {
            String json = "{\"type\":\"出撃\"}";
            TypeRequiredRecord r = JsonMappers.MAPPER.reader()
                    .forType(TypeRequiredRecord.class)
                    .readValue(json);
            assertEquals(Type.出撃, r.type());
        }

        @Test
        @DisplayName("必須プロパティ type が無い場合は JacksonException")
        void missingRequiredProperty_throws() {
            String json = "{}";
            assertThrows(JacksonException.class, () ->
                    JsonMappers.MAPPER.reader()
                            .forType(TypeRequiredRecord.class)
                            .readValue(json));
        }

        @Test
        @DisplayName("宣言外の enum 値が来た場合も JacksonException（必須とは別の失敗）")
        void unknownEnumValue_throws() {
            String json = "{\"type\":\"第三\"}";
            assertThrows(JacksonException.class, () ->
                    JsonMappers.MAPPER.reader()
                            .forType(TypeRequiredRecord.class)
                            .readValue(json));
        }
    }

    /**
     * JacksonException 発生時のメッセージを標準出力に表示する。
     * メッセージから原因を判断できるかを確認するためのテスト。
     */
    @Nested
    @DisplayName("JacksonException メッセージ確認（標準出力で内容を確認）")
    class JacksonExceptionMessageDisplay {

        @Test
        @DisplayName("通常読み込み・宣言外の enum 値 → 例外メッセージを表示")
        void displayMessage_unknownEnumValue_normalRead() {
            String json = "{\"type\":\"不明\"}";
            JacksonException ex = assertThrows(JacksonException.class, () ->
                    JsonMappers.MAPPER.reader()
                            .forType(TypeOnlyRecord.class)
                            .readValue(json));
            assertNotNull(ex.getMessage(), "メッセージが設定されていること");
            printExceptionInfo("通常読み込み・enum に無い値 \"不明\"", ex);
        }

        @Test
        @DisplayName("STRICT_CREATOR・必須プロパティ欠損 → 例外メッセージを表示")
        void displayMessage_missingRequiredProperty_strictCreator() {
            String json = "{}";
            JacksonException ex = assertThrows(JacksonException.class, () ->
                    JsonMappers.STRICT_CREATOR_READER
                            .forType(TypeRequiredRecord.class)
                            .readValue(json));
            assertNotNull(ex.getMessage(), "メッセージが設定されていること");
            printExceptionInfo("STRICT_CREATOR・type キーなし", ex);
        }

        @Test
        @DisplayName("STRICT_CREATOR・宣言外の enum 値 → 例外メッセージを表示")
        void displayMessage_unknownEnumValue_strictCreator() {
            String json = "{\"type\":\"第三\"}";
            JacksonException ex = assertThrows(JacksonException.class, () ->
                    JsonMappers.STRICT_CREATOR_READER
                            .forType(TypeRequiredRecord.class)
                            .readValue(json));
            assertNotNull(ex.getMessage(), "メッセージが設定されていること");
            printExceptionInfo("STRICT_CREATOR・enum に無い値 \"第三\"", ex);
        }

        private void printExceptionInfo(String scenario, JacksonException ex) {
            System.out.println("--- JacksonException メッセージ確認: " + scenario + " ---");
            System.out.println("getMessage(): " + ex.getMessage());
            if (ex.getCause() != null) {
                System.out.println("getCause(): " + ex.getCause());
                if (ex.getCause().getMessage() != null && !ex.getCause().getMessage().equals(ex.getMessage())) {
                    System.out.println("getCause().getMessage(): " + ex.getCause().getMessage());
                }
            }
            System.out.println("----------------------------------------");
        }
    }
}
