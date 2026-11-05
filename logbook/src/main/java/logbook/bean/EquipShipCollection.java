package logbook.bean;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import logbook.internal.Config;
import lombok.Data;

/**
 * 艦個別の装備可能カテゴリ（{@code api_mst_equip_ship}）
 *
 */
@Data
public class EquipShipCollection implements Serializable {

    private static final long serialVersionUID = 4182746591038472615L;

    /** 艦 ID → 艦個別の装備可能カテゴリ */
    private Map<Integer, EquipShip> equipShipMap = new LinkedHashMap<>();

    /**
     * アプリケーションのデフォルト設定ディレクトリから{@link EquipShipCollection}を取得します、
     * これは次の記述と同等です
     * <blockquote>
     *     <code>Config.getDefault().get(EquipShipCollection.class, EquipShipCollection::new)</code>
     * </blockquote>
     *
     * @return {@link EquipShipCollection}
     */
    public static EquipShipCollection get() {
        return Config.getDefault().get(EquipShipCollection.class, EquipShipCollection::new);
    }
}
