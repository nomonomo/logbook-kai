package logbook.bean;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonObject;

import com.fasterxml.jackson.annotation.JsonIgnore;

import logbook.internal.JsonHelper;
import lombok.Data;

/**
 * 出撃/進撃
 *
 */
@Data
public class MapStartNext implements Serializable {

    private static final long serialVersionUID = -4272803839336790705L;

    /** api_rashin_flg */
    private Integer rashinFlg;

    /** api_rashin_id */
    private Integer rashinId;

    /** api_maparea_id */
    private Integer mapareaId;

    /** api_mapinfo_no */
    private Integer mapinfoNo;

    /** api_no */
    private Integer no;

    /** api_color_no */
    private Integer colorNo;

    /** api_event_id */
    private Integer eventId;

    /** api_event_kind */
    private Integer eventKind;

    /** api_next */
    private Integer next;

    /** api_bosscell_no */
    private Integer bosscellNo;

    /** api_bosscomp */
    private Integer bosscomp;

    /** api_eventmap */
    private MapTypes.Eventmap eventmap;

    /** api_comment_kind */
    private Integer commentKind;

    /** api_production_kind */
    private Integer productionKind;

    /** api_enemy */
    private MapTypes.Enemy enemy;

    /** api_happening */
    private MapTypes.Happening happening;

    /** api_itemget */
    private List<MapTypes.Itemget> itemget;

    /** api_select_route */
    private MapTypes.SelectRoute selectRoute;

    /** api_from_no */
    private Integer fromNo;

    /** api_destruction_battle */
    private DestructionBattle destructionBattle;

    /** api_m1 */
    private Integer m1;

    /** api_m2 */
    private Integer m2;

    /**
     * ギミック1が達成されたかを返します
     * @return
     */
    @JsonIgnore
    public boolean achievementGimmick1() {
        return (this.m1 != null && this.m1 > 0)
                || (this.destructionBattle != null
                        && this.destructionBattle.m1 != null
                        && this.destructionBattle.m1 > 0);
    }

    /**
     * ギミック2が達成されたかを返します
     * @return
     */
    @JsonIgnore
    public boolean achievementGimmick2() {
        return (this.m2 != null && this.m2 > 0)
                || (this.destructionBattle != null
                        && this.destructionBattle.m2 != null
                        && this.destructionBattle.m2 > 0);
    }

    /**
     * JsonObjectから{@link MapStartNext}を構築します
     *
     * @param json JsonObject
     * @return {@link MapStartNext}
     */
    public static MapStartNext toMapStartNext(JsonObject json) {
        MapStartNext bean = new MapStartNext();
        JsonHelper.bind(json)
                .at("api_data")
                .setInteger("api_rashin_flg", bean::setRashinFlg)
                .setInteger("api_rashin_id", bean::setRashinId)
                .setInteger("api_maparea_id", bean::setMapareaId)
                .setInteger("api_mapinfo_no", bean::setMapinfoNo)
                .setInteger("api_no", bean::setNo)
                .setInteger("api_color_no", bean::setColorNo)
                .setInteger("api_event_id", bean::setEventId)
                .setInteger("api_event_kind", bean::setEventKind)
                .setInteger("api_next", bean::setNext)
                .setInteger("api_bosscell_no", bean::setBosscellNo)
                .setInteger("api_bosscomp", bean::setBosscomp)
                .set("api_eventmap", bean::setEventmap, MapTypes.Eventmap::toEventmap)
                .setInteger("api_comment_kind", bean::setCommentKind)
                .setInteger("api_production_kind", bean::setProductionKind)
                .set("api_enemy", bean::setEnemy, MapTypes.Enemy::toEnemy)
                .set("api_happening", bean::setHappening, MapTypes.Happening::toHappening)
                .set("api_itemget", bean::setItemget, JsonHelper.toList(MapTypes.Itemget::toItemget))
                .set("api_select_route", bean::setSelectRoute, MapTypes.SelectRoute::toSelectRoute)
                .setInteger("api_from_no", bean::setFromNo)
                .set("api_destruction_battle", bean::setDestructionBattle, DestructionBattle::toDestructionBattle)
                .setInteger("api_m1", bean::setM1)
                .setInteger("api_m2", bean::setM2)
                .ignore(
                        "api_cell_data", // マップセル一覧（start・クライアント描画用）
                        "api_airsearch", // 索敵機演出
                        "api_limit_state", // 制限状態（観測は常に 0）
                        "api_e_deck_info", // 敵編成プレビュー
                        "api_ration_flag", // 給糧関連（観測は常に 0。要再検討）
                        "api_cell_flavor", // セル文言（クライアント表示）
                        "api_itemget_eo_comment", // 1-6 EO 資材コメント
                        "api_itemget_eo_result", // 1-6 EO 最終クリア報酬（他 EO は battleresult）
                        "api_get_eo_rate") // 1-6 EO 最終クリア戦果（他 EO は battleresult）
                .reportUnknown();
        return bean;
    }

