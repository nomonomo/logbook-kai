package logbook.internal.fleet;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import logbook.bean.AppCondition;
import logbook.bean.AppConfig;
import logbook.bean.BattleTypes.CombinedType;
import logbook.bean.DeckPort;
import logbook.bean.Mission;
import logbook.bean.MissionCollection;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.bean.ShipMst;
import logbook.bean.ShipgraphCollection;
import logbook.internal.FleetStats;
import logbook.internal.Ships;
import lombok.Value;

/**
 * 1 艦隊分の表示用コピー。艦や艦隊のオブジェクトは持たない。
 */
@Value
public class FleetSnapshot {

    /** 疲労回復予定がないときのエポック */
    public static final long NO_COND_RECOVER = Long.MAX_VALUE;

    /** 艦隊 ID */
    int deckId;

    /** 艦隊名 */
    String name;

    /** 合計 */
    FleetStats stats;

    /** タブ色 */
    String tabStyle;

    /** 立ち絵背景 */
    String backgroundStyle;

    /** 立ち絵背景を有効にする */
    boolean backgroundEnabled;

    /** 疲労の回復予定エポック秒。回復予定がなければ {@link #NO_COND_RECOVER} */
    long condRecoverEpoch;

    /** 遠征状態 */
    int missionState;

    /** 遠征先 ID */
    int missionTargetId;

    /** 遠征の表示名。未出撃時は「未出撃」または「連合艦隊」 */
    String missionTitle;

    /** 未出撃時に時刻欄へ出す文言（連合艦隊の種類） */
    String idleTimeText;

    /** 遠征の帰還エポックミリ秒 */
    long missionReturnEpoch;

    /** 遠征の所要時間（分） */
    int missionMinutes;

    /** 遠征中 */
    boolean onMission;

    /** 未出撃の色 */
    boolean missionEmpty;

    /** 艦の行 */
    List<FleetShipRow> rows;

