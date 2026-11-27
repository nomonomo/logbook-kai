package logbook.internal.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import logbook.bean.BattleLog;
import logbook.bean.BattleMidnightBattle;
import logbook.bean.BattleResult;
import logbook.bean.BattleTypes;
import logbook.bean.BattleTypes.IFormation;
import logbook.bean.CombinedBattleEcBattle;
import logbook.bean.MapStartNext;
import logbook.bean.MapTypes;
import logbook.bean.Ship;
import logbook.bean.SortieBattle;
import logbook.internal.notify.LastDanceReport.FlagshipDamage;
import logbook.notify.LastDance;

class LastDanceReportTest {

    @Test
    void enteredOnlyWhenCrossingIntoRange() {
        assertTrue(LastDanceReport.entered(500, 300, 300));
        assertFalse(LastDanceReport.entered(200, 50, 300));
        assertFalse(LastDanceReport.entered(250, 300, 300));
        assertFalse(LastDanceReport.entered(1000, 100, 300));
    }

    @Test
    void dayDamageIsFlagshipHpLost() {
        Optional<FlagshipDamage> hit = LastDanceReport.flagshipDamage(sortie(100));

        assertTrue(hit.isPresent());
        assertEquals(100, hit.get().damage());
        assertEquals(300, hit.get().maxHp());
    }

    @Test
    void nightDamageIsAdded() {
        BattleLog log = sortie(100);
        log.setMidnight(night(50));

        Optional<FlagshipDamage> hit = LastDanceReport.flagshipDamage(log);

        assertTrue(hit.isPresent());
        assertEquals(150, hit.get().damage());
    }

    @Test
    void overkillStopsAtStartHp() {
        Optional<FlagshipDamage> hit = LastDanceReport.flagshipDamage(sortie(999));

        assertTrue(hit.isPresent());
        assertEquals(300, hit.get().damage());
    }

    @Test
    void escortFlagshipIsIgnored() {
        Optional<FlagshipDamage> hit = LastDanceReport.flagshipDamage(escortHit(200));

        assertTrue(hit.isPresent());
        assertEquals(0, hit.get().damage());
        assertEquals(300, hit.get().maxHp());
    }

    @Test
    void publishesWhenPowerGaugeBossEntersLastDance() {
        Optional<LastDance> event = LastDanceReport.from(boss(200, 400, 5, 5, 4), 2);

        assertTrue(event.isPresent());
        LastDance dance = event.get();
        assertEquals("北方海域", dance.mapName());
        assertEquals(400, dance.gaugeBefore());
        assertEquals(200, dance.damage());
        assertEquals(200, dance.gaugeAfter());
        assertEquals(300, dance.bossMaxHp());
        assertEquals("北方海域 次の出撃でゲージを割り切れます。（残 200 / ボス最大 300）", dance.message());
        assertTrue(LastDanceReport.from(boss(200, 400, 3, 9, 5), 2).isPresent());
    }

    @Test
    void skipsTransportGaugeAndNonBoss() {
        assertTrue(LastDanceReport.from(boss(200, 400, 5, 5, 4), 3).isEmpty());
        assertTrue(LastDanceReport.from(boss(200, 400, 3, 9, 4), 2).isEmpty());
        assertTrue(LastDanceReport.from(boss(200, 200, 5, 5, 4), 2).isEmpty());
        assertTrue(LastDanceReport.from(boss(300, 250, 5, 5, 4), 2).isEmpty());
        assertTrue(LastDanceReport.from(boss(100, 1000, 5, 5, 4), 2).isEmpty());
    }

    private static BattleLog boss(double damage, int gaugeBefore, int cellNo, int bossCellNo, int eventId) {
        BattleLog log = sortie(damage);
        MapStartNext cell = new MapStartNext();
        cell.setMapareaId(62);
        cell.setMapinfoNo(1);
        cell.setNo(cellNo);
        cell.setBosscellNo(bossCellNo);
        cell.setEventId(eventId);
        MapTypes.Eventmap eventmap = new MapTypes.Eventmap();
        eventmap.setNowMaphp(gaugeBefore);
        cell.setEventmap(eventmap);
        log.setNext(List.of(cell));
        BattleResult result = new BattleResult();
        result.setQuestName("北方海域");
        log.setResult(result);
        return log;
    }

    private static BattleLog sortie(double damage) {
        SortieBattle battle = new SortieBattle();
        battle.setDockId(1);
        battle.setShipKe(List.of(1001));
        battle.setShipLv(List.of(1));
        battle.setESlot(List.of(List.of()));
        battle.setEMaxhps(List.of(300));
        battle.setENowhps(List.of(300));
        battle.setFMaxhps(List.of(40));
        battle.setFNowhps(List.of(40));
        battle.setHougeki1(dayShot(0, damage));
        return log(battle);
    }

    private static BattleLog escortHit(double damage) {
        CombinedBattleEcBattle battle = new CombinedBattleEcBattle();
        battle.setDockId(1);
        battle.setShipKe(List.of(1001));
        battle.setShipLv(List.of(1));
        battle.setESlot(List.of(List.of()));
        battle.setEMaxhps(List.of(300));
        battle.setENowhps(List.of(300));
        battle.setShipKeCombined(List.of(2001));
        battle.setShipLvCombined(List.of(1));
        battle.setESlotCombined(List.of(List.of()));
        battle.setEMaxhpsCombined(List.of(80));
        battle.setENowhpsCombined(List.of(80));
        battle.setFMaxhps(List.of(40));
        battle.setFNowhps(List.of(40));
        battle.setHougeki1(dayShot(6, damage));
        return log(battle);
    }

    private static BattleLog log(IFormation battle) {
        BattleLog log = new BattleLog();
        log.setBattle(battle);
        Map<Integer, List<Ship>> deck = new HashMap<>();
        deck.put(1, Arrays.asList((Ship) null));
        log.setDeckMap(deck);
        log.setItemMap(new HashMap<>());
        log.setEscape(Set.of());
        return log;
    }

    private static BattleMidnightBattle night(double damage) {
        BattleMidnightBattle midnight = new BattleMidnightBattle();
        BattleTypes.MidnightHougeki hougeki = new BattleTypes.MidnightHougeki();
        hougeki.setAtEflag(List.of(0));
        hougeki.setAtList(List.of(0));
        hougeki.setDfList(List.of(List.of(0)));
        hougeki.setClList(List.of(List.of(1)));
        hougeki.setDamage(List.of(List.of(damage)));
        midnight.setHougeki(hougeki);
        return midnight;
    }

    private static BattleTypes.Hougeki dayShot(int target, double damage) {
        BattleTypes.Hougeki hougeki = new BattleTypes.Hougeki();
        hougeki.setAtEflag(List.of(0));
        hougeki.setAtList(List.of(0));
        hougeki.setAtType(List.of(0));
        hougeki.setDfList(List.of(List.of(target)));
        hougeki.setClList(List.of(List.of(1)));
        hougeki.setDamage(List.of(List.of(damage)));
        return hougeki;
    }
}
