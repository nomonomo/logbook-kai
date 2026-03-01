package logbook.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import logbook.internal.JsonMappers;
import tools.jackson.core.type.TypeReference;

/**
 * {@link ShipSupplementalInfo} のデシリアライズを STRICT_CREATOR_READER_WITH_COMMENTS で検証する。
 */
class ShipSupplementalInfoTest {

    private static final String RESOURCE = "logbook/supplemental/ships.json";

    @Test
    void strictCreatorReaderWithComments_deserializesShipsJson_firstEntryFieldByFieldCheck() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "テスト用 ships.json が存在すること");

            Map<String, List<ShipSupplementalInfo>> json = JsonMappers.STRICT_CREATOR_READER_WITH_COMMENTS
                    .forType(new TypeReference<Map<String, List<ShipSupplementalInfo>>>() {})
                    .readValue(in);

            List<ShipSupplementalInfo> list = json.get("ships");
            assertNotNull(list);
            assertEquals(3, list.size(), "ships 件数");

            ShipSupplementalInfo first = list.get(0);
            assertEquals(1, first.id(), "id");
            assertEquals("睦月", first.name(), "name");
            assertEquals(16, first.min_taisen(), "min_taisen");
            assertEquals(37, first.min_kaihi(), "min_kaihi");
            assertEquals(4, first.min_sakuteki(), "min_sakuteki");
        }
    }
}
