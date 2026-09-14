package logbook.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import logbook.bean.BattleEventDetail;
import logbook.bean.BattleEventLog;
import logbook.bean.BattleLog;
import logbook.bean.BattleTypes;
import logbook.bean.BattleTypes.Stage1;
import logbook.bean.MapStartNext;
import logbook.bean.MapStartNext.DestructionBattle;
import logbook.bean.MapinfoMst;
import logbook.bean.MapinfoMstCollection;
import logbook.bean.ShipMst;
import logbook.bean.ShipMstCollection;
import logbook.bean.SlotitemMst;
import logbook.bean.SlotitemMstCollection;
import logbook.internal.log.BattleEventLogFormat;
import logbook.internal.log.LogWriter;

/**
 * 戦闘イベントログの生成と出力。
 */
public final class BattleEventLogs {

    private BattleEventLogs() {
    }

    /**
     * 確定した基地空襲を記録する。
     *
     * @param next map/next
     */
    public static void writeAirRaid(MapStartNext next) {
        BattleEventLog event = createAirRaid(next);
        if (event != null) {
            BattleEventDetails.write(createAirRaidDetail(event, next));
            write(event);
        }
    }

    /**
     * 基地空襲のイベント行を作成する。
     *
     * @param next map/next
     * @return イベント行。空襲がない場合はnull
     */
    public static BattleEventLog createAirRaid(MapStartNext next) {
        DestructionBattle destruction = next.getDestructionBattle();
        if (destruction == null) {
            return null;
        }
        BattleEventLog event = base(next);
        event.setType("空襲");
        event.setContent(DestructionBattleSupport.lostKindText(destruction.getLostKind()));
        event.setGimmick(gimmickText(destruction.getM1(), destruction.getM2()));
        applyFormation(event, destruction.getFormation());
        applyAirBaseAttack(event, destruction.getAirBaseAttack());
        applyEnemyShips(event, destruction);
        event.setBaseHp(hpValues(destruction.getFNowhps(), destruction.getFMaxhps()));
        return event;
    }

    /**
     * 基地空襲の詳細JSONを作成する。
     *
     * @param event CSVイベント行
     * @param next map/next
     * @return 詳細
     */
    public static BattleEventDetail createAirRaidDetail(BattleEventLog event, MapStartNext next) {
        BattleEventDetail detail = new BattleEventDetail();
        detail.setTime(event.getTime());
        detail.setType(event.getType());
        detail.setNext(next);
        return detail;
    }

    /**
     * map/next直下のギミック値を記録する。
     *
     * @param next map/next
     */
    public static void writeNextGimmicks(MapStartNext next) {
        createNextGimmicks(next).forEach(BattleEventLogs::write);
    }

    /**
     * map/next直下のギミックイベント行を作成する。
     *
     * @param next map/next
     * @return イベント行
     */
    public static List<BattleEventLog> createNextGimmicks(MapStartNext next) {
        List<BattleEventLog> events = new ArrayList<>();
        String text = gimmickText(next.getM1(), next.getM2());
        if (!text.isEmpty()) {
            BattleEventLog event = base(next);
            event.setType("ギミック");
            event.setContent(text);
            event.setGimmick(text);
            events.add(event);
        }
        return events;
    }

    /**
     * 母港で通知されたギミック値を直前戦闘の海域付きで記録する。
     *
     * @param lastBattle 直前戦闘
     * @param value api_m_flag2
     */
    public static void writePortGimmick(BattleLog lastBattle, int value) {
        BattleEventLog event = createPortGimmick(lastBattle, value);
        if (event != null) {
            write(event);
        }
    }

    /**
     * 母港ギミックのイベント行を作成する。
     *
     * @param lastBattle 直前戦闘
     * @param value api_m_flag2
     * @return イベント行。値が0以下の場合はnull
     */
    public static BattleEventLog createPortGimmick(BattleLog lastBattle, int value) {
        if (value <= 0) {
            return null;
        }
        MapStartNext next = Optional.ofNullable(lastBattle)
                .map(BattleLog::getNext)
                .filter(list -> !list.isEmpty())
                .map(List::getLast)
                .orElse(null);
        BattleEventLog event = next != null ? base(next) : new BattleEventLog();
        event.setTime(Logs.nowString());
        event.setType("帰還時通知");
        event.setContent("ギミック達成通知");
        event.setGimmick("ギミック達成通知");
        return event;
    }

