package logbook.bean;

import java.util.List;

import jakarta.json.JsonObject;

import logbook.bean.BattleTypes.IMidnightBattle;
import logbook.internal.JsonHelper;
import lombok.Data;

/**
 * 夜戦
 * IMidnightBattleはSerializableを継承しているため、シリアライズ可能です。
 */
@Data
public class BattleMidnightBattle implements IMidnightBattle {

    private static final long serialVersionUID = 1993839270894519690L;

    /** api_dock_id/api_deck_id */
    private Integer dockId;

    /** api_ship_ke */
    private List<Integer> shipKe;

    /** api_ship_lv */
    private List<Integer> shipLv;

    /** api_f_nowhps */
    private List<Integer> fNowhps;

    /** api_f_maxhps */
    private List<Integer> fMaxhps;

    /** api_e_nowhps */
    private List<Integer> eNowhps;

    /** api_e_maxhps */
    private List<Integer> eMaxhps;

    /** api_eSlot */
    private List<List<Integer>> eSlot;

    /** api_fParam */
    private List<List<Integer>> fParam;

    /** api_eParam */
    private List<List<Integer>> eParam;

    /** api_friendly_info */
    private BattleTypes.FriendlyInfo friendlyInfo;

    /** api_friendly_battle */
    private BattleTypes.FriendlyBattle friendlyBattle;

    /** api_touch_plane */
    private List<Integer> touchPlane;

    /** api_flare_pos */
    private List<Integer> flarePos;

    /** api_smoke_type */
    private Integer smokeType;

    /** api_balloon_cell */
    private Integer balloonCell;

    /** api_atoll_cell */
    private Integer atollCell;

    /** api_hougeki */
    private BattleTypes.MidnightHougeki hougeki;

    /**
     * JsonObjectから{@link BattleMidnightBattle}を構築します
     *
     * @param json JsonObject
     * @return {@link BattleMidnightBattle}
     */
    public static BattleMidnightBattle toBattle(JsonObject json) {
        BattleMidnightBattle bean = new BattleMidnightBattle();
        JsonHelper.bind(json)
                .setInteger("api_dock_id", bean::setDockId)
                .setInteger("api_deck_id", bean::setDockId)
                .setIntegerList("api_ship_ke", bean::setShipKe)
                .setIntegerList("api_ship_lv", bean::setShipLv)
                .setIntegerList("api_f_nowhps", bean::setFNowhps)
                .setIntegerList("api_f_maxhps", bean::setFMaxhps)
                .setIntegerList("api_e_nowhps", bean::setENowhps)
                .setIntegerList("api_e_maxhps", bean::setEMaxhps)
                .set("api_eSlot", bean::setESlot, JsonHelper.toList(JsonHelper::toIntegerList))
                .set("api_fParam", bean::setFParam, JsonHelper.toList(JsonHelper::toIntegerList))
                .set("api_eParam", bean::setEParam, JsonHelper.toList(JsonHelper::toIntegerList))
                .set("api_friendly_info", bean::setFriendlyInfo, BattleTypes.FriendlyInfo::toFriendlyInfo)
                .set("api_friendly_battle", bean::setFriendlyBattle, BattleTypes.FriendlyBattle::toFriendlyBattle)
                .setIntegerList("api_touch_plane", bean::setTouchPlane)
                .setIntegerList("api_flare_pos", bean::setFlarePos)
                .setInteger("api_smoke_type", bean::setSmokeType)
                .setInteger("api_balloon_cell", bean::setBalloonCell)
                .setInteger("api_atoll_cell", bean::setAtollCell)
                .set("api_hougeki", bean::setHougeki, BattleTypes.MidnightHougeki::toMidnightHougeki);
        return bean;
    }
}
