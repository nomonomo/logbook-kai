package logbook.bean;

import java.io.Serializable;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import logbook.bean.QuestList.Quest;
import logbook.internal.Logs;
import lombok.Data;

/**
 * 任務
 */
@Data
public class AppQuest implements Serializable {

    private static final long serialVersionUID = 4212109733911812553L;

    /** デイリー */
    private static final int DAILY = 1;
    /** ウィークリー */
    private static final int WEEKLY = 2;
    /** マンスリー */
    private static final int MONTHLY = 3;
    /** 単発 */
    private static final int ONECE = 4;
    /** クォータリー */
    private static final int QUARTRELY = 5;
    /** イヤリー */
    private static final int YEARLY = 100;  // 艦これ的には「その他(5)」なためかぶらないように大きな数字にする

    /** No */
    private Integer no;

    /** 任務 */
    private Quest quest;

    /** 期限 */
    private String expire;

    /** 受諾中 */
    private boolean active;

    /**
     * 任務を構築します
     *
     * @param quest 任務
     * @return {@link AppQuest}
     */
    public static AppQuest toAppQuest(Quest quest) {
        AppQuest bean = new AppQuest();
        bean.setNo(quest.getNo());
        bean.setQuest(quest);
        bean.setActive(quest.getState() == 2 || quest.getState() == 3);  // 受諾中、もしくは完了済み

        ZonedDateTime base = ZonedDateTime.now(ZoneId.of("GMT+04:00"))
                .truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime expire = null;

        Cycle cycle = resolveCycle(quest);
        int type = cycle.type();
        int yearlyResetMonth = cycle.yearlyResetMonth();

        if (type == DAILY) {
            // 1=デイリー
            // 1日加算
            expire = base.plusDays(1)
                    .withZoneSameInstant(ZoneId.of("Asia/Tokyo"));
        } else if (type == WEEKLY) {
            // 2=ウィークリー
            // 7日加算して曜日(1(月曜日)から7(日曜日))-1を減算する
            expire = base.plusWeeks(1)
                    .minusDays(base.getDayOfWeek().getValue() - 1)
                    .withZoneSameInstant(ZoneId.of("Asia/Tokyo"));
        } else if (type == MONTHLY) {
            // 3=マンスリー
            // 翌月1日にする
            expire = base
                    .withDayOfMonth(1)
                    .plusMonths(1)
                    .withZoneSameInstant(ZoneId.of("Asia/Tokyo"));
        } else if (type == ONECE) {
            // 4=単発
            // 期限なし、とりあえず9999年12月31日
            expire = base
                    // XXXX年1月1日
                    .withDayOfYear(1)
                    // 10000年1月1日
                    .withYear(10000)
                    // 9999年12月31日
                    .minusDays(1)
                    .withZoneSameInstant(ZoneId.of("Asia/Tokyo"));
        } else if (type == QUARTRELY) {
            // 5=他
            // クオータリー最終月を求める
            int addMonth = (base.getMonthValue() / 3 * 3) - base.getMonthValue() + 3;
            // クオータリー最終月の翌月1日
            expire = base.withDayOfMonth(1)
                    .plusMonths(addMonth)
                    .withZoneSameInstant(ZoneId.of("Asia/Tokyo"));
        } else if (type == YEARLY) {
            // 100 = イヤリー、艦これ上は5=他
            expire = (base.getMonthValue() >= yearlyResetMonth ? base.plusYears(1) : base)
                    .withMonth(yearlyResetMonth)
                    .withDayOfMonth(1)
                    .withZoneSameInstant(ZoneId.of("Asia/Tokyo"));
        }
        if (expire != null) {
            bean.setExpire(Logs.DATE_FORMAT.format(expire));
        }

        return bean;
    }

    /**
     * 周期を決める。{@code labelType}（既知）を正とし、無ければ条件 JSON、それも無ければ {@code api_type}。
     */
    private static Cycle resolveCycle(Quest quest) {
        Cycle fromLabel = cycleFromLabelType(quest.getLabelType());
        if (fromLabel != null) {
            return fromLabel;
        }
        Cycle fromCondition = cycleFromCondition(quest.getNo());
        if (fromCondition != null) {
            return fromCondition;
        }
        Integer apiType = quest.getType();
        return new Cycle(apiType != null ? apiType : 0, 0);
    }

    /**
     * {@code api_label_type} から周期を決める。未知・未設定は null。
     */
    private static Cycle cycleFromLabelType(Integer labelType) {
        if (labelType == null) {
            return null;
        }
        return switch (labelType) {
        case 1 -> new Cycle(ONECE, 0);
        case 2 -> new Cycle(DAILY, 0);
        case 3 -> new Cycle(WEEKLY, 0);
        case 6 -> new Cycle(MONTHLY, 0);
        case 7 -> new Cycle(QUARTRELY, 0);
        default -> {
            if (labelType >= 101 && labelType <= 112) {
                yield new Cycle(YEARLY, labelType - 100);
            }
            yield null;
        }
        };
    }

    /**
     * 条件 JSON の {@code resetType} / {@code yearlyResetMonth} から周期を決める。無ければ null。
     */
    private static Cycle cycleFromCondition(Integer questNo) {
        if (questNo == null) {
            return null;
        }
        AppQuestCondition condition = AppQuestCondition.loadFromResource(questNo);
        if (condition == null) {
            return null;
        }
        String resetType = condition.getResetType();
        if (resetType == null) {
            return null;
        }
        return switch (resetType) {
        case "デイリー" -> new Cycle(DAILY, 0);
        case "ウィークリー" -> new Cycle(WEEKLY, 0);
        case "マンスリー" -> new Cycle(MONTHLY, 0);
        case "単発" -> new Cycle(ONECE, 0);
        case "クオータリー", "クォータリー" -> new Cycle(QUARTRELY, 0);
        case "イヤリー" -> new Cycle(YEARLY,
                condition.getYearlyResetMonth() != null ? condition.getYearlyResetMonth() : 0);
        default -> null;
        };
    }

    /**
     * expire 計算用の内部周期。{@code api_type} の番号とは一致しない場合がある。
     */
    private record Cycle(int type, int yearlyResetMonth) {
    }
}
