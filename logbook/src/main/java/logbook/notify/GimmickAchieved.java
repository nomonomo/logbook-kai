package logbook.notify;

/**
 * ギミックの達成を確認した。
 *
 * @param kind 海域の変化か、達成そのものか
 */
public record GimmickAchieved(Kind kind) implements Notification {

    /**
     * ギミックの種別。
     */
    public enum Kind {
        /** ルート追加など、海域に変化があった */
        SEA_AREA_CHANGE,
        /** 装甲破砕など、ギミックを達成した */
        ACHIEVED
    }
}
