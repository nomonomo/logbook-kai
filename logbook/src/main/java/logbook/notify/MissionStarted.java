package logbook.notify;

import java.util.List;

/**
 * 第二〜第四艦隊のどれかが遠征を開始した時点のスナップショット。
 *
 * @param fleets 第二・第三・第四艦隊の状態
 */
public record MissionStarted(List<FleetMission> fleets) implements Notification {

    /**
     * 1 艦隊の遠征状態。
     *
     * @param fleetId 艦隊番号
     * @param fleetName 艦隊名
     * @param missionName 遠征名。未遠征なら空文字
     * @param underway 遠征中なら true
     * @param returnAtMillis 帰還予定時刻（epoch milli）。未遠征や不明なら 0
     */
    public record FleetMission(
            int fleetId,
            String fleetName,
            String missionName,
            boolean underway,
            long returnAtMillis) {
    }

    public MissionStarted {
        fleets = List.copyOf(fleets);
    }
}
