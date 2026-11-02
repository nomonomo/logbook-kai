package logbook.internal.notify;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import logbook.bean.DeckPort;
import logbook.notify.MissionStarted;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionStartWatchTest {

    @Test
    void firstObservationDoesNotNotify() {
        MissionStartWatch watch = new MissionStartWatch();
        assertTrue(watch.update(ports(1, 0, 0)).isEmpty());
    }

    @Test
    void secondFleetStartNotifiesSnapshot() {
        MissionStartWatch watch = new MissionStartWatch();
        watch.update(ports(0, 0, 0));
        MissionStarted started = watch.update(ports(1, 0, 0)).orElseThrow();
        assertEquals(List.of(2, 3, 4), started.fleets().stream().map(MissionStarted.FleetMission::fleetId).toList());
        assertTrue(started.fleets().get(0).underway());
        assertEquals("第二", started.fleets().get(0).fleetName());
        assertFalse(started.fleets().get(1).underway());
        assertFalse(started.fleets().get(2).underway());
    }

    @Test
    void alreadyUnderwayDoesNotNotifyAgain() {
        MissionStartWatch watch = new MissionStartWatch();
        watch.update(ports(0, 1, 0));
        assertTrue(watch.update(ports(0, 1, 0)).isEmpty());
    }

    @Test
    void firstFleetIsIgnored() {
        MissionStartWatch watch = new MissionStartWatch();
        Map<Integer, DeckPort> baseline = ports(0, 0, 0);
        baseline.put(1, deck(1, "第一", 0));
        watch.update(baseline);
        Map<Integer, DeckPort> started = ports(0, 0, 0);
        started.put(1, deck(1, "第一", 1));
        assertTrue(watch.update(started).isEmpty());
    }

    private static Map<Integer, DeckPort> ports(int second, int third, int fourth) {
        Map<Integer, DeckPort> ports = new LinkedHashMap<>();
        ports.put(2, deck(2, "第二", second));
        ports.put(3, deck(3, "第三", third));
        ports.put(4, deck(4, "第四", fourth));
        return ports;
    }

    private static DeckPort deck(int id, String name, int state) {
        DeckPort port = new DeckPort();
        port.setId(id);
        port.setName(name);
        port.setMission(List.of((long) state, 5L, 9L));
        return port;
    }
}
