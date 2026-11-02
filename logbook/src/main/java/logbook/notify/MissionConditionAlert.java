package logbook.notify;

/**
 * 遠征開始時に編成条件を満たしていない。
 *
 * @param fleetId 艦隊番号
 * @param fleetName 艦隊名
 * @param missionName 遠征名
 */
public record MissionConditionAlert(int fleetId, String fleetName, String missionName) implements Notification {
}
