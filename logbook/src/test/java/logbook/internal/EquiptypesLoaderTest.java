package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import logbook.bean.Equiptypes;

/**
 * {@link EquiptypesLoader#load} の読み込み結果を検証する。
 */
class EquiptypesLoaderTest {

    private static final String RESOURCE = "logbook/supplemental/equiptypes.json";

    @Test
    void load_returnsEquiptypesWithExpectedCategories() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "テスト用 equiptypes.json が存在すること");

            Equiptypes root = EquiptypesLoader.load(in);

            var categories = root.categories();
            assertNotNull(categories);
            assertEquals(2, categories.size(), "categories 件数");

            Equiptypes.Category first = categories.get(0);
            assertEquals("砲・機銃", first.name());
            assertArrayEquals(new int[] { 1, 2, 3, 4, 21 }, first.types());

            Equiptypes.Category second = categories.get(1);
            assertEquals("魚雷", second.name());
            assertArrayEquals(new int[] { 5, 22, 32 }, second.types());
        }
    }
}
