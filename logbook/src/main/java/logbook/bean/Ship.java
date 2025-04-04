package logbook.bean;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import jakarta.json.JsonObject;

import logbook.internal.JsonHelper;
import logbook.internal.ShipType;
import logbook.internal.Ships;
import logbook.internal.SlotItemType;
import lombok.Data;

/**
 * 艦娘
 *
 */
@Data
public class Ship implements Chara, Serializable, Cloneable {

    private static final long serialVersionUID = 1824670290941099475L;

    /** ID */
    private Integer id;

    /** 図鑑番号 */
    private Integer sortno;

    /** 艦船ID */
    private Integer shipId;

    /** Lv */
    private Integer lv;

    /** 経験値 */
    private List<Integer> exp;

    /** HP */
    private Integer nowhp;

    /** 最大HP */
    private Integer maxhp;

    /** 速力 */
    private Integer soku;

    /** 射程 */
    private Integer leng;

    /** 装備 */
    private List<Integer> slot;

    /** 機数 */
    private List<Integer> onslot;

    /** 補強増設 */
    private Integer slotEx;

    /** 改修 */
    private List<Integer> kyouka;

    /** レア度 */
    private Integer backs;

    /** 燃料 */
    private Integer fuel;

    /** 弾薬 */
    private Integer bull;

    /** スロット数 */
    private Integer slotnum;

    /** 入渠時間 */
    private Integer ndockTime;

    /** 入渠消費資材 */
    private List<Integer> ndockItem;

    /** api_srate */
    private Integer srate;

    /** コンディション */
    private Integer cond;

    /** 火力 */
    private List<Integer> karyoku;

    /** 雷装 */
    private List<Integer> raisou;

    /** 対空 */
    private List<Integer> taiku;

    /** 装甲 */
    private List<Integer> soukou;

    /** 回避 */
    private List<Integer> kaihi;

    /** 対潜 */
    private List<Integer> taisen;

    /** 索敵 */
    private List<Integer> sakuteki;

    /** 運 */
    private List<Integer> lucky;

    /** ロック */
    private Boolean locked;

    /** ロック(装備) */
    private Boolean lockedEquip;

    /** 出撃海域 */
    private Integer sallyArea = 0;

    /** 特殊効果アイテム */
    private List<SpEffectItem> spEffectItems;

    @Override
    public Ship clone() {
        try {
            return (Ship) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * この艦娘が指定された艦種であるかを返します。
     * @param shipType 艦種定数
     * @return この艦娘が指定された艦種である場合true
     */
    public boolean is(ShipType shipType) {
        return Ships.shipMst(this)
                .map(mst -> mst.is(shipType))
                .orElse(false);
    }

    /**
     * この艦娘が指定された艦種であるかを返します。
     * @param shipType1 艦種定数
     * @param shipType2 艦種定数
     * @return この艦娘が指定された艦種である場合true
     */
    public boolean is(ShipType shipType1, ShipType shipType2) {
        return Ships.shipMst(this)
                .map(mst -> mst.is(shipType1, shipType2))
                .orElse(false);
    }

    /**
     * この艦娘が指定された艦種であるかを返します。
     * @param shipTypes 艦種定数
     * @return この艦娘が指定された艦種である場合true
     */
    public boolean is(ShipType... shipTypes) {
        return Ships.shipMst(this)
                .map(mst -> mst.is(shipTypes))
                .orElse(false);
    }

    /**
     * この装備定義から{@link SlotItemType}を返します。
     * 
     * @return {@link SlotItemType}
     */
    public Optional<ShipType> asShipType() {
        return Ships.shipMst(this).map(ShipType::toShipType);
    }

    @Override
    public boolean isShip() {
        return true;
    }

    @Override
    public Ship asShip() {
        return this;
    }

    /**
     * JsonObjectから{@link Ship}を構築します
     *
     * @param json JsonObject
     * @return {@link Ship}
     */
    public static Ship toShip(JsonObject json) {
        Ship bean = new Ship();
        JsonHelper.bind(json)
                .setInteger("api_id", bean::setId)
                .setInteger("api_sortno", bean::setSortno)
                .setInteger("api_ship_id", bean::setShipId)
                .setInteger("api_lv", bean::setLv)
                .setIntegerList("api_exp", bean::setExp)
                .setInteger("api_nowhp", bean::setNowhp)
                .setInteger("api_maxhp", bean::setMaxhp)
                .setInteger("api_soku", bean::setSoku)
                .setInteger("api_leng", bean::setLeng)
                .setIntegerList("api_slot", bean::setSlot)
                .setIntegerList("api_onslot", bean::setOnslot)
                .setInteger("api_slot_ex", bean::setSlotEx)
                .setIntegerList("api_kyouka", bean::setKyouka)
                .setInteger("api_backs", bean::setBacks)
                .setInteger("api_fuel", bean::setFuel)
                .setInteger("api_bull", bean::setBull)
                .setInteger("api_slotnum", bean::setSlotnum)
                .setInteger("api_ndock_time", bean::setNdockTime)
                .setIntegerList("api_ndock_item", bean::setNdockItem)
                .setInteger("api_srate", bean::setSrate)
                .setInteger("api_cond", bean::setCond)
                .setIntegerList("api_karyoku", bean::setKaryoku)
                .setIntegerList("api_raisou", bean::setRaisou)
                .setIntegerList("api_taiku", bean::setTaiku)
                .setIntegerList("api_soukou", bean::setSoukou)
                .setIntegerList("api_kaihi", bean::setKaihi)
                .setIntegerList("api_taisen", bean::setTaisen)
                .setIntegerList("api_sakuteki", bean::setSakuteki)
                .setIntegerList("api_lucky", bean::setLucky)
                .setBoolean("api_locked", bean::setLocked)
                .setBoolean("api_locked_equip", bean::setLockedEquip)
                .set("api_sp_effect_items", bean::setSpEffectItems, JsonHelper.toList(SpEffectItem::toSpEffectItem))
                .setInteger("api_sally_area", bean::setSallyArea);
        return bean;
    }


    @Data
    public static class SpEffectItem implements Serializable {

        private static final long serialVersionUID = -1430429039139415315L;

        /** 青リボン */
        public static final int KIND_BLUE_RIBBON = 1;

        /** 白タスキ */
        public static final int KIND_WHITE_TASUKI = 2;

        /** 種別 */
        private Integer kind;

        /** 砲撃 */
        private Integer houg;

        /** 雷撃 */
        private Integer raig;

        /** 装甲 */
        private Integer souk;

        /** 回避 */
        private Integer kaih;

        public static SpEffectItem toSpEffectItem(JsonObject json) {
            SpEffectItem bean = new SpEffectItem();
            JsonHelper.bind(json)
            .setInteger("api_kind", bean::setKind)
            .setInteger("api_houg", bean::setHoug)
            .setInteger("api_raig", bean::setRaig)
            .setInteger("api_souk", bean::setSouk)
            .setInteger("api_kaih", bean::setKaih);
            return bean;
        }
    }
}
