package logbook.bean;

import java.util.List;

/**
 * 任務条件 JSON の艦隊条件用 DTO。
 * 読み込み元: logbook/quest/{questNo}.json の filter.fleet および再帰的 conditions。
 * difference が JSON に無い場合（null）は compact コンストラクタで false に正規化する。
 */
public record FleetConditionRecord(
        String description,
        List<String> stype,
        List<String> name,
        Boolean difference,
        Integer count,
        Integer order,
        List<FleetConditionRecord> conditions,
        String operator) {

    /** JSON にキーが無い場合は null が渡るため、コンストラクタで false に正規化する。 */
    public FleetConditionRecord {
        difference = difference == null ? Boolean.FALSE : difference;
    }
}
