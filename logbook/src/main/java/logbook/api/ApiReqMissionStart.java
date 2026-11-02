package logbook.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.json.JsonObject;

import logbook.bean.DeckPort;
import logbook.bean.DeckPortCollection;
import logbook.bean.Mission;
import logbook.bean.MissionCollection;
import logbook.bean.MissionCondition;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.internal.LoggerHolder;
import logbook.internal.Missions;
import logbook.notify.MissionConditionAlert;
import logbook.notify.Notifications;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * /kcsapi/api_req_mission/start
 *
 */
@API("/kcsapi/api_req_mission/start")
public class ApiReqMissionStart implements APIListenerSpi {

    @Override
    public void accept(JsonObject json, RequestMetaData req, ResponseMetaData res) {

        JsonObject data = json.getJsonObject("api_data");
        if (data != null) {
            Integer deckId = Integer.valueOf(req.getParameter("api_deck_id"));
            Integer missionId = Integer.valueOf(req.getParameter("api_mission_id"));

            try {
                Optional<MissionCondition> condition = Missions.getMissionCondition(missionId);
                if (condition.isPresent()) {
                    Map<Integer, Ship> shipMap = ShipCollection.get()
                            .getShipMap();
                    List<Ship> fleet = DeckPortCollection.get().getDeckPortMap().get(deckId).getShip()
                            .stream()
                            .map(shipMap::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    if (!condition.get().test(fleet)) {
                        Notifications.publish(missionConditionAlert(deckId, missionId));
                    }
                }
            } catch (Exception e) {
                LoggerHolder.get().error("遠征開始で例外", e);
            }
        }
    }

    private static MissionConditionAlert missionConditionAlert(int deckId, int missionId) {
        DeckPort port = DeckPortCollection.get().getDeckPortMap().get(deckId);
        String fleetName = port != null && port.getName() != null ? port.getName() : "";
        Mission mission = MissionCollection.get().getMissionMap().get(missionId);
        String missionName = mission != null && mission.getName() != null ? mission.getName() : "";
        return new MissionConditionAlert(deckId, fleetName, missionName);
    }
}
