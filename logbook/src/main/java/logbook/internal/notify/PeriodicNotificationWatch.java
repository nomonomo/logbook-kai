package logbook.internal.notify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import logbook.bean.AppCondition;
import logbook.bean.DeckPort;
import logbook.bean.DeckPortCollection;
import logbook.bean.Mission;
import logbook.bean.MissionCollection;
import logbook.bean.Ndock;
import logbook.bean.NdockCollection;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.bean.ShipMst;
import logbook.internal.Ships;
import logbook.notify.MissionReturning;
import logbook.notify.NdockCompleted;
import logbook.notify.Notification;
import logbook.notify.NotificationListener;
import logbook.notify.Notifications;
import logbook.notify.PortSupplyCompleted;
import lombok.extern.slf4j.Slf4j;

/**
 * 1 秒ごとの遠征・入渠・母港給糧の通知を発行する。
 */
@Slf4j
public final class PeriodicNotificationWatch {

    private final Consumer<Notification> publisher;

    /** 遠征の再通知を、秒数を返したリスナーだけに渡す */
    private final BiConsumer<Notification, List<NotificationListener>> remindPublisher;

    private final Supplier<List<NotificationListener>> listeners;

    /** 艦隊番号から、遠征リマインドの初回時刻とリスナーごとの前回時刻 */
    private final Map<Integer, MissionClock> missionClocks = new HashMap<>();

    /** 範囲外の間隔を、同じリスナーの同じ値では一度だけ警告する */
    private final Map<NotificationListener, Duration> rejectedRemind = new HashMap<>();

    private final Map<Integer, Long> ndockNotifiedAt = new HashMap<>();

    private final Map<Integer, Long> portSupplyNotifiedAt = new HashMap<>();

    private long portSupplyTimerSeen;

    private int portSupplyPortsHash;

    private int portSupplyShipsHash;

    private int portSupplyNdocksHash;

    private boolean portSupplyFingerprintValid;

    /** 艦隊番号から到達予定時刻 */
    private Map<Integer, Long> portSupplyDueAt = Map.of();

    /** 艦隊番号から表示名 */
    private Map<Integer, String> portSupplyFleetNames = Map.of();

    public PeriodicNotificationWatch() {
        this(Notifications::publish, Notifications::publishTo, Notifications::registeredListeners);
    }

    PeriodicNotificationWatch(Consumer<Notification> publisher) {
        this(publisher, Notifications::publishTo, Notifications::registeredListeners);
    }

    PeriodicNotificationWatch(Consumer<Notification> publisher,
            BiConsumer<Notification, List<NotificationListener>> remindPublisher,
            Supplier<List<NotificationListener>> listeners) {
        this.publisher = publisher;
        this.remindPublisher = remindPublisher;
        this.listeners = listeners;
    }

    /**
     * 現在時刻で遠征・入渠・母港給糧を確認し、期限を過ぎていれば通知する。
     */
    public void tick() {
        try {
            long now = System.currentTimeMillis();
            checkMissions(now);
            checkNdocks(now);
            for (PortSupplyCompleted event : duePortSupplies(now)) {
                this.publisher.accept(event);
            }
        } catch (Exception e) {
            log.warn("通知の監視に失敗しました", e);
        }
    }

