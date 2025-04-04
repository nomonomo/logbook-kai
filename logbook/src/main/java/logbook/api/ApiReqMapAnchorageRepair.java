package logbook.api;

import java.util.Map;
import java.util.Optional;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.internal.JsonHelper;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * /kcsapi/api_req_map/anchorage_repair
 */
@API("/kcsapi/api_req_map/anchorage_repair")
public class ApiReqMapAnchorageRepair implements APIListenerSpi {

    @Override
    public void accept(JsonObject json, RequestMetaData req, ResponseMetaData res) {
        Optional.ofNullable(json.getJsonObject("api_data"))
            .map(data -> data.getJsonArray("api_ship_data"))
            .ifPresent(this::apiShipData);
    }
    
    /**
     * api_data.api_ship_data
     *
     * @param array api_ship_data
     */
    private void apiShipData(JsonArray array) {
        Map<Integer, Ship> map = ShipCollection.get().getShipMap();
        map.putAll(JsonHelper.toMap(array, Ship::getId, Ship::toShip));
    }
}
