package logbook.bean;

/**
 * 艦娘付加情報（対潜・回避・索敵の最小値など）。
 * 想定読み込み元: リソース {@code logbook/supplemental/ships.json}（PluginServices 経由）。
 * JSON キーとコンポーネント名を一致させているため @JsonProperty は不要。
 */
public record ShipSupplementalInfo(int id, String name, int min_taisen, int min_kaihi, int min_sakuteki) {
}
