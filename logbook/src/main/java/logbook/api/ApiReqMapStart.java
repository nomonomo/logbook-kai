package logbook.api;

import java.util.StringJoiner;

import jakarta.json.JsonObject;

import logbook.bean.AppCondition;
import logbook.bean.BattleLog;
import logbook.bean.BattleTypes.CombinedType;
import logbook.bean.MapStartNext;
import logbook.internal.DestructionBattleSupport;
import logbook.internal.notify.NotificationFacts;
import logbook.notify.BadlyDamagedAlert.Phase;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * /kcsapi/api_req_map/start
 *
 */
@API("/kcsapi/api_req_map/start")
public class ApiReqMapStart implements APIListenerSpi {

    @Override
    public void accept(JsonObject json, RequestMetaData req, ResponseMetaData res) {

        JsonObject data = json.getJsonObject("api_data");
        if (data != null) {
            BattleLog log = new BattleLog();
            log.setCombinedType(CombinedType.toCombinedType(AppCondition.get().getCombinedType()));
            log.getNext().add(MapStartNext.toMapStartNext(data));

            AppCondition condition = AppCondition.get();
            condition.setBattleResult(log);
            condition.setMapStart(Boolean.TRUE);
            condition.setDeckId(Integer.parseInt(req.getParameter("api_deck_id")));
            DestructionBattleSupport.onMapStart(log.getNext().get(0));
            // ルート情報
            condition.getRoute().add(new StringJoiner("-")
                    .add(data.getJsonNumber("api_maparea_id").toString())
                    .add(data.getJsonNumber("api_mapinfo_no").toString())
                    .add(data.getJsonNumber("api_no").toString())
                    .toString());

            NotificationFacts.publishBadlyDamaged(Phase.SORTIE, condition.getDeckId(), condition.isCombinedFlag());
        }
    }
}
