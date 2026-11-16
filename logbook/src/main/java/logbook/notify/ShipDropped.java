package logbook.notify;

import java.util.List;

/**
 * 戦闘結果からドロップ報告の材料が揃った。
 * 艦ドロップがあるはずなのに艦 ID が無い結果は、この通知にはしない。
 * ペイロードの版とサーバ番号は、ホスト名から受け側が作る。
 *
 * @param mapArea 海域番号。1-5 なら 1
 * @param mapNo マップ番号。1-5 なら 5
 * @param cell セル番号
 * @param enemyFormation 敵陣形
 * @param difficulty 海域難易度。通常は 0、丁丙乙甲は 1 から 4
 * @param dropShipId ドロップ艦のマスタ ID。ドロップ無しは -1
 * @param ownedShipCount ドロップ艦の改装込み所持数。ドロップ無しは 0
 * @param rank 勝利ランク
 * @param enemyShips 敵編成のマスタ ID
 * @param enemyCombinedShips 敵連合の第二編成。無ければ null
 * @param endedAt 結果時刻。UTC の RFC3339、秒まで
 * @param host ゲームサーバのホスト名。例は w11t.kancolle-server.com
 * @param items ドロップしたアイテム。無ければ null
 */
public record ShipDropped(
        int mapArea,
        int mapNo,
        int cell,
        int enemyFormation,
        int difficulty,
        int dropShipId,
        int ownedShipCount,
        String rank,
        List<Integer> enemyShips,
        List<Integer> enemyCombinedShips,
        String endedAt,
        String host,
        List<DroppedItem> items) implements Notification {

    /**
     * ドロップしたアイテム。
     *
     * @param itemId アイテム ID
     * @param ownedCount ドロップ時点の所持数
     * @param count ドロップ数。不明なら null で、受け側は 1 とする
     */
    public record DroppedItem(int itemId, int ownedCount, Integer count) {
    }

    public ShipDropped {
        enemyShips = List.copyOf(enemyShips);
        enemyCombinedShips = enemyCombinedShips == null ? null : List.copyOf(enemyCombinedShips);
        items = items == null ? null : List.copyOf(items);
        host = host == null ? "" : host;
    }
}
