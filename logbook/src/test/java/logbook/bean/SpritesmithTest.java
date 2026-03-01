package logbook.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;

import logbook.internal.JsonMappers;

/**
 * {@link Spritesmith} のデシリアライズを common_shutter.json で検証する。
 */
class SpritesmithTest {

    private static final String RESOURCE = "logbook/sprite/common_shutter.json";

    @Test
    void lenientReader_deserializesCommonShutterJson_fieldByFieldCheck() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "common_shutter.json が存在すること");

            Spritesmith sprite = JsonMappers.LENIENT_READER
                    .forType(Spritesmith.class)
                    .readValue(in);

            Map<String, Spritesmith.Frame> frames = sprite.frames();
            assertNotNull(frames);
            assertEquals(4, frames.size(), "frames 件数");

            Spritesmith.Frame first = frames.get("common_shutter_0");
            assertNotNull(first);

            Spritesmith.Rect frameRect = first.frame();
            assertNotNull(frameRect);
            assertEquals(0, frameRect.x(), "common_shutter_0.frame.x");
            assertEquals(0, frameRect.y(), "common_shutter_0.frame.y");
            assertEquals(1200, frameRect.w(), "common_shutter_0.frame.w");
            assertEquals(405, frameRect.h(), "common_shutter_0.frame.h");

            assertFalse(first.rotated(), "common_shutter_0.rotated");
            assertFalse(first.trimmed(), "common_shutter_0.trimmed");

            Spritesmith.Rect spriteSourceSize = first.spriteSourceSize();
            assertNotNull(spriteSourceSize);
            assertEquals(0, spriteSourceSize.x());
            assertEquals(0, spriteSourceSize.y());
            assertEquals(1200, spriteSourceSize.w());
            assertEquals(405, spriteSourceSize.h());

            Spritesmith.Size sourceSize = first.sourceSize();
            assertNotNull(sourceSize);
            assertEquals(1200, sourceSize.w(), "sourceSize は w,h のみ（Size 型）");
            assertEquals(405, sourceSize.h(), "sourceSize は w,h のみ（Size 型）");
        }
    }
}
