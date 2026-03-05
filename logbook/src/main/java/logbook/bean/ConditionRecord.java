package logbook.bean;

import java.util.List;

/**
 * 任務条件 JSON の集計条件用 DTO。
 * 読み込み元: logbook/quest/{questNo}.json の conditions 要素。
 * start / boss が JSON に無い場合（null）は compact コンストラクタで false に正規化する。
 */
public record ConditionRecord(
        String description,
        List<String> area,
        String cell,
        List<String> cells,
        Boolean start,
        Boolean boss,
        List<String> rank,
        List<String> stype,
        int count,
        List<String> missions) {

    /** JSON にキーが無い場合は null が渡るため、コンストラクタで false に正規化する。 */
    public ConditionRecord {
        start = start == null ? Boolean.FALSE : start;
        boss = boss == null ? Boolean.FALSE : boss;
    }
}
