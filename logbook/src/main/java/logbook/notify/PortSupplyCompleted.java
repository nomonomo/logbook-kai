package logbook.notify;

/**
 * 母港給糧が上限（COND 54）に達した艦隊。
 *
 * @param fleetId 艦隊番号
 * @param fleetName 艦隊名
 */
public record PortSupplyCompleted(int fleetId, String fleetName) implements Notification {
}
