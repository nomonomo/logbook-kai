package logbook.internal.notify;

import logbook.bean.BattleLog;
import logbook.bean.BattleResult;
import logbook.notify.GimmickAchieved;
import logbook.notify.Notifications;

/**
 * 出撃・連合の戦闘結果から出す通知です。
 * 結果まで入った戦闘ログがあるときだけ呼び出します。
 */
public final class BattleResultNotices {

    private BattleResultNotices() {
    }

    /**
     * ドロップ艦と、戦闘結果に付いたギミックと、ラストダンスを通知します。
     *
     * @param battleLog 結果まで入った戦闘ログ
     * @param host ゲームサーバの Host
     */
    public static void publish(BattleLog battleLog, String host) {
        if (battleLog == null) {
            return;
        }
        ShipDroppedReport.publish(battleLog, host);
        LastDanceReport.publish(battleLog);
        BattleResult result = battleLog.getResult();
        if (result == null) {
            return;
        }
        if (result.achievementGimmick1()) {
            Notifications.publish(new GimmickAchieved(GimmickAchieved.Kind.SEA_AREA_CHANGE));
        }
        if (result.achievementGimmick2()) {
            Notifications.publish(new GimmickAchieved(GimmickAchieved.Kind.ACHIEVED));
        }
    }
}
