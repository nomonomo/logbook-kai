package logbook.internal.notify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import logbook.bean.DeckPort;
import logbook.bean.Mission;
import logbook.bean.MissionCollection;
import logbook.notify.MissionStarted;
import logbook.notify.MissionStarted.FleetMission;
import logbook.notify.Notifications;

/**
 * 第二〜第四艦隊の遠征開始を検知する。最初の観測は基準の記録だけで、既に遠征中の艦隊では出さない。
 */
public final class MissionStartWatch {

    /** 通知対象の艦隊番号 */
    static final List<Integer> TARGET_FLEET_IDS = List.of(2, 3, 4);

    private static final MissionStartWatch INSTANCE = new MissionStartWatch();

    private final Map<Integer, Integer> lastMissionState = new ConcurrentHashMap<>();

    private boolean initialized;

    /**
     * アプリケーションで共有する検知器を返します。
     *
     * @return 検知器
     */
    public static MissionStartWatch get() {
        return INSTANCE;
    }

    public MissionStartWatch() {
    }

    /**
     * 更新後の艦隊を取り込み、開始があれば通知する。
     *
     * @param ports 更新後の艦隊
     */
    public void accept(Map<Integer, DeckPort> ports) {
        update(ports).ifPresent(Notifications::publish);
    }

    /**
     * 更新後の艦隊から開始を検知する。未初期化なら基準状態の記録のみ行う。
     *
     * @param ports 更新後の艦隊
     * @return 開始していればスナップショット
     */
    public synchronized Optional<MissionStarted> update(Map<Integer, DeckPort> ports) {
        if (!this.initialized) {
            recordBaseline(ports);
            return Optional.empty();
        }
        boolean started = false;
        for (int fleetId : TARGET_FLEET_IDS) {
            DeckPort port = ports == null ? null : ports.get(fleetId);
            int state = PortSupply.missionState(port);
            int previous = this.lastMissionState.getOrDefault(fleetId, 0);
            this.lastMissionState.put(fleetId, state);
            if (previous != 1 && state == 1) {
                started = true;
            }
        }
        if (!started) {
            return Optional.empty();
        }
        return Optional.of(snapshot(ports));
    }

    private void recordBaseline(Map<Integer, DeckPort> ports) {
        for (int fleetId : TARGET_FLEET_IDS) {
            DeckPort port = ports == null ? null : ports.get(fleetId);
            this.lastMissionState.put(fleetId, PortSupply.missionState(port));
        }
        this.initialized = true;
    }

    private static MissionStarted snapshot(Map<Integer, DeckPort> ports) {
        List<FleetMission> fleets = new ArrayList<>();
        for (int fleetId : TARGET_FLEET_IDS) {
            fleets.add(toFleet(ports == null ? null : ports.get(fleetId), fleetId));
        }
        return new MissionStarted(fleets);
    }

    private static FleetMission toFleet(DeckPort port, int fleetId) {
        String fleetName = PortSupply.fleetName(port, fleetId);
        if (port == null || PortSupply.missionState(port) != 1) {
            return new FleetMission(fleetId, fleetName, "", false, 0L);
        }
        int missionId = port.getMission().size() > 1 && port.getMission().get(1) != null
                ? port.getMission().get(1).intValue()
                : 0;
        long returnAt = port.getMission().size() > 2 && port.getMission().get(2) != null
                ? port.getMission().get(2)
                : 0L;
        return new FleetMission(fleetId, fleetName, missionName(missionId), true, returnAt);
    }

    private static String missionName(int missionId) {
        try {
            Mission mission = MissionCollection.get().getMissionMap().get(missionId);
            if (mission != null && mission.getName() != null && !mission.getName().isEmpty()) {
                return mission.getName();
            }
        } catch (RuntimeException ignored) {
            // マスタ未読込
        }
        return "遠征 #" + missionId;
    }
}
