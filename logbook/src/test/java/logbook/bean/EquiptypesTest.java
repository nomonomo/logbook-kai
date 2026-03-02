package logbook.bean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import logbook.internal.JsonMappers;

/**
 * {@link Equiptypes} のデシリアライズを STRICT_CREATOR_READER_WITH_COMMENTS で検証する。
 */
class EquiptypesTest {

    private static final String RESOURCE = "logbook/supplemental/equiptypes.json";

    @Test
    void strictCreatorReaderWithComments_deserializesEquiptypesJson_fieldByFieldCheck() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "テスト用 equiptypes.json が存在すること");

            Equiptypes root = JsonMappers.STRICT_CREATOR_READER_WITH_COMMENTS
                    .forType(Equiptypes.class)
                    .readValue(in);

            var categories = root.categories();
            assertNotNull(categories);
            assertEquals(2, categories.size(), "categories 件数");

            Equiptypes.Category first = categories.get(0);
            assertEquals("砲・機銃", first.name(), "先頭カテゴリ name");
            assertArrayEquals(new int[] { 1, 2, 3, 4, 21 }, first.types(), "先頭カテゴリ types");

            Equiptypes.Category second = categories.get(1);
            assertEquals("魚雷", second.name(), "2 件目カテゴリ name");
            assertArrayEquals(new int[] { 5, 22, 32 }, second.types(), "2 件目カテゴリ types");
        }
    }
}
