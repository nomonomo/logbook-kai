package logbook.bean;

import java.util.List;

/**
 * 任務条件 JSON のフィルター条件用 DTO。
 * 読み込み元: logbook/quest/{questNo}.json の filter 要素。
 */
public record FilterConditionRecord(
        List<String> area,
        FleetConditionRecord fleet) {
}
