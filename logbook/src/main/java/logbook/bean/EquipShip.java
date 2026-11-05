package logbook.bean;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import logbook.internal.JsonHelper;
import lombok.Data;

/**
 * api_mst_equip_ship の1艦
 * <p>
 * {@code api_equip_type} にキーがあれば、そのカテゴリを装備できる。
 * 値は null を想定し、判定には使わない。
 *
 */
@Data
public class EquipShip implements Serializable {

    private static final long serialVersionUID = 5827364109283746510L;

    /** api_equip_type */
    private Map<Integer, List<Integer>> equipType = new LinkedHashMap<>();

    /**
     * JsonObjectから{@link EquipShip}を構築します
     *
     * @param json 艦1件
     * @return {@link EquipShip}
     */
    public static EquipShip toEquipShip(JsonObject json) {
        Function<JsonObject, Map<Integer, List<Integer>>> equipTypeFunc = type -> JsonHelper
                .toMap(type, Integer::valueOf, EquipShip::toEquipTypeItems);

        EquipShip bean = new EquipShip();
        JsonHelper.bind(json)
                .at("api_data.api_mst_equip_ship{}")
                .set("api_equip_type", bean::setEquipType, equipTypeFunc)
                .reportUnknown();
        return bean;
    }

    /**
     * 装備カテゴリの値を保存用のリストへ変換します。
     * 装備可否はキーの有無で決めるため、null はそのまま残します。
     *
     * @param value api_equip_type の値
     * @return 配列なら装備 ID。null または配列以外は null
     */
    private static List<Integer> toEquipTypeItems(JsonValue value) {
        if (value == null || value == JsonValue.NULL) {
            return null;
        }
        if (value.getValueType() == JsonValue.ValueType.ARRAY) {
            return JsonHelper.toIntegerList((JsonArray) value);
        }
        return null;
    }
}
