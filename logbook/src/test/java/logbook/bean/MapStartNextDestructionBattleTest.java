package logbook.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import logbook.bean.MapStartNext.DestructionBattle;
import logbook.internal.BattleEventLogs;
import logbook.internal.log.BattleEventLogFormat;

/**
 * 基地空襲（api_destruction_battle）のパース
 */
public class MapStartNextDestructionBattleTest {

    @Test
    public void testToMapStartNextWithDestructionBattle() throws Exception {
        putShipMst(1586, "テスト敵艦");

        Path p = Paths.get("./src/test/resources/logbook/bean/req_map_next_destruction_battle.json");
        try (Reader reader = Files.newBufferedReader(p);
                JsonReader jsonReader = Json.createReader(reader)) {
            JsonObject json = jsonReader.readObject().getJsonObject("api_data");
            MapStartNext next = MapStartNext.toMapStartNext(json);

            assertEquals(Integer.valueOf(6), next.getMapareaId());
            assertEquals(Integer.valueOf(5), next.getMapinfoNo());
            assertEquals(Integer.valueOf(6), next.getNo());

            DestructionBattle destruction = next.getDestructionBattle();
            assertNotNull(destruction);
            assertEquals(Integer.valueOf(4), destruction.getLostKind());
            assertEquals(Integer.valueOf(1), destruction.getFormation().get(0));
            assertEquals(Integer.valueOf(3), destruction.getFormation().get(1));
            assertEquals(Integer.valueOf(1), destruction.getFormation().get(2));
            assertEquals(List.of(1586, 1615, 1592, 1577, 1577, 1577), destruction.getShipKe());
            assertEquals(Integer.valueOf(350), destruction.getENowhps().get(0));
            assertEquals(Integer.valueOf(350), destruction.getEMaxhps().get(0));
            assertEquals(List.of(200, 200, 200), destruction.getFNowhps());
            assertEquals(List.of(200, 200, 200), destruction.getFMaxhps());
            assertEquals(Integer.valueOf(1547), destruction.getESlot().get(0).get(0));

            assertNotNull(destruction.getAirBaseAttack());
            assertNotNull(destruction.getAirBaseAttack().getStage1());
            assertEquals(Integer.valueOf(2), destruction.getAirBaseAttack().getStage1().getDispSeiku());
            assertNull(destruction.getAirBaseAttack().getStage2());
            assertNull(destruction.getAirBaseAttack().getStage3());
            assertFalse(destruction.getAirBaseAttack().getMapSquadronPlane().isEmpty());
            assertEquals(BattleTypes.DispSeiku.航空優勢,
                    BattleTypes.DispSeiku.toDispSeiku(destruction.getAirBaseAttack().getStage1().getDispSeiku()));

            BattleEventLog event = BattleEventLogs.createAirRaid(next);
            assertNotNull(event);
            assertEquals("", event.getEfleet(), "損害結果は内容欄へ表示し、敵艦隊欄には重複させない");
            BattleEventLogFormat format = new BattleEventLogFormat();
            String csv = format.format(event);
            assertTrue(csv.contains("\"テスト敵艦\""), "最小マスタ登録後は ID ではなく艦名になる");
            assertFalse(csv.contains("\"1586\""));
            assertTrue(csv.contains("350/350"));
            assertEquals(List.of("200/200", "200/200", "200/200"), event.getBaseHp());
            assertTrue(format.header().contains("基地1HP,基地2HP,基地3HP"));
            assertFalse(format.header().contains("API"));
            assertFalse(csv.contains("api_map_squadron_plane"), "詳細JSONはイベントCSVへ含めない");
        }
    }

    private static void putShipMst(int id, String name) {
        ShipMst mst = new ShipMst();
        mst.setId(id);
        mst.setName(name);
        ShipMstCollection.get().getShipMap().put(id, mst);
    }
}
