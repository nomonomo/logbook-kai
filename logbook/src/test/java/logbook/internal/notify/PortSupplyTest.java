package logbook.internal.notify;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import logbook.bean.DeckPort;
import logbook.bean.DeckPortCollection;
import logbook.bean.NdockCollection;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortSupplyTest {

    private static final long TIMER = 1_700_000_000_000L;

    private static final int GAIN_NOSAKI = PortSupply.gainOf(996);

    private static final int GAIN_NOSAKI_KAI = PortSupply.gainOf(1002);

    @AfterEach
    void resetCollections() {
        ShipCollection.get().setShipMap(new LinkedHashMap<>());
        NdockCollection.get().setNdockSet(new LinkedHashSet<>());
        DeckPortCollection.get().setDeckPortMap(new LinkedHashMap<>());
    }

    @Test
    void kaiFrom49TakesTwoSteps() {
        assertEquals(52, PortSupply.projectedCond(49, GAIN_NOSAKI_KAI, 1, TIMER, TIMER));
        assertEquals(54, PortSupply.projectedCond(49, GAIN_NOSAKI_KAI, 2, TIMER, TIMER));
        assertEquals(2, PortSupply.stepsToCap(49, GAIN_NOSAKI_KAI, TIMER, TIMER));
    }

    @Test
    void nosakiFrom49TakesThreeSteps() {
        assertEquals(51, PortSupply.projectedCond(49, GAIN_NOSAKI, 1, TIMER, TIMER));
        assertEquals(53, PortSupply.projectedCond(49, GAIN_NOSAKI, 2, TIMER, TIMER));
        assertEquals(54, PortSupply.projectedCond(49, GAIN_NOSAKI, 3, TIMER, TIMER));
        assertEquals(3, PortSupply.stepsToCap(49, GAIN_NOSAKI, TIMER, TIMER));
    }

    @Test
    void kaiFrom40MatchesFrom49BecauseNaturalFillsFirstTick() {
        assertEquals(52, PortSupply.projectedCond(40, GAIN_NOSAKI_KAI, 1, TIMER, TIMER));
        assertEquals(54, PortSupply.projectedCond(40, GAIN_NOSAKI_KAI, 2, TIMER, TIMER));
        assertEquals(2, PortSupply.stepsToCap(40, GAIN_NOSAKI_KAI, TIMER, TIMER));
    }

    @Test
    void kaiFrom19TakesThreeSteps() {
        assertEquals(37, PortSupply.projectedCond(19, GAIN_NOSAKI_KAI, 1, TIMER, TIMER));
        assertEquals(52, PortSupply.projectedCond(19, GAIN_NOSAKI_KAI, 2, TIMER, TIMER));
        assertEquals(54, PortSupply.projectedCond(19, GAIN_NOSAKI_KAI, 3, TIMER, TIMER));
        assertEquals(3, PortSupply.stepsToCap(19, GAIN_NOSAKI_KAI, TIMER, TIMER));
    }

    @Test
    void alreadyAtCapTakesZeroSteps() {
        assertEquals(0, PortSupply.stepsToCap(54, GAIN_NOSAKI_KAI, TIMER, TIMER));
        assertEquals(54, PortSupply.projectedCond(54, GAIN_NOSAKI_KAI, 1, TIMER, TIMER));
    }

    @Test
    void supplyStepsToCapMatchesCeilDiv() {
        assertEquals(0, PortSupply.supplyStepsToCap(54, GAIN_NOSAKI_KAI));
        assertEquals(2, PortSupply.supplyStepsToCap(49, GAIN_NOSAKI_KAI));
        assertEquals(3, PortSupply.supplyStepsToCap(49, GAIN_NOSAKI));
        assertEquals(1, PortSupply.supplyStepsToCap(52, GAIN_NOSAKI_KAI));
        assertEquals(2, PortSupply.supplyStepsToCap(51, GAIN_NOSAKI));
    }

    @Test
    void olderCondOriginAddsNaturalOnFirstVisit() {
        long origin = TIMER - Duration.ofMinutes(12).toMillis();
        assertEquals(48, PortSupply.projectedCond(30, GAIN_NOSAKI_KAI, 1, TIMER, TIMER));
        assertEquals(52, PortSupply.projectedCond(30, GAIN_NOSAKI_KAI, 1, origin, TIMER));
        assertEquals(3, PortSupply.stepsToCap(30, GAIN_NOSAKI_KAI, TIMER, TIMER));
        assertEquals(2, PortSupply.stepsToCap(30, GAIN_NOSAKI_KAI, origin, TIMER));
    }

    @Test
    void fleetKaiFrom49DueInThirtyMinutes() {
        var due = PortSupply.dueAtMillis(TIMER, TIMER, GAIN_NOSAKI_KAI, 49);
        assertTrue(due.isPresent());
        assertEquals(TIMER + Duration.ofMinutes(30).toMillis(), due.getAsLong());
    }

    @Test
    void fleetNosakiFrom49DueInFortyFiveMinutes() {
        var due = PortSupply.dueAtMillis(TIMER, TIMER, GAIN_NOSAKI, 49);
        assertTrue(due.isPresent());
        assertEquals(TIMER + Duration.ofMinutes(45).toMillis(), due.getAsLong());
    }

    @Test
    void allCompanionsAtCapDoNotNotify() {
        assertTrue(PortSupply.dueAtMillis(TIMER, TIMER, GAIN_NOSAKI_KAI, 54).isEmpty());
    }

    @Test
    void dueAtByFleetIsPerFleet() {
        var fleets = List.of(
                new PortSupply.FleetState(1, "第一艦隊", GAIN_NOSAKI_KAI, 49),
                new PortSupply.FleetState(2, "第二艦隊", GAIN_NOSAKI, 49));
        var due = PortSupply.dueAtByFleet(TIMER, TIMER, fleets);
        assertEquals(2, due.size());
        assertEquals(TIMER + Duration.ofMinutes(30).toMillis(), due.get(1));
        assertEquals(TIMER + Duration.ofMinutes(45).toMillis(), due.get(2));
    }

    @Test
    void isSupplyShipMatchesMasterIds() {
        assertTrue(PortSupply.isSupplyShip(996));
        assertTrue(PortSupply.isSupplyShip(1002));
        assertFalse(PortSupply.isSupplyShip(20));
        assertFalse(PortSupply.isSupplyShip(null));
    }

    @Test
    void observeCapturesGainAndMinCond() {
        installFleet(deck(1, 0, List.of(1, 2, 3)), Map.of(
                1, ship(1, 1002, 49),
                2, ship(2, 20, 40),
                3, ship(3, 21, 52)));
        var fleet = PortSupply.observe().fleets().getFirst();
        assertEquals(1, fleet.fleetId());
        assertEquals("艦隊 #1", fleet.fleetName());
        assertEquals(GAIN_NOSAKI_KAI, fleet.gain());
        assertEquals(40, fleet.minCond());
    }

    @Test
    void observeIgnoresExpeditionFleet() {
        installFleet(deck(1, 1, List.of(1, 2)), Map.of(
                1, ship(1, 1002, 49),
                2, ship(2, 20, 40)));
        assertTrue(PortSupply.observe().fleets().isEmpty());
    }

    @Test
    void observeExcludesSupplyShipFromMinCond() {
        installFleet(deck(1, 0, List.of(1)), Map.of(1, ship(1, 1002, 49)));
        assertTrue(PortSupply.observe().fleets().isEmpty());
    }

    @Test
    void deckPortChangeChangesObservationHash() {
        installFleet(deck(1, 0, List.of(1, 2)), Map.of(
                1, ship(1, 1002, 49),
                2, ship(2, 20, 40)));
        int before = PortSupply.observe().portsHash();
        installFleet(deck(1, 1, List.of(1, 2)), Map.of(
                1, ship(1, 1002, 49),
                2, ship(2, 20, 40)));
        assertNotEquals(before, PortSupply.observe().portsHash());
    }

    @Test
    void ndockChangeChangesMinCond() {
        installFleet(deck(1, 0, List.of(1, 2, 3)), Map.of(
                1, ship(1, 1002, 49),
                2, ship(2, 20, 40),
                3, ship(3, 21, 49)));
        assertEquals(40, PortSupply.observe().fleets().getFirst().minCond());
        NdockCollection.get().setNdockSet(new LinkedHashSet<>(List.of(2)));
        var after = PortSupply.observe();
        assertEquals(49, after.fleets().getFirst().minCond());
        assertEquals(TIMER + Duration.ofMinutes(30).toMillis(),
                PortSupply.dueAtByFleet(TIMER, TIMER, after.fleets()).get(1));
    }

    private static void installFleet(DeckPort port, Map<Integer, Ship> ships) {
        DeckPortCollection.get().setDeckPortMap(new LinkedHashMap<>(Map.of(port.getId(), port)));
        ShipCollection.get().setShipMap(new LinkedHashMap<>(ships));
    }

    private static DeckPort deck(int id, int missionState, List<Integer> ships) {
        DeckPort port = new DeckPort();
        port.setId(id);
        port.setName("艦隊 #" + id);
        port.setMission(List.of((long) missionState, 0L, 0L));
        port.setShip(ships);
        return port;
    }

    private static Ship ship(int id, int masterId, int cond) {
        Ship ship = new Ship();
        ship.setId(id);
        ship.setShipId(masterId);
        ship.setCond(cond);
        return ship;
    }
}
