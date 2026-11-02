package logbook.notify;

/**
 * 遠征が残り1分以内になった。
 *
 * @param fleetId 艦隊番号
 * @param fleetName 艦隊名
 * @param missionName 遠征名。不明なら空文字
 * @param remind 再通知なら true。初回は false
 */
public record MissionReturning(int fleetId, String fleetName, String missionName, boolean remind)
        implements Notification {
}
