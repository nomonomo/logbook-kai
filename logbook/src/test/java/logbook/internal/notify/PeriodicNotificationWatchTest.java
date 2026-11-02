package logbook.internal.notify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import logbook.bean.AppCondition;
import logbook.bean.AppConfig;
import logbook.bean.AppConfig.NotifyKind;
import logbook.bean.DeckPort;
import logbook.bean.DeckPortCollection;
import logbook.bean.NdockCollection;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.notify.MissionReturning;
import logbook.notify.Notification;
import logbook.notify.NotificationListener;
import logbook.notify.PortSupplyCompleted;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodicNotificationWatchTest {

    private static final long TIMER = 1_700_000_000_000L;

    @AfterEach
    void reset() {
        ShipCollection.get().setShipMap(new LinkedHashMap<>());
        NdockCollection.get().setNdockSet(new LinkedHashSet<>());
        DeckPortCollection.get().setDeckPortMap(new LinkedHashMap<>());
        AppCondition.get().setNosakiTimer(0L);
        AppCondition.get().setCondUpdateTime(0L);
    }

    @Test
    void sameFingerprintDoesNotRecompute() {
        installEligibleFleet();
        AppCondition.get().setCondUpdateTime(TIMER / 1000L);
        PeriodicNotificationWatch watch = new PeriodicNotificationWatch(event -> {
        });
        assertTrue(watch.refreshPortSupplyDue(TIMER));
        Map<Integer, Long> due = Map.copyOf(watch.portSupplyDueAt());
        assertFalse(watch.refreshPortSupplyDue(TIMER));
        assertEquals(due, watch.portSupplyDueAt());
    }

    @Test
    void notifiesOnceWhenDue() {
        installEligibleFleet();
        AppCondition.get().setNosakiTimer(TIMER);
        AppCondition.get().setCondUpdateTime(TIMER / 1000L);
        PeriodicNotificationWatch watch = new PeriodicNotificationWatch(event -> {
        });
        long dueAt = TIMER + Duration.ofMinutes(30).toMillis();
        assertTrue(watch.duePortSupplies(dueAt - 1).isEmpty());
        List<PortSupplyCompleted> first = watch.duePortSupplies(dueAt);
        assertEquals(1, first.size());
        assertEquals(1, first.getFirst().fleetId());
        assertEquals("艦隊 #1", first.getFirst().fleetName());
        assertTrue(watch.duePortSupplies(dueAt + 1).isEmpty());
    }

    @Test
    void missionRemindUsesSharedIntervalPerChannel() {
        long now = TIMER;
        DeckPort port = new DeckPort();
        port.setId(2);
        port.setName("第二艦隊");
        port.setMission(List.of(1L, 0L, now + 10_000L));
        DeckPortCollection.get().setDeckPortMap(new LinkedHashMap<>(Map.of(2, port)));

        AppConfig config = AppConfig.get();
        Boolean previousToastRemind = config.getUseToastRemind();
        Boolean previousSoundRemind = config.getUseSoundRemind();
        int previousRemind = config.getRemind();
        EnumSet<NotifyKind> previousToast = config.getToastNotify();
        EnumSet<NotifyKind> previousSound = config.getSoundNotify();
        config.setUseToastRemind(true);
        config.setUseSoundRemind(false);
        config.setRemind(60);
        config.setToastNotify(EnumSet.of(NotifyKind.MISSION));
        config.setSoundNotify(EnumSet.of(NotifyKind.MISSION));
        ToastNotificationListener toast = new ToastNotificationListener();
        SoundNotificationListener sound = new SoundNotificationListener();
        try {
            List<MissionReturning> events = new ArrayList<>();
            List<List<NotificationListener>> reminded = new ArrayList<>();
            PeriodicNotificationWatch watch = new PeriodicNotificationWatch(
                    event -> collectMission(events, event),
                    (event, targets) -> {
                        collectMission(events, event);
                        reminded.add(targets);
                    },
                    () -> List.of(toast, sound));

            watch.checkMissions(now);
            assertEquals(1, events.size());
            assertFalse(events.getFirst().remind());
            assertTrue(reminded.isEmpty());

            events.clear();
            watch.checkMissions(now + 30_000L);
            assertTrue(events.isEmpty());

            watch.checkMissions(now + 60_000L);
            assertEquals(1, events.size());
            assertTrue(events.getFirst().remind());
            assertEquals(List.of(toast), reminded.getFirst());
        } finally {
            config.setUseToastRemind(previousToastRemind);
            config.setUseSoundRemind(previousSoundRemind);
            config.setRemind(previousRemind);
            config.setToastNotify(previousToast);
            config.setSoundNotify(previousSound);
        }
    }

    @Test
    void missionRemindRejectsOutOfRangeIntervals() {
        long now = TIMER;
        DeckPort port = new DeckPort();
        port.setId(1);
        port.setName("第一艦隊");
        port.setMission(List.of(1L, 0L, now + 10_000L));
        DeckPortCollection.get().setDeckPortMap(new LinkedHashMap<>(Map.of(1, port)));

        NotificationListener tooShort = new FixedRemindListener(Duration.ofSeconds(9));
        NotificationListener minimum = new FixedRemindListener(Duration.ofSeconds(10));
        NotificationListener maximum = new FixedRemindListener(Duration.ofSeconds(3600));
        NotificationListener tooLong = new FixedRemindListener(Duration.ofSeconds(3601));
        List<MissionReturning> events = new ArrayList<>();
        List<List<NotificationListener>> reminded = new ArrayList<>();
        PeriodicNotificationWatch watch = new PeriodicNotificationWatch(
                event -> collectMission(events, event),
                (event, targets) -> {
                    collectMission(events, event);
                    reminded.add(targets);
                },
                () -> List.of(tooShort, minimum, maximum, tooLong));

        watch.checkMissions(now);
        assertEquals(1, events.size());
        assertFalse(events.getFirst().remind());
        assertTrue(reminded.isEmpty());

        events.clear();
        watch.checkMissions(now + 10_000L);
        assertEquals(1, events.size());
        assertTrue(events.getFirst().remind());
        assertEquals(List.of(minimum), reminded.getFirst());

        events.clear();
        reminded.clear();
        watch.checkMissions(now + 3_601_000L);
        assertEquals(1, events.size());
        assertEquals(List.of(minimum, maximum), reminded.getFirst());
    }

    @Test
    void missingTimerClearsState() {
        installEligibleFleet();
        AppCondition.get().setNosakiTimer(TIMER);
        PeriodicNotificationWatch watch = new PeriodicNotificationWatch(event -> {
        });
        assertFalse(watch.duePortSupplies(TIMER + Duration.ofMinutes(30).toMillis()).isEmpty());
        AppCondition.get().setNosakiTimer(0L);
        assertTrue(watch.duePortSupplies(TIMER).isEmpty());
        assertTrue(watch.portSupplyDueAt().isEmpty());
    }

    private static final class FixedRemindListener implements NotificationListener {

        private final Duration interval;

        private FixedRemindListener(Duration interval) {
            this.interval = interval;
        }

        @Override
        public void onNotify(Notification event) {
        }

        @Override
        public Optional<Duration> missionRemindInterval() {
            return Optional.of(this.interval);
        }
    }

    private static void collectMission(List<MissionReturning> events, Notification event) {
        if (event instanceof MissionReturning returning) {
            events.add(returning);
        }
    }

    private static void installEligibleFleet() {
        DeckPort port = new DeckPort();
        port.setId(1);
        port.setName("艦隊 #1");
        port.setMission(List.of(0L, 0L, 0L));
        port.setShip(List.of(1, 2));
        DeckPortCollection.get().setDeckPortMap(new LinkedHashMap<>(Map.of(1, port)));
        Ship flagship = new Ship();
        flagship.setId(1);
        flagship.setShipId(1002);
        flagship.setCond(49);
        Ship companion = new Ship();
        companion.setId(2);
        companion.setShipId(20);
        companion.setCond(49);
        Map<Integer, Ship> ships = new LinkedHashMap<>();
        ships.put(1, flagship);
        ships.put(2, companion);
        ShipCollection.get().setShipMap(ships);
    }
}
