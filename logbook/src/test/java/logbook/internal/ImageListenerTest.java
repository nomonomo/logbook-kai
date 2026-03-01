package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import logbook.bean.Spritesmith;

/**
 * {@link ImageListener#forEachFrame} の分解ロジックを検証する。
 * テスト内で BufferedImage を生成し、common_shutter.json と合わせて forEachFrame を呼ぶ。
 */
class ImageListenerTest {

    private static final String JSON_RESOURCE = "logbook/sprite/common_shutter.json";
    private static final int IMAGE_WIDTH = 2405;
    private static final int IMAGE_HEIGHT = 815;

    @Test
    void forEachFrame_invokesCallbackFourTimes_withCorrectFirstSubimageSize() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(JSON_RESOURCE)) {
            assertNotNull(in, "common_shutter.json が存在すること");

            Spritesmith sprite = JsonMappers.LENIENT_READER
                    .forType(Spritesmith.class)
                    .readValue(in);

            BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);

            Map<String, BufferedImage> byName = new LinkedHashMap<>();
            ImageListener.forEachFrame(sprite, image, (name, subimage) -> byName.put(name, subimage));

            assertEquals(4, byName.size(), "コールバックは 4 回呼ばれる");
            BufferedImage firstSub = byName.get("common_shutter_0");
            assertNotNull(firstSub, "common_shutter_0 の subimage が得られること");
            assertEquals(1200, firstSub.getWidth(), "先頭フレームの subimage 幅（common_shutter_0.frame.w）");
            assertEquals(405, firstSub.getHeight(), "先頭フレームの subimage 高さ（common_shutter_0.frame.h）");
        }
    }
}
