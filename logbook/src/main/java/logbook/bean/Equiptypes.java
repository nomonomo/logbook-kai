package logbook.bean;

import java.util.List;

/**
 * 装備カテゴリ情報（equiptypes.json 用 DTO）。
 * 想定読み込み元: リソース {@code logbook/supplemental/equiptypes.json}（PluginServices 経由）。
 * JSON キーとコンポーネント名を一致させているため @JsonProperty は不要。
 */
public record Equiptypes(List<Category> categories) {

    /** 1 カテゴリ分（名前と装備タイプ ID の配列）。 */
    public record Category(String name, int[] types) {
    }
}
