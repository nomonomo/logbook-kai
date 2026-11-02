package logbook.internal.notify;

import java.util.List;
import java.util.stream.Collectors;

import logbook.bean.AppBouyomiConfig;
import logbook.internal.BouyomiChanUtils;
import logbook.internal.BouyomiChanUtils.Type;
import logbook.internal.Tuple;
import logbook.notify.BadlyDamagedAlert;
import logbook.notify.BadlyDamagedAlert.ShipNotice;
import logbook.notify.CondRecovered;
import logbook.notify.GimmickAchieved;
import logbook.notify.MissionConditionAlert;
import logbook.notify.MissionReturning;
import logbook.notify.MissionStarted;
import logbook.notify.NdockCompleted;
import logbook.notify.Notification;
import logbook.notify.NotificationListener;
import logbook.notify.PortSupplyCompleted;

/**
 * 棒読みちゃんへ読み上げを送る。母港給糧と遠征開始は持たない。遠征完了の再通知は読まない。
 */
public final class BouyomiNotificationListener implements NotificationListener {

    @Override
    public void onNotify(Notification event) {
        if (!AppBouyomiConfig.get().isEnable()) {
            return;
        }
        switch (event) {
            case MissionReturning e -> {
                if (!e.remind()) {
                    speakMission(e);
                }
            }
            case NdockCompleted e -> speakNdock(e);
            case BadlyDamagedAlert e -> speakBadly(e);
            case MissionConditionAlert e -> speakMissionCondition(e);
            case GimmickAchieved e -> speakGimmick(e);
            case CondRecovered _ -> {
            }
            case MissionStarted _ -> {
            }
            case PortSupplyCompleted _ -> {
            }
        }
    }

    private static void speakMission(MissionReturning event) {
        BouyomiChanUtils.speak(Type.MissionComplete,
                Tuple.of("${fleetName}", event.fleetName()),
                Tuple.of("${fleetNumber}", String.valueOf(event.fleetId())),
                Tuple.of("${missionName}", event.missionName()));
    }

    private static void speakNdock(NdockCompleted event) {
        BouyomiChanUtils.speak(Type.NdockComplete,
                Tuple.of("${hiraganaName}", event.yomi()),
                Tuple.of("${kanjiName}", event.shipName()));
    }

    private static void speakBadly(BadlyDamagedAlert event) {
        List<ShipNotice> named = BadlyDamageNotices.ships(event).stream()
                .filter(ship -> ship.name() != null && !ship.name().isEmpty())
                .toList();
        if (named.isEmpty()) {
            return;
        }
        String hiragana = named.stream()
                .map(ship -> ship.yomi() == null ? "" : ship.yomi())
                .collect(Collectors.joining("、"));
        String kanji = named.stream()
                .map(ShipNotice::name)
                .collect(Collectors.joining("、"));
        BouyomiChanUtils.speak(Type.MapStartNextAlert,
                Tuple.of("${hiraganaNames}", hiragana),
                Tuple.of("${kanjiNames}", kanji));
    }

    private static void speakMissionCondition(MissionConditionAlert event) {
        BouyomiChanUtils.speak(Type.MissionStartAlert,
                Tuple.of("${fleetName}", event.fleetName()),
                Tuple.of("${fleetNumber}", String.valueOf(event.fleetId())),
                Tuple.of("${missionName}", event.missionName()));
    }

    private static void speakGimmick(GimmickAchieved event) {
        BouyomiChanUtils.speak(gimmickType(event.kind()));
    }

    private static Type gimmickType(GimmickAchieved.Kind kind) {
        if (kind == GimmickAchieved.Kind.SEA_AREA_CHANGE) {
            return Type.AchievementGimmick1;
        }
        return Type.AchievementGimmick2;
    }
}
