package logbook.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;

import logbook.bean.ShipSupplementalInfo;

/**
 * {@link ShipSupplementalLoader#loadSupplementalMap} の読み込み結果を検証する。
 * Ships の static 初期化を避けるため、Ships ではなく ShipSupplementalLoader を直接呼ぶ。
 */
class ShipsSupplementalLoadTest {

    private static final String RESOURCE = "logbook/supplemental/ships.json";

    @Test
    void loadSupplementalMap_returnsMapWithExpectedEntries() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "テスト用 ships.json が存在すること");

            Map<Integer, ShipSupplementalInfo> map = ShipSupplementalLoader.loadSupplementalMap(in);

            assertEquals(3, map.size(), "件数（id 1, 2, 6 の 3 件）");

            ShipSupplementalInfo first = map.get(1);
            assertNotNull(first);
            assertEquals(1, first.id());
            assertEquals("睦月", first.name());
            assertEquals(16, first.min_taisen());
            assertEquals(37, first.min_kaihi());
            assertEquals(4, first.min_sakuteki());

            ShipSupplementalInfo second = map.get(2);
            assertNotNull(second);
            assertEquals(2, second.id());
            assertEquals("如月", second.name());
        }
    }
}
