package logbook.api;

import java.util.Map;

import jakarta.json.JsonObject;

import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * /kcsapi/api_req_kaisou/slot_exchange_index
 *
 */
@API("/kcsapi/api_req_kaisou/slot_exchange_index")
public class ApiReqKaisouSlotExchangeIndex implements APIListenerSpi {

    @Override
    public void accept(JsonObject json, RequestMetaData req, ResponseMetaData res) {
        JsonObject data = json.getJsonObject("api_data");
        if (data != null) {
            Map<Integer, Ship> shipMap = ShipCollection.get()
                    .getShipMap();

            Integer shipId = Integer.valueOf(req.getParameter("api_id"));
            shipMap.put(shipId, Ship.toShip(data.getJsonObject("api_ship_data")));
        }
    }

}
