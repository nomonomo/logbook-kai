package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ValueDeserializer;

/**
 * {@link UnknownPropertyLoggingHandler} の動作を検証するテスト。
 * 未知のプロパティ名がハンドラに渡ることを検証する。
 */
class UnknownPropertyLoggingHandlerTest {

    /** テスト用 DTO。JsonProperty で JSON キーを明示。Lombok 不使用。 */
    public record UnknownPropertyTestBean(
            @JsonProperty("name") String name,
            @JsonProperty("value") Integer value) {
    }

    /**
     * 未知プロパティ名を収集するテスト用ハンドラ。
     */
    private static final class CollectingUnknownPropertyHandler extends DeserializationProblemHandler {
        private final List<String> unknownPropertyNames = new ArrayList<>();

        List<String> getUnknownPropertyNames() {
            return unknownPropertyNames;
        }

        @Override
        public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p,
                ValueDeserializer<?> deserializer, Object beanOrClass, String propertyName) {
            unknownPropertyNames.add(propertyName);
            try {
                p.skipChildren();
            } catch (Exception e) {
                return false;
            }
            return true;
        }
    }

    @Test
    void unknownPropertiesAreReportedToHandler() throws Exception {
        String resource = "logbook/internal/unknown_property_test.json";
        CollectingUnknownPropertyHandler handler = new CollectingUnknownPropertyHandler();
        var reader = JsonMapper.builder()
                .build()
                .reader()
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .withHandler(handler)
                .forType(UnknownPropertyTestBean.class);

        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            UnknownPropertyTestBean bean = reader.readValue(in);
            assertEquals("a", bean.name());
            assertEquals(1, bean.value());
        }

        List<String> unknown = handler.getUnknownPropertyNames();
        assertTrue(unknown.contains("unknownField"), "未知プロパティ unknownField がハンドラに渡されること");
        assertTrue(unknown.contains("anotherUnknown"), "未知プロパティ anotherUnknown がハンドラに渡されること");
    }
}
