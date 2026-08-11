package logbook.api;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import logbook.bean.Basic;
import logbook.bean.SlotItem;
import logbook.bean.SlotItemCollection;
import logbook.bean.Useitem;
import logbook.bean.UseitemCollection;
import logbook.internal.DestructionBattleSupport;
import logbook.internal.JsonHelper;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * /kcsapi/api_get_member/require_info
 *
 */
@API("/kcsapi/api_get_member/require_info")
public class ApiGetMemberRequireInfo implements APIListenerSpi {

    /** Collection 等に反映するキー */
    private static final Set<String> HANDLED_API_DATA_KEYS = Set.of(
            "api_basic",
            "api_slot_item",
            "api_useitem");

    /** 未対応でよいと確認済みのキー */
    private static final Set<String> IGNORED_API_DATA_KEYS = Set.of(
            "api_unsetslot",
            "api_kdock",
            "api_furniture",
            "api_extra_supply",
            "api_oss_setting",
            "api_skin_id",
            "api_position_id");

    /** 未知キー報告の対象外（対応済み + 意図的未対応） */
    private static final Set<String> KNOWN_API_DATA_KEYS = Stream
            .concat(HANDLED_API_DATA_KEYS.stream(), IGNORED_API_DATA_KEYS.stream())
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public void accept(JsonObject json, RequestMetaData req, ResponseMetaData res) {
        // ログインやり直し・画面リロード時。出撃継続が不明なため基地空襲 pending を破棄
        DestructionBattleSupport.discardPending();
        JsonObject data = json.getJsonObject("api_data");
        if (data != null) {
            JsonHelper.reportUnknownKeys(data, "api_data", KNOWN_API_DATA_KEYS);
            this.apiBasic(data.getJsonObject("api_basic"));
            this.apiSlotItem(data.getJsonArray("api_slot_item"));
            // api_useitem (オプショナル)
            JsonArray useitemArray = data.getJsonArray("api_useitem");
            if (useitemArray != null) {
                this.apiUseitem(useitemArray);
            }
        }
    }

    /**
     * api_data.api_basic
     *
     * @param object api_basic
     */
    private void apiBasic(JsonObject object) {
        Basic.updateBasic(Basic.get(), object);
        // require_info の時の api_basic は戦果が送られてこないので更新すべきでない
        //AppExpRecords.get().update(Basic.get());
    }

    /**
     * api_data.api_slot_item
     *
     * @param array api_slot_item
     */
    private void apiSlotItem(JsonArray array) {
        SlotItemCollection.get()
                .setSlotitemMap(JsonHelper.toMap(array, SlotItem::getId, SlotItem::toSlotItem));
    }

    /**
     * api_data.api_useitem
     *
     * @param array api_useitem
     */
    private void apiUseitem(JsonArray array) {
        UseitemCollection.get()
                .setUseitemMap(JsonHelper.toMap(array, Useitem::getId, Useitem::toUseitem));
    }

}
