package logbook.api;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.json.JsonObject;

import logbook.bean.Mapinfo;
import logbook.internal.JsonHelper;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * /kcsapi/api_get_member/mapinfo
 *
 */
@API("/kcsapi/api_get_member/mapinfo")
public class ApiGetMemberMapinfo implements APIListenerSpi {

    /** Collection 等に反映するキー */
    private static final Set<String> HANDLED_API_DATA_KEYS = Set.of(
            "api_map_info",
            "api_air_base");

    /** 未対応でよいと確認済みのキー */
    private static final Set<String> IGNORED_API_DATA_KEYS = Set.of(
            "api_air_base_expanded_info");

    /** 未知キー報告の対象外（対応済み + 意図的未対応） */
    private static final Set<String> KNOWN_API_DATA_KEYS = Stream
            .concat(HANDLED_API_DATA_KEYS.stream(), IGNORED_API_DATA_KEYS.stream())
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public void accept(JsonObject json, RequestMetaData req, ResponseMetaData res) {
        JsonObject object = json.getJsonObject("api_data");
        if (object != null) {
            JsonHelper.reportUnknownKeys(object, "api_data", KNOWN_API_DATA_KEYS);
            Mapinfo mapinfo = Mapinfo.get();

            // 海域
            if (object.getJsonArray("api_map_info") != null) {
                mapinfo.setMapInfo(JsonHelper.toList(object.getJsonArray("api_map_info"), Mapinfo.MapInfo::toMapInfo));
            }
            // 基地航空隊
            if (object.getJsonArray("api_air_base") != null) {
                mapinfo.setAirBase(JsonHelper.toList(object.getJsonArray("api_air_base"), Mapinfo.AirBase::toAirBase));
            } else {
                mapinfo.getAirBase().clear();
            }
            mapinfo.setLastModified(System.currentTimeMillis());
        }
    }

}
