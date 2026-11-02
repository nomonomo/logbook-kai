package logbook.api;

import java.util.StringJoiner;

import jakarta.json.JsonObject;

import logbook.bean.AppCondition;
import logbook.bean.BattleLog;
import logbook.bean.BattleTypes.CombinedType;
import logbook.bean.MapStartNext;
import logbook.internal.BattleEventLogs;
import logbook.internal.DestructionBattleSupport;
import logbook.internal.notify.NotificationFacts;
import logbook.notify.BadlyDamagedAlert.Phase;
import logbook.notify.GimmickAchieved;
import logbook.notify.Notifications;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * /kcsapi/api_req_map/next
 *
 */
@API("/kcsapi/api_req_map/next")
public class ApiReqMapNext implements APIListenerSpi {

    @Override
    public void accept(JsonObject json, RequestMetaData req, ResponseMetaData res) {

        JsonObject data = json.getJsonObject("api_data");
        if (data != null) {
            MapStartNext next = MapStartNext.toMapStartNext(data);

            AppCondition condition = AppCondition.get();
            BattleLog log = condition.getBattleResult();
            if (log == null) {
                log = new BattleLog();
                condition.setBattleResult(log);
            }
            log.setCombinedType(CombinedType.toCombinedType(condition.getCombinedType()));
            log.getNext().add(next);
            DestructionBattleSupport.onMapNext(next);
            BattleEventLogs.writeNextGimmicks(next);
            // ルート情報
            condition.getRoute().add(new StringJoiner("-")
                    .add(data.getJsonNumber("api_maparea_id").toString())
                    .add(data.getJsonNumber("api_mapinfo_no").toString())
                    .add(data.getJsonNumber("api_no").toString())
                    .toString());

            NotificationFacts.publishBadlyDamaged(Phase.ADVANCE, condition.getDeckId(), condition.isCombinedFlag());
            if (next.achievementGimmick1()) {
                Notifications.publish(new GimmickAchieved(GimmickAchieved.Kind.SEA_AREA_CHANGE));
            }
            if (next.achievementGimmick2()) {
                Notifications.publish(new GimmickAchieved(GimmickAchieved.Kind.ACHIEVED));
            }
        }
    }
}
