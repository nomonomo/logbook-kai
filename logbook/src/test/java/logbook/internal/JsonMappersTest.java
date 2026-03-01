package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.core.JacksonException;

/**
 * {@link JsonMappers} の各 Reader（MAPPER / LENIENT_READER /
 * LENIENT_READER_WITH_UNKNOWN_LOGGING / READER_WITH_COMMENTS）の振る舞いを検証するテスト。
 */
class JsonMappersTest {

    /** テスト用 DTO。unknown_property_test.json / comment_test.json の name, value 用。 */
    private static record NameValueBean(
            @JsonProperty("name") String name,
            @JsonProperty("value") Integer value) {
    }

    /**
     * テスト用 DTO。name, value に加え、JSON に存在しない項目（absentInJson）を持つ。
     * デシリアライズ時、absentInJson には null が入る想定。
     */
    private static record NameValueWithAbsentBean(
            @JsonProperty("name") String name,
            @JsonProperty("value") Integer value,
            @JsonProperty("absentInJson") String absentInJson) {
    }

    /**
     * MAPPER で未知プロパティ付き JSON を読む。
     * Jackson 3 では FAIL_ON_UNKNOWN_PROPERTIES のデフォルトが false のため例外にはならない場合がある。
     * その場合でも既知プロパティは正しくマッピングされることを確認する。
     */
    @Test
    void mapperWithUnknownPropertyJson() throws Exception {
        String resource = "logbook/internal/unknown_property_test.json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            NameValueBean bean = JsonMappers.MAPPER.reader()
                    .forType(NameValueBean.class)
                    .readValue(in);
            assertEquals("a", bean.name());
            assertEquals(1, bean.value());
        }
    }

    @Test
    void lenientReaderIgnoresUnknownProperties() throws Exception {
        String resource = "logbook/internal/unknown_property_test.json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            NameValueBean bean = JsonMappers.LENIENT_READER
                    .forType(NameValueBean.class)
                    .readValue(in);
            assertEquals("a", bean.name());
            assertEquals(1, bean.value());
        }
    }

    @Test
    void lenientReaderWithUnknownLoggingSucceeds() throws Exception {
        String resource = "logbook/internal/unknown_property_test.json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            NameValueBean bean = JsonMappers.LENIENT_READER_WITH_UNKNOWN_LOGGING
                    .forType(NameValueBean.class)
                    .readValue(in);
            assertEquals("a", bean.name());
            assertEquals(1, bean.value());
        }
    }

    @Test
    void readerWithCommentsParsesCommentJson() throws Exception {
        String resource = "logbook/internal/comment_test.json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            NameValueBean bean = JsonMappers.READER_WITH_COMMENTS
                    .forType(NameValueBean.class)
                    .readValue(in);
            assertEquals("withComment", bean.name());
            assertEquals(2, bean.value());
        }
    }

    @Test
    void mapperFailsOnCommentJson() throws Exception {
        String resource = "logbook/internal/comment_test.json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThrows(JacksonException.class, () ->
                    JsonMappers.MAPPER.reader().forType(NameValueBean.class).readValue(in));
        }
    }

    @Test
    void lenientReaderFailsOnCommentJson() throws Exception {
        String resource = "logbook/internal/comment_test.json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThrows(JacksonException.class, () ->
                    JsonMappers.LENIENT_READER.forType(NameValueBean.class).readValue(in));
        }
    }

    // --- JSON に存在しない項目を持つ DTO で各 Reader の挙動を検証（例外の有無・欠損項目は null）---

    private static final String RESOURCE_NAME_VALUE_ONLY = "logbook/internal/unknown_property_test.json";

    @Test
    void mapperWithDtoHavingAbsentField_noException_absentFieldIsNull() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_NAME_VALUE_ONLY)) {
            NameValueWithAbsentBean bean = JsonMappers.MAPPER.reader()
                    .forType(NameValueWithAbsentBean.class)
                    .readValue(in);
            assertEquals("a", bean.name());
            assertEquals(1, bean.value());
            assertNull(bean.absentInJson(), "JSON に存在しない項目は null");
        }
    }

    @Test
    void lenientReaderWithDtoHavingAbsentField_noException_absentFieldIsNull() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_NAME_VALUE_ONLY)) {
            NameValueWithAbsentBean bean = JsonMappers.LENIENT_READER
                    .forType(NameValueWithAbsentBean.class)
                    .readValue(in);
            assertEquals("a", bean.name());
            assertEquals(1, bean.value());
            assertNull(bean.absentInJson(), "JSON に存在しない項目は null");
        }
    }

    @Test
    void lenientReaderWithUnknownLoggingWithDtoHavingAbsentField_noException_absentFieldIsNull() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_NAME_VALUE_ONLY)) {
            NameValueWithAbsentBean bean = JsonMappers.LENIENT_READER_WITH_UNKNOWN_LOGGING
                    .forType(NameValueWithAbsentBean.class)
                    .readValue(in);
            assertEquals("a", bean.name());
            assertEquals(1, bean.value());
            assertNull(bean.absentInJson(), "JSON に存在しない項目は null");
        }
    }

    @Test
    void readerWithCommentsWithDtoHavingAbsentField_noException_absentFieldIsNull() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("logbook/internal/comment_test.json")) {
            NameValueWithAbsentBean bean = JsonMappers.READER_WITH_COMMENTS
                    .forType(NameValueWithAbsentBean.class)
                    .readValue(in);
            assertEquals("withComment", bean.name());
            assertEquals(2, bean.value());
            assertNull(bean.absentInJson(), "JSON に存在しない項目は null");
        }
    }
}
