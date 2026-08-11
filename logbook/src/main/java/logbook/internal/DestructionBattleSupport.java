package logbook.internal;

import java.util.List;
import java.util.Objects;

import logbook.bean.AppCondition;
import logbook.bean.MapStartNext;
import logbook.bean.MapStartNext.DestructionBattle;

/**
 * 基地空襲（api_destruction_battle）の確定判定・ルート反映・戦闘ログ追記。
 * <p>
 * {@code start}/{@code next} 受信時点では未確定。母港へ退却した場合は未発生。
 * 確定条件は次のとおり。
 * <ul>
 * <li>戦闘 API（{@code battle} 等）を受信した</li>
 * <li>次の {@code next} を受信した（直前の pending を確定）</li>
 * <li>pending の {@code api_next == 0}（行き止まり＝出撃完了）の状態で母港へ帰還した</li>
 * </ul>
 * 強制リロード後のログイン（{@code require_info}）や新規 {@code start} では pending を破棄する。
 */
public final class DestructionBattleSupport {

    /** ルート・CSV マスに使うラベル */
    public static final String CELL_LABEL = "空襲";

    private DestructionBattleSupport() {
    }

    /**
     * 出撃開始（start）時の処理。
     * 残留 pending は破棄し、今回の応答に空襲があれば pending にする。
     *
     * @param next start の応答
     */
    public static void onMapStart(MapStartNext next) {
        discardPending();
        if (next.getDestructionBattle() != null) {
            AppCondition.get().setPendingDestructionBattle(next);
        }
    }

    /**
     * 進撃（next）時の処理。
     * 直前の pending があれば確定し、今回の応答に空襲があれば pending にする。
     * 出撃中の {@code BattleLog} は変更しない。
     *
     * @param next next の応答
     */
    public static void onMapNext(MapStartNext next) {
        confirmPending();
        if (next.getDestructionBattle() != null) {
            AppCondition.get().setPendingDestructionBattle(next);
        }
    }

    /**
     * 戦闘開始時に pending の基地空襲を確定する。
     * {@code log.setRoute} の直前に呼ぶ。
     */
    public static void confirmPending() {
        AppCondition condition = AppCondition.get();
        MapStartNext pending = condition.getPendingDestructionBattle();
        if (pending == null) {
            return;
        }
        condition.setPendingDestructionBattle(null);
        applyConfirmed(pending, condition.getRoute());
    }

    /**
     * 母港帰還時。行き止まり（api_next == 0）なら確定、それ以外（退却）は破棄。
     * ルート削除より前に呼ぶ。
     */
    public static void onPort() {
        AppCondition condition = AppCondition.get();
        MapStartNext pending = condition.getPendingDestructionBattle();
        if (pending == null) {
            return;
        }
        condition.setPendingDestructionBattle(null);
        if (isSortieComplete(pending)) {
            applyConfirmed(pending, condition.getRoute());
        }
    }

    /**
     * ログインやり直し・強制リロード等で出撃継続が不明なとき pending を破棄する。
     */
    public static void discardPending() {
        AppCondition.get().setPendingDestructionBattle(null);
    }

    /**
     * api_next == 0（行き止まり）かどうか。
     *
     * @param next MapStartNext
     * @return 出撃完了（これ以上進撃できない）なら true
     */
    public static boolean isSortieComplete(MapStartNext next) {
        return next != null && Objects.equals(next.getNext(), 0);
    }

    /**
     * api_lost_kind の文言を返す
     *
     * @param lostKind api_lost_kind
     * @return 被害状況の短い文言
     */
    public static String lostKindText(Integer lostKind) {
        if (lostKind == null) {
            return "";
        }
        switch (lostKind) {
        case 1:
            return "備蓄資源に損害";
        case 2:
            return "資源・基地航空隊に損害";
        case 3:
            return "基地航空隊に損害";
        case 4:
            return "損害なし";
        default:
            return "lost_kind=" + lostKind;
        }
    }

    private static void applyConfirmed(MapStartNext next, List<String> route) {
        DestructionBattle destruction = next.getDestructionBattle();
        if (destruction == null) {
            return;
        }
        insertAirRaidIntoRoute(route);
        BattleEventLogs.writeAirRaid(next);
    }

    /**
     * 到達セルの直前に「空襲」を挿入する（セルは既に route 末尾にある想定）。
     */
    private static void insertAirRaidIntoRoute(List<String> route) {
        if (route == null) {
            return;
        }
        if (route.isEmpty()) {
            route.add(CELL_LABEL);
            return;
        }
        // 既に末尾手前が空襲なら二重挿入しない
        int last = route.size() - 1;
        if (last > 0 && CELL_LABEL.equals(route.get(last - 1))) {
            return;
        }
        if (CELL_LABEL.equals(route.get(last))) {
            return;
        }
        route.add(last, CELL_LABEL);
    }
}
