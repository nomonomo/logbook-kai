package logbook.internal.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import logbook.bean.BattleLog;
import logbook.bean.BattleResult;
import logbook.bean.BattleTypes.ICombinedEcBattle;
import logbook.bean.BattleTypes.IFormation;
import logbook.bean.MapStartNext;
import logbook.internal.notify.ShipDroppedReport.Sources;
import logbook.notify.ShipDropped;
import logbook.notify.ShipDropped.DroppedItem;

class ShipDroppedReportTest {

    private static final Instant AT = Instant.parse("2026-01-02T12:34:56.789Z");

    private static final String HOST = "w11t.kancolle-server.com:443";

    @Test
    void dropIncludesRemodelCountAndHost() {
        BattleResult result = result("S", "甲");
        BattleResult.GetShip ship = new BattleResult.GetShip();
        ship.setShipId(41);
        result.setGetShip(ship);

        Optional<ShipDropped> dropped = ShipDroppedReport.from(
                battle(formation(1, List.of(-1, 1501, -1)), cell(1, 1, 3), result),
                new Sources(Map.of(40, 1, 42, 2), Map.of(40, 41, 41, 42), Map.of()),
                AT,
                HOST);

        assertTrue(dropped.isPresent());
        ShipDropped event = dropped.get();
        assertEquals(1, event.mapArea());
        assertEquals(1, event.mapNo());
        assertEquals(3, event.cell());
        assertEquals(1, event.enemyFormation());
        assertEquals(4, event.difficulty());
        assertEquals(41, event.dropShipId());
        assertEquals(3, event.ownedShipCount());
        assertEquals("S", event.rank());
        assertEquals(List.of(1501), event.enemyShips());
        assertNull(event.enemyCombinedShips());
        assertEquals("2026-01-02T12:34:56Z", event.endedAt());
        assertEquals("w11t.kancolle-server.com", event.host());
        assertNull(event.items());
    }

    @Test
    void noDropUsesMinusOne() {
        BattleResult result = result("A", "");
        result.setGetFlag(List.of(0, 0, 0));

        Optional<ShipDropped> dropped = ShipDroppedReport.from(
                battle(formation(5, List.of(1505, 1502)), cell(2, 5, 7), result),
                new Sources(Map.of(), Map.of(), Map.of()),
                AT,
                HOST);

        assertTrue(dropped.isPresent());
        assertEquals(2, dropped.get().mapArea());
        assertEquals(5, dropped.get().mapNo());
        assertEquals(7, dropped.get().cell());
        assertEquals(5, dropped.get().enemyFormation());
        assertEquals(0, dropped.get().difficulty());
        assertEquals(-1, dropped.get().dropShipId());
        assertEquals(0, dropped.get().ownedShipCount());
    }

    @Test
    void missingShipOnExpectedDropIsNotReported() {
        BattleResult result = result("S", "");
        result.setGetFlag(List.of(0, 1, 0));

        Optional<ShipDropped> dropped = ShipDroppedReport.from(
                battle(formation(1, List.of(1501)), cell(1, 1, 1), result),
                new Sources(Map.of(), Map.of(), Map.of()),
                AT,
                HOST);

        assertTrue(dropped.isEmpty());
    }

    @Test
    void sauryWithoutShipIsReported() {
        BattleResult result = result("S", "");
        result.setGetFlag(List.of(1, 0, 0));
        BattleResult.Useitem useitem = new BattleResult.Useitem();
        useitem.setUseitemId(68);
        useitem.setUseitemName("");
        result.setGetUseitem(useitem);

        Optional<ShipDropped> dropped = ShipDroppedReport.from(
                battle(formation(1, List.of(1501)), cell(1, 5, 11), result),
                new Sources(Map.of(), Map.of(), Map.of(68, 12)),
                AT,
                HOST);

        assertTrue(dropped.isPresent());
        assertEquals(-1, dropped.get().dropShipId());
        assertEquals(0, dropped.get().ownedShipCount());
        assertEquals(List.of(new DroppedItem(68, 12, null)), dropped.get().items());
    }

