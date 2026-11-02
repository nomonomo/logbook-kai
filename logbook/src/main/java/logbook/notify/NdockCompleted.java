package logbook.notify;

/**
 * 入渠が残り1分以内になった。
 *
 * @param dockId ドック番号
 * @param shipId 艦ID。不明なら 0
 * @param shipName 艦名。不明なら空文字
 * @param yomi 読み。不明なら空文字
 * @param level レベル。不明なら 0
 */
public record NdockCompleted(int dockId, int shipId, String shipName, String yomi, int level)
        implements Notification {
}
