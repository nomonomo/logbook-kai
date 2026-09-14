package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * {@link ImageListenerConfigLoader} のテスト。
 */
class ImageListenerConfigLoaderTest {

    @Test
    void parsesReleaseStyleProperties() throws Exception {
        String text = """
                img.category.1=common
                img.category.2=duty
                img.category.3=sally
                """;
        ImageListenerConfig config = ImageListenerConfigLoader.parse(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of("common", "duty", "sally"), config.imgCategories());
    }

    @Test
    void ignoresUnknownKeysIncludingRemovedMapFlag() throws Exception {
        String text = """
                img.category.1=common
                resources.map.enabled=true
                """;
        ImageListenerConfig config = ImageListenerConfigLoader.parse(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of("common"), config.imgCategories());
    }

    @Test
    void allowsGapsAndSortsByIndex() throws Exception {
        // 欠番（3〜9）は許容し、番号昇順で一覧化する
        String text = """
                img.category.10=supply
                img.category.2=duty
                img.category.1=common
                """;
        ImageListenerConfig config = ImageListenerConfigLoader.parse(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of("common", "duty", "supply"), config.imgCategories());
    }

    @Test
    void ignoresCategoryIndexOverflow() throws Exception {
        // \\d+ にマッチしても Integer 範囲外なら NumberFormatException
        String text = """
                img.category.1=common
                img.category.99999999999=overflow
                """;
        ImageListenerConfig config = ImageListenerConfigLoader.parse(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of("common"), config.imgCategories());
    }

    @Test
    void keepsFirstOnDuplicateIndex() throws Exception {
        // 01 と 1 は同じ番号。キー名ソートで img.category.01 が先
        String text = """
                img.category.1=common
                img.category.01=duplicate
                img.category.2=duty
                """;
        ImageListenerConfig config = ImageListenerConfigLoader.parse(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of("duplicate", "duty"), config.imgCategories());
    }
}