    @Test
    void combinedEnemyIsSecondFleet() {
        IFormation day = formation(11, List.of(-1, 1501));
        ICombinedEcBattle combined = mock(ICombinedEcBattle.class);
        when(combined.getShipKeCombined()).thenReturn(List.of(-1, 1505, 1502, 1502));
        doReturn(true).when(day).isICombinedEcBattle();
        doReturn(combined).when(day).asICombinedEcBattle();

        Optional<ShipDropped> dropped = ShipDroppedReport.from(
                battle(day, cell(48, 1, 11), result("B", "丙")),
                new Sources(Map.of(), Map.of(), Map.of()),
                AT,
                HOST);

        assertTrue(dropped.isPresent());
        assertEquals(48, dropped.get().mapArea());
        assertEquals(1, dropped.get().mapNo());
        assertEquals(11, dropped.get().cell());
        assertEquals(11, dropped.get().enemyFormation());
        assertEquals(2, dropped.get().difficulty());
        assertEquals(List.of(1501), dropped.get().enemyShips());
        assertEquals(List.of(1505, 1502, 1502), dropped.get().enemyCombinedShips());
    }

    @Test
    void itemsKeepUseitemAndEventUseitemOnly() {
        BattleResult result = result("S", "乙");
        BattleResult.Useitem useitem = new BattleResult.Useitem();
        useitem.setUseitemId(68);
        result.setGetUseitem(useitem);
        BattleResult.GetEventitem saury = new BattleResult.GetEventitem();
        saury.setType(1);
        saury.setId(54);
        saury.setValue(3);
        BattleResult.GetEventitem gear = new BattleResult.GetEventitem();
        gear.setType(3);
        gear.setId(379);
        gear.setValue(1);
        result.setGetEventitem(List.of(saury, gear));

        Optional<ShipDropped> dropped = ShipDroppedReport.from(
                battle(formation(2, List.of(1501)), cell(3, 2, 4), result),
                new Sources(Map.of(), Map.of(), Map.of(68, 12, 54, 1)),
                AT,
                HOST);

        assertTrue(dropped.isPresent());
        assertEquals(3, dropped.get().difficulty());
        assertEquals(List.of(
                new DroppedItem(68, 12, null),
                new DroppedItem(54, 1, 3)), dropped.get().items());
    }

    @Test
    void unknownFormationIsNotReported() {
        Optional<ShipDropped> dropped = ShipDroppedReport.from(
                battle(formation(9, List.of(1501)), cell(1, 1, 1), result("S", "")),
                new Sources(Map.of(), Map.of(), Map.of()),
                AT,
                HOST);

        assertTrue(dropped.isEmpty());
    }

    private static BattleLog battle(IFormation formation, MapStartNext cell, BattleResult result) {
        BattleLog log = new BattleLog();
        log.setBattle(formation);
        log.getNext().add(cell);
        log.setResult(result);
        return log;
    }

    private static IFormation formation(int enemy, List<Integer> ships) {
        IFormation battle = mock(IFormation.class);
        when(battle.getFormation()).thenReturn(List.of(1, enemy, 1));
        when(battle.getShipKe()).thenReturn(ships);
        return battle;
    }

    private static MapStartNext cell(int area, int info, int no) {
        MapStartNext cell = new MapStartNext();
        cell.setMapareaId(area);
        cell.setMapinfoNo(info);
        cell.setNo(no);
        return cell;
    }

    private static BattleResult result(String rank, String level) {
        BattleResult result = new BattleResult();
        result.setWinRank(rank);
        BattleResult.EnemyInfo info = new BattleResult.EnemyInfo();
        info.setLevel(level);
        result.setEnemyInfo(info);
        return result;
    }
}