    private static String gimmickText(Integer m1, Integer m2) {
        List<String> values = new ArrayList<>();
        if (m1 != null && m1 > 0) {
            values.add("ルート追加等");
        }
        if (m2 != null && m2 > 0) {
            values.add("装甲破砕等");
        }
        return String.join("、", values);
    }

    private static void write(BattleEventLog event) {
        LogWriter.getInstance(BattleEventLogFormat::new).write(event);
    }

    private static BattleEventLog base(MapStartNext next) {
        BattleEventLog event = new BattleEventLog();
        event.setTime(Logs.nowString());
        event.setArea(areaText(next));
        event.setCell(Optional.ofNullable(next.getNo()).map(String::valueOf).orElse(""));
        return event;
    }

    private static void applyFormation(BattleEventLog event, List<Integer> formation) {
        if (formation == null || formation.size() < 3) {
            return;
        }
        event.setFformation(BattleTypes.Formation.toFormation(formation.get(0)).toString());
        event.setEformation(BattleTypes.Formation.toFormation(formation.get(1)).toString());
        event.setIntercept(BattleTypes.Intercept.toIntercept(formation.get(2)).toString());
    }

    private static void applyAirBaseAttack(BattleEventLog event, BattleTypes.AirBaseAttack airBaseAttack) {
        if (airBaseAttack == null || airBaseAttack.getStage1() == null) {
            return;
        }
        Stage1 stage1 = airBaseAttack.getStage1();
        if (stage1.getDispSeiku() != null) {
            event.setDispseiku(BattleTypes.DispSeiku.toDispSeiku(stage1.getDispSeiku()).toString());
        }
        if (stage1.getTouchPlane() != null && stage1.getTouchPlane().size() >= 2) {
            Map<Integer, SlotitemMst> slotitems = SlotitemMstCollection.get().getSlotitemMap();
            event.setFtouch(itemName(slotitems, stage1.getTouchPlane().get(0)));
            event.setEtouch(itemName(slotitems, stage1.getTouchPlane().get(1)));
        }
    }

    private static String itemName(Map<Integer, SlotitemMst> slotitems, Integer id) {
        return Optional.ofNullable(slotitems.get(id)).map(SlotitemMst::getName).orElse("");
    }

    private static void applyEnemyShips(BattleEventLog event, DestructionBattle destruction) {
        List<Integer> shipKe = destruction.getShipKe();
        if (shipKe == null) {
            return;
        }
        Map<Integer, ShipMst> shipMap = ShipMstCollection.get().getShipMap();
        List<String> names = new ArrayList<>();
        List<String> hp = new ArrayList<>();
        for (int i = 0; i < Math.min(shipKe.size(), 12); i++) {
            Integer shipId = shipKe.get(i);
            names.add(enemyName(shipMap.get(shipId), shipId));
            hp.add(hpAt(destruction.getENowhps(), destruction.getEMaxhps(), i));
        }
        event.setEnemyShips(names);
        event.setEnemyHp(hp);
    }

    private static String enemyName(ShipMst ship, Integer id) {
        if (ship == null) {
            return id != null && id > 0 ? String.valueOf(id) : "";
        }
        String yomi = ship.getYomi();
        return yomi == null || yomi.isEmpty() || "-".equals(yomi)
                ? ship.getName()
                : ship.getName() + "(" + yomi + ")";
    }

    private static String hpAt(List<Integer> now, List<Integer> max, int index) {
        return now != null && max != null && now.size() > index && max.size() > index
                ? now.get(index) + "/" + max.get(index)
                : "";
    }

    private static List<String> hpValues(List<Integer> now, List<Integer> max) {
        if (now == null || max == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < Math.min(now.size(), max.size()); i++) {
            values.add(now.get(i) + "/" + max.get(i));
        }
        return values;
    }

    private static String areaText(MapStartNext next) {
        if (next == null || next.getMapareaId() == null || next.getMapinfoNo() == null) {
            return "";
        }
        String shortName = next.getMapareaId() + "-" + next.getMapinfoNo();
        String mapName = MapinfoMstCollection.get().getMapinfo().values().stream()
                .filter(m -> Objects.equals(m.getMapareaId(), next.getMapareaId())
                        && Objects.equals(m.getNo(), next.getMapinfoNo()))
                .map(MapinfoMst::getName)
                .findFirst()
                .orElse("");
        return mapName.isEmpty() ? shortName : shortName + " " + mapName;
    }

}
