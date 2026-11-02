package logbook.notify;

/**
 * 母港の艦隊が疲労回復予定時刻を過ぎた。
 *
 * @param fleetId 艦隊番号。不明なら 0
 * @param fleetName 艦隊名
 */
public record CondRecovered(int fleetId, String fleetName) implements Notification {
}
