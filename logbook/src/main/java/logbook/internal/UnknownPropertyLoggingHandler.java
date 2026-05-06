package logbook.internal;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.ValueDeserializer;

/**
 * デシリアライズ時に未知プロパティを検出した際にログ出力し、スキップするハンドラ。
 * FAIL_ON_UNKNOWN_PROPERTIES を外した Reader と組み合わせて使用する。
 */
@Slf4j
public final class UnknownPropertyLoggingHandler extends DeserializationProblemHandler {

    @Override
    public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p,
            ValueDeserializer<?> deserializer, Object beanOrClass, String propertyName) {
        String targetType = beanOrClass instanceof Class<?> c
                ? c.getSimpleName()
                : beanOrClass.getClass().getSimpleName();
        log.debug("未知のプロパティをスキップ: type={}, property={}", targetType, propertyName);
        try {
            p.skipChildren();
        } catch (Exception e) {
            log.debug("未知プロパティのスキップ中に例外", e);
            return false;
        }
        return true;
    }
}
