package logbook.internal.notify;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logbook.bean.BattleLog;
import logbook.bean.BattleResult;
import logbook.bean.BattleTypes.ICombinedEcBattle;
import logbook.bean.BattleTypes.IFormation;
import logbook.bean.MapStartNext;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.bean.ShipMst;
import logbook.bean.ShipMstCollection;
import logbook.bean.Useitem;
import logbook.bean.UseitemCollection;
import logbook.notify.Notifications;
import logbook.notify.ShipDropped;
import logbook.notify.ShipDropped.DroppedItem;
import lombok.extern.slf4j.Slf4j;

/**
 * 戦闘結果からドロップ艦通知を作ります。
 * 足りない結果と、艦ドロップがあるはずなのに艦 ID が無い結果は出しません。
 */
@Slf4j
public final class ShipDroppedReport {

    private static final DateTimeFormatter DROPPED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    private static final Set<Integer> FORMATIONS = Set.of(1, 2, 3, 4, 5, 6, 11, 12, 13, 14);

    private static final Set<String> RANKS = Set.of("S", "A", "B", "C", "D", "E");

    /** api_get_eventitem のうち、使用アイテム */
    private static final int USEITEM_TYPE = 1;

    private ShipDroppedReport() {
    }

    /**
     * 所持数の材料です。
     *
     * @param ownedByShipId 艦マスタ ID ごとの所持数
     * @param aftershipId 艦マスタ ID から改装後 ID。改装先が無い艦は入れない
     * @param useitemCount アイテム ID ごとの所持数
     */
    public record Sources(
            Map<Integer, Integer> ownedByShipId,
            Map<Integer, Integer> aftershipId,
            Map<Integer, Integer> useitemCount) {
    }

    /**
     * 戦闘結果から通知を作ります。
     *
     * @param battleLog 結果まで入った戦闘ログ
     * @param sources 所持数
     * @param at 結果時刻。null なら現在
     * @param host ゲームサーバの Host。ポート付きでもよい
     * @return 材料が揃ったときだけ通知
     */
    public static Optional<ShipDropped> from(BattleLog battleLog, Sources sources, Instant at, String host) {
        return Optional.ofNullable(build(battleLog, sources, at, host).event());
    }

    /**
     * 結果の揃った戦闘を通知します。出せないときは専用ログへ理由を書きます。
     *
     * @param battleLog 結果まで入った戦闘ログ
     * @param host ゲームサーバの Host
     */
    public static void publish(BattleLog battleLog, String host) {
        Built built = build(battleLog, currentSources(), Instant.now(), host);
        if (built.event() == null) {
            log.debug("ドロップ艦通知を出しません: {}", built.reason());
            return;
        }
        Notifications.publish(built.event());
    }

    private static Built build(BattleLog battleLog, Sources sources, Instant at, String host) {
        if (battleLog == null) {
            return skip("戦闘ログがありません");
        }
        if (battleLog.isPractice()) {
            return skip("演習です");
        }
        BattleResult result = battleLog.getResult();
        if (result == null) {
            return skip("戦闘結果がありません");
        }
        IFormation battle = battleLog.getBattle();
        if (battle == null) {
            return skip("昼戦がありません");
        }
        List<MapStartNext> route = battleLog.getNext();
        if (route == null || route.isEmpty() || route.get(route.size() - 1) == null) {
            return skip("海域がありません");
        }
        MapStartNext cell = route.get(route.size() - 1);
        if (cell.getMapareaId() == null || cell.getMapinfoNo() == null || cell.getNo() == null) {
            return skip("海域がありません");
        }
        int mapArea = cell.getMapareaId();
        int mapNo = cell.getMapinfoNo();
        if (mapArea < 1 || mapNo < 1) {
            return skip("マップ番号が不正です");
        }
        int cellNo = cell.getNo();
        if (cellNo < 1) {
            return skip("セル番号が不正です");
        }
        List<Integer> formation = battle.getFormation();
        if (formation == null || formation.size() < 2 || formation.get(1) == null
                || !FORMATIONS.contains(formation.get(1))) {
            return skip("敵陣形が不正です");
        }
        int enemyFormation = formation.get(1);
        Integer difficulty = mapLevel(result);
        if (difficulty == null) {
            return skip("難易度が不正です");
        }
        String rank = result.getWinRank();
        if (rank == null || !RANKS.contains(rank)) {
            return skip("ランクが不正です");
        }
        List<Integer> enemyShips = positiveIds(battle.getShipKe());
        if (enemyShips.isEmpty()) {
            return skip("敵編成がありません");
        }
        Integer dropped = droppedShipId(result);
        int dropShipId;
        int ownedShipCount;
        if (dropped != null) {
            dropShipId = dropped;
            ownedShipCount = ownedInFamily(dropped, sources);
        } else if (shipDropExpected(result)) {
            return skip("ドロップ艦がありません");
        } else {
            dropShipId = -1;
            ownedShipCount = 0;
        }
        List<DroppedItem> items = items(result, sources);
        return new Built(new ShipDropped(
                mapArea,
                mapNo,
                cellNo,
                enemyFormation,
                difficulty,
                dropShipId,
                ownedShipCount,
                rank,
                enemyShips,
                enemySecond(battle),
                DROPPED_AT.format((at == null ? Instant.now() : at).truncatedTo(ChronoUnit.SECONDS)),
                hostname(host),
                items), null);
    }

