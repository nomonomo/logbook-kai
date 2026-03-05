package logbook.bean;

import java.util.List;

/**
 * 任務条件 JSON のデシリアライズ用 DTO。
 * 読み込み元: logbook/quest/{questNo}.json
 */
public record AppQuestConditionRecord(
        AppQuestCondition.Type type,
        String resetType,
        Integer yearlyResetMonth,
        FilterConditionRecord filter,
        List<ConditionRecord> conditions) {
}
