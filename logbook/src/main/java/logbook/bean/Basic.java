package logbook.bean;

import java.io.Serializable;

import jakarta.json.JsonObject;

import logbook.internal.Config;
import logbook.internal.JsonHelper;
import lombok.Data;

/**
 * api_basic
 *
 */
@Data
public class Basic implements Serializable {

    private static final long serialVersionUID = -2392950337873034663L;

    /** api_comment */
    private String comment = "";

    /** api_count_deck */
    private Integer countDeck = 0;

    /** api_count_kdock */
    private Integer countKdock = 0;

    /** api_count_ndock */
    private Integer countNdock = 0;

    /** api_experience */
    private Integer experience = 0;

    /** api_fcoin */
    private Integer fcoin = 0;

    /** api_large_dock */
    private Boolean largeDock = Boolean.FALSE;

    /** api_level */
    private Integer level = 0;

    /** api_rank */
    private Integer rank = 0;

    /** api_max_chara */
    private Integer maxChara = 0;

    /** api_max_slotitem */
    private Integer maxSlotitem = 0;

    /** api_medals */
    private Integer medals = 0;

    /** api_nickname */
    private String nickname = "";

    /**
     * JsonObjectから{@link Basic}を構築します
     *
     * @param bean Basic
     * @param json JsonObject
     * @return {@link Basic}
     */
    public static Basic updateBasic(Basic bean, JsonObject json) {
        JsonHelper.bind(json)
                .at("api_data.api_basic")
                .setString("api_comment", bean::setComment)
                .setInteger("api_count_deck", bean::setCountDeck)
                .setInteger("api_count_kdock", bean::setCountKdock)
                .setInteger("api_count_ndock", bean::setCountNdock)
                .setInteger("api_experience", bean::setExperience)
                .setInteger("api_fcoin", bean::setFcoin)
                .setBoolean("api_large_dock", bean::setLargeDock)
                .setInteger("api_level", bean::setLevel)
                .setInteger("api_rank", bean::setRank)
                .setInteger("api_max_chara", bean::setMaxChara)
                .setInteger("api_max_slotitem", bean::setMaxSlotitem)
                .setInteger("api_medals", bean::setMedals)
                .setString("api_nickname", bean::setNickname)
                .ignore(
                        "api_member_id", // 提督 ID
                        "api_nickname_id", // ニックネーム ID
                        "api_active_flag", // アクティブフラグ
                        "api_starttime", // セッション開始時刻らしき値
                        "api_fleetname", // 連合艦隊名など
                        "api_comment_id", // コメント ID
                        "api_max_kagu", // 家具上限関連
                        "api_playtime", // プレイ時間（クライアント用）
                        "api_tutorial", // チュートリアルフラグ
                        "api_furniture", // 設置家具 ID
                        "api_st_win", // 出撃勝利数
                        "api_st_lose", // 出撃敗北数
                        "api_ms_count", // 遠征回数
                        "api_ms_success", // 遠征成功数
                        "api_pt_win", // 演習勝利数
                        "api_pt_lose", // 演習敗北数
                        "api_pt_challenged", // 演習被挑戦
                        "api_pt_challenged_win", // 演習被挑戦勝利
                        "api_firstflag", // 初回フラグ
                        "api_tutorial_progress", // チュートリアル進捗
                        "api_pvp") // 演習関連カウンタ
                .reportUnknown();
        return bean;
    }

    /**
     * アプリケーションのデフォルト設定ディレクトリから{@link Basic}を取得します、
     * これは次の記述と同等です
     * <blockquote>
     *     <code>Config.getDefault().get(Basic.class, Basic::new)</code>
     * </blockquote>
     *
     * @return {@link Basic}
     */
    public static Basic get() {
        return Config.getDefault().get(Basic.class, Basic::new);
    }
}
