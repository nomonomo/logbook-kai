package logbook.api;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import logbook.bean.DeckPort;
import logbook.bean.DeckPortCollection;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.internal.JsonHelper;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * /kcsapi/api_get_member/ship3
 *
 */
@API("/kcsapi/api_get_member/ship3")
public class ApiGetMemberShip3 implements APIListenerSpi {

    /** Collection 等に反映するキー */
    private static final Set<String> HANDLED_API_DATA_KEYS = Set.of(
            "api_ship_data",
            "api_deck_data");

    /** 未対応でよいと確認済みのキー */
    private static final Set<String> IGNORED_API_DATA_KEYS = Set.of(
            "api_slot_data");

    /** 未知キー報告の対象外（対応済み + 意図的未対応） */
    private static final Set<String> KNOWN_API_DATA_KEYS = Stream
            .concat(HANDLED_API_DATA_KEYS.stream(), IGNORED_API_DATA_KEYS.stream())
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public void accept(JsonObject json, RequestMetaData req, ResponseMetaData res) {
        JsonObject data = json.getJsonObject("api_data");
        if (data != null) {
            JsonHelper.reportUnknownKeys(data, "api_data", KNOWN_API_DATA_KEYS);
            this.apiShipData(data.getJsonArray("api_ship_data"), req);
            this.apiDeckData(data.getJsonArray("api_deck_data"));
        }
    }

    /**
     * api_data.api_ship_data
     *
     * @param array api_ship_data
     * @param req リクエスト
     */
    private void apiShipData(JsonArray array, RequestMetaData req) {
        Map<Integer, Ship> map = ShipCollection.get()
                .getShipMap();
        if (!req.getParameterMap()
                .containsKey("api_shipid")) {
            // 艦娘の指定がない場合クリア
            map.clear();
        }
        map.putAll(JsonHelper.toMap(array, Ship::getId, Ship::toShip));
    }

    /**
     * api_data.api_deck_data
     *
     * @param array api_deck_data
     */
    private void apiDeckData(JsonArray array) {
        Map<Integer, DeckPort> deckMap = JsonHelper.toMap(array, DeckPort::getId, DeckPort::toDeckPort);
        DeckPortCollection.get()
                .setDeckPortMap(deckMap);
        DeckPortCollection.get()
                .setMissionShips(deckMap.values()
                        .stream()
                        .filter(d -> d.getMission().get(0) != 0)
                        .map(DeckPort::getShip)
                        .flatMap(List::stream)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }
}