    /**
     * 母港給糧の到達を確認する。期限を過ぎた艦隊は 1 回だけ返す。
     *
     * @param nowMillis 現在時刻（epoch milli）
     * @return 今回通知する艦隊
     */
    List<PortSupplyCompleted> duePortSupplies(long nowMillis) {
        long timer = AppCondition.get().getNosakiTimer();
        if (timer < 1) {
            clearPortSupplyState();
            return List.of();
        }
        refreshPortSupplyDue(timer);
        if (this.portSupplyDueAt.isEmpty()) {
            return List.of();
        }
        List<PortSupplyCompleted> due = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : this.portSupplyDueAt.entrySet()) {
            int fleetId = entry.getKey();
            if (this.portSupplyNotifiedAt.getOrDefault(fleetId, 0L) != 0L || nowMillis < entry.getValue()) {
                continue;
            }
            this.portSupplyNotifiedAt.put(fleetId, nowMillis);
            String fleetName = this.portSupplyFleetNames.getOrDefault(fleetId, "艦隊 #" + fleetId);
            due.add(new PortSupplyCompleted(fleetId, fleetName));
        }
        return List.copyOf(due);
    }

    void checkMissions(long now) {
        List<NotificationListener> registered = this.listeners.get();
        if (registered == null) {
            registered = List.of();
        }
        for (DeckPort port : DeckPortCollection.get().getDeckPortMap().values()) {
            if (port == null || port.getId() == null) {
                continue;
            }
            int fleetId = port.getId();
            List<Long> mission = port.getMission();
            if (mission == null || mission.size() < 3 || mission.get(0) == null || mission.get(2) == null) {
                continue;
            }
            int state = mission.get(0).intValue();
            if (state == 0) {
                this.missionClocks.remove(fleetId);
                continue;
            }
            Duration remaining = Duration.ofMillis(mission.get(2) - now);
            if (remaining.compareTo(CompletionDue.THRESHOLD) > 0) {
                continue;
            }
            MissionClock clock = this.missionClocks.computeIfAbsent(fleetId, id -> new MissionClock());
            if (clock.firstAt == 0L) {
                clock.firstAt = now;
                for (NotificationListener listener : registered) {
                    if (this.remindInterval(listener).isPresent()) {
                        clock.lastAt.put(listener, now);
                    }
                }
                this.publisher.accept(new MissionReturning(fleetId, port.getName(), missionName(mission), false));
                continue;
            }
            List<NotificationListener> due = new ArrayList<>();
            for (NotificationListener listener : registered) {
                var interval = this.remindInterval(listener);
                if (interval.isEmpty()) {
                    continue;
                }
                long last = clock.lastAt.getOrDefault(listener, clock.firstAt);
                if (now - last >= interval.get().toMillis()) {
                    clock.lastAt.put(listener, now);
                    due.add(listener);
                }
            }
            if (!due.isEmpty()) {
                this.remindPublisher.accept(
                        new MissionReturning(fleetId, port.getName(), missionName(mission), true),
                        List.copyOf(due));
            }
        }
    }

    private Optional<Duration> remindInterval(NotificationListener listener) {
        Optional<Duration> interval = listener.missionRemindInterval();
        if (interval == null || interval.isEmpty()) {
            return Optional.empty();
        }
        Duration duration = interval.get();
        Duration min = Duration.ofSeconds(NotificationListener.MISSION_REMIND_MIN_SECONDS);
        Duration max = Duration.ofSeconds(NotificationListener.MISSION_REMIND_MAX_SECONDS);
        if (duration.compareTo(min) < 0 || duration.compareTo(max) > 0) {
            if (!duration.equals(this.rejectedRemind.get(listener))) {
                this.rejectedRemind.put(listener, duration);
                log.warn("遠征リマインドの間隔が範囲外のため登録しません: {} {}秒",
                        listener.getClass().getName(), duration.toSeconds());
            }
            return Optional.empty();
        }
        this.rejectedRemind.remove(listener);
        return interval;
    }

    private void checkNdocks(long now) {
        for (Ndock ndock : NdockCollection.get().getNdockMap().values()) {
            if (ndock == null || ndock.getId() == null) {
                continue;
            }
            int dockId = ndock.getId();
            Long completeTime = ndock.getCompleteTime();
            if (completeTime == null || completeTime < 1) {
                this.ndockNotifiedAt.put(dockId, 0L);
                continue;
            }
            Duration remaining = Duration.ofMillis(completeTime - now);
            long last = this.ndockNotifiedAt.getOrDefault(dockId, 0L);
            if (!CompletionDue.due(remaining, last, now, false, CompletionDue.THRESHOLD)) {
                continue;
            }
            this.ndockNotifiedAt.put(dockId, now);
            this.publisher.accept(toNdock(ndock));
        }
    }

    /**
     * 指紋またはタイマーが変わったときだけ到達時刻を再計算する。
     *
     * @param timer 野埼タイマー
     * @return 再計算したなら true
     */
    boolean refreshPortSupplyDue(long timer) {
        boolean timerChanged = timer != this.portSupplyTimerSeen;
        if (timerChanged) {
            this.portSupplyTimerSeen = timer;
            this.portSupplyNotifiedAt.clear();
        }
        PortSupply.Observation observation = PortSupply.observe();
        boolean fingerprintChanged = !this.portSupplyFingerprintValid
                || observation.portsHash() != this.portSupplyPortsHash
                || observation.shipsHash() != this.portSupplyShipsHash
                || observation.ndocksHash() != this.portSupplyNdocksHash;
        if (!timerChanged && !fingerprintChanged) {
            return false;
        }
        this.portSupplyPortsHash = observation.portsHash();
        this.portSupplyShipsHash = observation.shipsHash();
        this.portSupplyNdocksHash = observation.ndocksHash();
        this.portSupplyFingerprintValid = true;
        Map<Integer, Long> dueAt = PortSupply.dueAtByFleet(
                timer,
                AppCondition.get().getCondUpdateTime() * 1000L,
                observation.fleets());
        Map<Integer, String> names = new HashMap<>();
        for (PortSupply.FleetState fleet : observation.fleets()) {
            names.put(fleet.fleetId(), fleet.fleetName());
        }
        this.portSupplyDueAt = Map.copyOf(dueAt);
        this.portSupplyFleetNames = Map.copyOf(names);
        this.portSupplyNotifiedAt.keySet().retainAll(dueAt.keySet());
        return true;
    }

    Map<Integer, Long> portSupplyDueAt() {
        return this.portSupplyDueAt;
    }

    private void clearPortSupplyState() {
        this.portSupplyNotifiedAt.clear();
        this.portSupplyTimerSeen = 0L;
        this.portSupplyFingerprintValid = false;
        this.portSupplyDueAt = Map.of();
        this.portSupplyFleetNames = Map.of();
    }

    private static String missionName(List<Long> mission) {
        if (mission.size() < 2 || mission.get(1) == null) {
            return "";
        }
        Mission found = MissionCollection.get().getMissionMap().get(mission.get(1).intValue());
        if (found == null || found.getName() == null) {
            return "";
        }
        return found.getName();
    }

    private static NdockCompleted toNdock(Ndock ndock) {
        int shipId = ndock.getShipId() == null ? 0 : ndock.getShipId();
        Ship ship = ShipCollection.get().getShipMap().get(shipId);
        if (ship == null) {
            return new NdockCompleted(ndock.getId(), shipId, "", "", 0);
        }
        var mst = Ships.shipMst(ship);
        int level = ship.getLv() == null ? 0 : ship.getLv();
        return new NdockCompleted(
                ndock.getId(),
                shipId,
                mst.map(ShipMst::getName).orElse(""),
                mst.map(ShipMst::getYomi).orElse(""),
                level);
    }

    private static final class MissionClock {

        private long firstAt;

        private final Map<NotificationListener, Long> lastAt = new HashMap<>();
    }
}
