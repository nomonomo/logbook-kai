package logbook.bean;

import java.util.List;

/**
 * 遠征条件 JSON のデシリアライズ用 DTO。
 * 読み込み元: logbook/mission/{mapareaId}/{dispNo}.json
 */
public record MissionConditionRecord(
        String description,
        String type,
        List<String> ship_type,
        Integer level,
        String item,
        Integer order,
        String count_type,
        List<MissionConditionRecord> conditions,
        Integer value,
        String operator,
        MissionConditionRecord greatSuccessCondition) {
}