    /**
     * Host からホスト名だけを残します。ポート番号は除きます。
     *
     * @param host Host ヘッダ
     * @return ホスト名。無ければ空文字
     */
    static String hostname(String host) {
        if (host == null) {
            return "";
        }
        String value = host.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            return end > 1 ? value.substring(1, end) : value;
        }
        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.substring(colon + 1).chars().allMatch(Character::isDigit)) {
            return value.substring(0, colon);
        }
        return value;
    }

    private static Integer mapLevel(BattleResult result) {
        BattleResult.EnemyInfo info = result.getEnemyInfo();
        String level = info == null || info.getLevel() == null ? "" : info.getLevel().trim();
        if (level.isEmpty()) {
            return 0;
        }
        return switch (level) {
            case "丁" -> 1;
            case "丙" -> 2;
            case "乙" -> 3;
            case "甲" -> 4;
            default -> null;
        };
    }

    private static Integer droppedShipId(BattleResult result) {
        if (result.getGetShip() == null || result.getGetShip().getShipId() == null) {
            return null;
        }
        int shipId = result.getGetShip().getShipId();
        return shipId >= 1 ? shipId : null;
    }

    /**
     * 艦ドロップがあるはずかを返します。
     * api_get_flag の 0 番目はアイテム、1 番目は艦です。
     *
     * @param result 戦闘結果
     * @return 艦フラグが立っているとき true
     */
    private static boolean shipDropExpected(BattleResult result) {
        List<Integer> flag = result.getGetFlag();
        return flag != null && flag.size() > 1 && Integer.valueOf(1).equals(flag.get(1));
    }

    private static List<Integer> enemySecond(IFormation battle) {
        if (!battle.isICombinedEcBattle()) {
            return null;
        }
        ICombinedEcBattle combined = battle.asICombinedEcBattle();
        if (combined == null) {
            return null;
        }
        List<Integer> second = positiveIds(combined.getShipKeCombined());
        return second.isEmpty() ? null : second;
    }

    private static List<Integer> positiveIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Integer> positive = new ArrayList<>();
        for (Integer id : ids) {
            if (id != null && id > 0) {
                positive.add(id);
            }
        }
        return positive;
    }

    private static List<DroppedItem> items(BattleResult result, Sources sources) {
        List<DroppedItem> items = new ArrayList<>();
        BattleResult.Useitem useitem = result.getGetUseitem();
        if (useitem != null && useitem.getUseitemId() != null && useitem.getUseitemId() >= 1) {
            items.add(new DroppedItem(useitem.getUseitemId(), itemCount(useitem.getUseitemId(), sources), null));
        }
        List<BattleResult.GetEventitem> events = result.getGetEventitem();
        if (events != null) {
            for (BattleResult.GetEventitem event : events) {
                if (event == null || event.getType() == null || event.getType() != USEITEM_TYPE) {
                    continue;
                }
                if (event.getId() == null || event.getId() < 1) {
                    continue;
                }
                Integer count = event.getValue();
                if (count != null && count < 1) {
                    continue;
                }
                items.add(new DroppedItem(event.getId(), itemCount(event.getId(), sources), count));
            }
        }
        return items.isEmpty() ? null : items;
    }

    private static int itemCount(int itemId, Sources sources) {
        if (sources == null || sources.useitemCount() == null) {
            return 0;
        }
        return Math.max(0, sources.useitemCount().getOrDefault(itemId, 0));
    }

    private static int ownedInFamily(int shipId, Sources sources) {
        Map<Integer, Integer> after = sources == null || sources.aftershipId() == null
                ? Map.of()
                : sources.aftershipId();
        Map<Integer, Integer> owned = sources == null || sources.ownedByShipId() == null
                ? Map.of()
                : sources.ownedByShipId();
        Map<Integer, List<Integer>> reverse = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : after.entrySet()) {
            Integer from = entry.getKey();
            Integer to = entry.getValue();
            if (from == null || from <= 0 || to == null || to <= 0) {
                continue;
            }
            reverse.computeIfAbsent(to, key -> new ArrayList<>()).add(from);
        }
        Set<Integer> family = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(shipId);
        while (!queue.isEmpty()) {
            int id = queue.removeFirst();
            if (id <= 0 || !family.add(id)) {
                continue;
            }
            Integer next = after.get(id);
            if (next != null && next > 0) {
                queue.add(next);
            }
            List<Integer> previous = reverse.get(id);
            if (previous != null) {
                queue.addAll(previous);
            }
        }
        int count = 0;
        for (Integer id : family) {
            count += Math.max(0, owned.getOrDefault(id, 0));
        }
        return count;
    }

    private static Sources currentSources() {
        Map<Integer, Integer> owned = new HashMap<>();
        Map<Integer, Ship> ships = ShipCollection.get().getShipMap();
        if (ships != null) {
            for (Ship ship : ships.values()) {
                if (ship == null || ship.getShipId() == null || ship.getShipId() <= 0) {
                    continue;
                }
                owned.merge(ship.getShipId(), 1, Integer::sum);
            }
        }
        Map<Integer, Integer> after = new HashMap<>();
        Map<Integer, ShipMst> masters = ShipMstCollection.get().getShipMap();
        if (masters != null) {
            for (ShipMst master : masters.values()) {
                if (master == null || master.getId() == null || master.getAftershipid() == null
                        || master.getAftershipid() <= 0) {
                    continue;
                }
                after.put(master.getId(), master.getAftershipid());
            }
        }
        Map<Integer, Integer> items = new HashMap<>();
        Map<Integer, Useitem> useitems = UseitemCollection.get().getUseitemMap();
        if (useitems != null) {
            for (Useitem item : useitems.values()) {
                if (item == null || item.getId() == null || item.getId() < 1) {
                    continue;
                }
                items.put(item.getId(), item.getCount() == null ? 0 : Math.max(0, item.getCount()));
            }
        }
        return new Sources(owned, after, items);
    }

    private static Built skip(String reason) {
        return new Built(null, reason);
    }

    private record Built(ShipDropped event, String reason) {
    }
}