    /**
     * 現在の母港から艦隊のコピーを作ります。
     *
     * @param port 艦隊
     * @param branchCoefficient 分岐点係数
     * @return コピー
     */
    public static FleetSnapshot of(DeckPort port, double branchCoefficient) {
        Map<Integer, Ship> shipMap = ShipCollection.get().getShipMap();
        List<Ship> ships = port.getShip().stream()
                .map(shipMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<FleetShipRow> rows = new ArrayList<>(ships.size());
        for (Ship ship : ships) {
            rows.add(FleetShipRow.of(ship));
        }
        AppCondition condition = AppCondition.get();
        MissionView mission = mission(port, condition);
        BackgroundView background = background(ships);
        return new FleetSnapshot(
                port.getId() == null ? 0 : port.getId(),
                port.getName() == null ? "" : port.getName(),
                FleetStats.of(ships, branchCoefficient),
                tabStyle(port, ships, condition),
                background.style,
                background.enabled,
                condRecoverEpoch(ships, condition),
                mission.state,
                mission.targetId,
                mission.title,
                mission.idleTimeText,
                mission.returnEpoch,
                mission.minutes,
                mission.onMission,
                mission.empty,
                List.copyOf(rows));
    }

    private static long condRecoverEpoch(List<Ship> ships, AppCondition condition) {
        int minCond = ships.stream()
                .mapToInt(Ship::getCond)
                .min()
                .orElse(49);
        if (minCond >= 49) {
            return NO_COND_RECOVER;
        }
        long cut = condition.getCondUpdateTime();
        long end = cut + (-Math.floorDiv(49 - minCond, -3) * 180);
        int minute = ZonedDateTime.ofInstant(Instant.ofEpochSecond(end), ZoneId.systemDefault()).getMinute();
        minute = (minute + 1) % 3;
        return end - (60L * minute);
    }

    private static String tabStyle(DeckPort port, List<Ship> ships, AppCondition condition) {
        AppConfig conf = AppConfig.get();
        String left;
        if (ships.stream().anyMatch(Ships::isBadlyDamage)) {
            left = configured(conf.getTabColorBadlyDamage(), "#FF655C");
        } else if (ships.stream().anyMatch(Ships::isHalfDamage)) {
            left = configured(conf.getTabColorHalfDamage(), "#FFBC5C");
        } else if (ships.stream().anyMatch(Ships::isSlightDamage)) {
            left = configured(conf.getTabColorSlightDamage(), "#FFEB5C");
        } else if (ships.stream().anyMatch(ship -> ship.getNowhp() != ship.getMaxhp())) {
            left = configured(conf.getTabColorLessThanSlightDamage(), "#D0EEFF");
        } else {
            left = blankToNull(conf.getTabColorNoDamage());
        }
        String right;
        boolean missionIdle = port.getMission() != null && !port.getMission().isEmpty()
                && port.getMission().get(0) == 0L;
        int deckId = port.getId() == null ? 0 : port.getId();
        if (ships.stream().anyMatch(FleetSnapshot::needsSupply)) {
            right = configured(conf.getTabColorNeedRefuel(), "#FFF030");
        } else if (deckId > 1 && missionIdle && (deckId != 2 || !condition.isCombinedFlag())) {
            right = configured(conf.getTabColorNoMission(), "#87CEFA");
        } else {
            right = null;
        }
        Optional<String> leftColor = usable(left);
        Optional<String> rightColor = usable(right);
        if (leftColor.isEmpty() && rightColor.isEmpty()) {
            return "";
        }
        return "-fx-background-color: -fx-outer-border, -fx-text-box-border, linear-gradient(from 40% 0% to 70% 100%, "
                + leftColor.orElse("-fx-color") + ", " + rightColor.orElse("-fx-color") + ");";
    }

    private static boolean needsSupply(Ship ship) {
        int fuelMax = Ships.shipMst(ship).map(ShipMst::getFuelMax).orElse(0);
        int bullMax = Ships.shipMst(ship).map(ShipMst::getBullMax).orElse(0);
        return !Objects.equals(ship.getFuel(), fuelMax) || !Objects.equals(ship.getBull(), bullMax);
    }

    private static String configured(String configured, String fallback) {
        String color = blankToNull(configured);
        return color == null ? fallback : color;
    }

    private static String blankToNull(String color) {
        if (color == null) {
            return null;
        }
        String trimmed = color.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Optional<String> usable(String color) {
        return Optional.ofNullable(color).map(String::trim).filter(value -> !value.equals("-"));
    }

    private static BackgroundView background(List<Ship> ships) {
        if (!AppConfig.get().isVisiblePoseImageOnFleetTab() || ships.isEmpty()) {
            return new BackgroundView("-fx-background-image: null", false);
        }
        Ship ship = ships.get(0);
        Path path = Ships.shipStandingPoseImagePath(ship);
        if (path == null) {
            return new BackgroundView("-fx-background-image: null", false);
        }
        StringBuilder style = new StringBuilder();
        style.append("-fx-background-image: url('");
        style.append(path.toUri());
        style.append("');");
        style.append("-fx-background-position: ");
        style.append(Ships.shipMst(ship)
                .map(ShipMst::getId)
                .flatMap(id -> Optional.ofNullable(ShipgraphCollection.get().getShipgraphMap().get(id)))
                .map(graph -> ((graph.getWeda().get(0) * -1) + 100) + "px " + (graph.getWeda().get(1) * -1) + "px")
                .orElse("center top"));
        style.append(";");
        return new BackgroundView(style.toString(), true);
    }

    private record BackgroundView(String style, boolean enabled) {
    }

    private static MissionView mission(DeckPort port, AppCondition condition) {
        List<Long> raw = port.getMission();
        int state = raw != null && raw.size() > 0 && raw.get(0) != null ? raw.get(0).intValue() : 0;
        int target = raw != null && raw.size() > 1 && raw.get(1) != null ? raw.get(1).intValue() : 0;
        long time = raw != null && raw.size() > 2 && raw.get(2) != null ? raw.get(2) : 0L;
        boolean onMission = state != 0 && target != 0 && time != 0;
        boolean combinedSecond = condition.isCombinedFlag() && port.getId() != null && port.getId() == 2;
        if (!onMission) {
            if (combinedSecond) {
                String combined = Optional.of(condition.getCombinedType())
                        .map(CombinedType::toCombinedType)
                        .filter(type -> type != CombinedType.未結成)
                        .map(CombinedType::toString)
                        .orElse("<不明な連合艦隊タイプ>");
                return new MissionView(state, target, "(連合艦隊)", combined, time, 0, false, false);
            }
            return new MissionView(state, target, "<未出撃>", "", time, 0, false, true);
        }
        Optional<Mission> mission = Optional.ofNullable(MissionCollection.get().getMissionMap().get(target));
        return new MissionView(state, target,
                mission.map(Mission::getName).orElse(""),
                "",
                time,
                mission.map(Mission::getTime).orElse(0),
                true,
                false);
    }

    private static final class MissionView {
        private final int state;
        private final int targetId;
        private final String title;
        private final String idleTimeText;
        private final long returnEpoch;
        private final int minutes;
        private final boolean onMission;
        private final boolean empty;

        private MissionView(int state, int targetId, String title, String idleTimeText, long returnEpoch, int minutes,
                boolean onMission, boolean empty) {
            this.state = state;
            this.targetId = targetId;
            this.title = title;
            this.idleTimeText = idleTimeText;
            this.returnEpoch = returnEpoch;
            this.minutes = minutes;
            this.onMission = onMission;
            this.empty = empty;
        }
    }
}