    /**
     * api_destruction_battle
     *
     */
    @Data
    public static class DestructionBattle implements Serializable {

        private static final long serialVersionUID = -6111573497713359784L;

        /** api_formation */
        private List<Integer> formation;

        /** api_ship_ke */
        private List<Integer> shipKe;

        /** api_ship_lv */
        private List<Integer> shipLv;

        /** api_e_nowhps */
        private List<Integer> eNowhps;

        /** api_e_maxhps */
        private List<Integer> eMaxhps;

        /** api_eSlot */
        private List<List<Integer>> eSlot;

        /** api_f_nowhps（基地HP） */
        private List<Integer> fNowhps;

        /** api_f_maxhps（基地最大HP） */
        private List<Integer> fMaxhps;

        /** api_air_base_attack（基地空襲では単一オブジェクト） */
        private BattleTypes.AirBaseAttack airBaseAttack;

        /** api_lost_kind */
        private Integer lostKind;

        /** api_m1 */
        private Integer m1;

        /** api_m2 */
        private Integer m2;

        public static DestructionBattle toDestructionBattle(JsonObject json) {
            DestructionBattle bean = new DestructionBattle();
            JsonHelper.bind(json)
                    .at("api_data.api_destruction_battle")
                    .setIntegerList("api_formation", bean::setFormation)
                    .setIntegerList("api_ship_ke", bean::setShipKe)
                    .setIntegerList("api_ship_lv", bean::setShipLv)
                    .setIntegerList("api_e_nowhps", bean::setENowhps)
                    .setIntegerList("api_e_maxhps", bean::setEMaxhps)
                    .set("api_eSlot", bean::setESlot, JsonHelper.toList(JsonHelper::toIntegerList))
                    .setIntegerList("api_f_nowhps", bean::setFNowhps)
                    .setIntegerList("api_f_maxhps", bean::setFMaxhps)
                    .set("api_air_base_attack", bean::setAirBaseAttack, DestructionBattle::toAirBaseAttack)
                    .setInteger("api_lost_kind", bean::setLostKind)
                    .setInteger("api_m1", bean::setM1)
                    .setInteger("api_m2", bean::setM2)
                    .reportUnknown();

            return bean;
        }

        /**
         * 基地空襲の api_air_base_attack（オブジェクト）。
         * 通常戦闘の配列要素用 {@link BattleTypes.AirBaseAttack#toAirBaseAttack} とは
         * {@code api_map_squadron_plane} の有無が異なる。
         */
        private static BattleTypes.AirBaseAttack toAirBaseAttack(JsonObject json) {
            BattleTypes.AirBaseAttack bean = new BattleTypes.AirBaseAttack();
            JsonHelper.bind(json)
                    .at("api_data.api_destruction_battle.api_air_base_attack")
                    .set("api_plane_from", bean::setPlaneFrom, JsonHelper.toList(JsonHelper::toIntegerList))
                    .set("api_stage1", bean::setStage1, BattleTypes.Stage1::toStage1)
                    .set("api_stage2", bean::setStage2, BattleTypes.Stage2::toStage2)
                    .set("api_stage3", bean::setStage3, BattleTypes.Stage3::toStage3)
                    .setIntegerList("api_stage_flag", bean::setStageFlag)
                    .set("api_map_squadron_plane", bean::setMapSquadronPlane,
                            DestructionBattle::toMapSquadronPlane)
                    .reportUnknown();
            return bean;
        }

        private static Map<Integer, List<BattleTypes.SquadronPlane>> toMapSquadronPlane(JsonObject json) {
            return JsonHelper.toMap(json, Integer::valueOf,
                    JsonHelper.toList(BattleTypes.SquadronPlane::toSquadronPlane));
        }
    }
}
