package logbook.internal.notify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import logbook.bean.DeckPort;
import logbook.bean.DeckPortCollection;
import logbook.bean.NdockCollection;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;

/**
 * 母港給糧（野埼）の対象判定と、COND 54 到達までの見積もり。
 * 15 分ごとに母港へ戻ると仮定し、各ステップで自然回復（+3/3分・上限49）のあと給糧（+2/+3・上限54）を適用する。
 */
public final class PortSupply {

    /** 野埼(996, +2), 野埼改(1002, +3) */
    private static final Map<Integer, Integer> SUPPLY_SHIP_GAINS = Map.of(
            996, 2,
            1002, 3);

    /** 給糧込みの COND 上限 */
    public static final int COND_CAP = 54;

    /** 自然回復の COND 上限 */
    public static final int NATURAL_CAP = 49;

    /** 自然回復 1 回の増加 */
    public static final int NATURAL_GAIN = 3;

    /** 母港に戻ると仮定する間隔 */
    public static final Duration TICK = Duration.ofMinutes(15);

    /** 自然回復の間隔 */
    public static final Duration NATURAL_TICK = Duration.ofMinutes(3);

    static final int MAX_STEPS = 24;

    private PortSupply() {
    }

    /**
     * 給糧対象艦隊の投影入力。
     *
     * @param fleetId 艦隊番号
     * @param fleetName 艦隊名
     * @param gain 1 回帰港あたりの給糧
     * @param minCond 対象艦の最小 COND
     */
    public record FleetState(int fleetId, String fleetName, int gain, int minCond) {
    }

    /**
     * 1 回の走査結果。再計算判定用のハッシュと、到達計算用の艦隊状態を持つ。
     * 野埼タイマーと自然回復の起点は含めない。
     *
     * @param portsHash 艦隊のハッシュ
     * @param shipsHash 艦のハッシュ
     * @param ndocksHash 入渠のハッシュ
     * @param fleets 給糧対象艦隊
     */
    public record Observation(
            int portsHash,
            int shipsHash,
            int ndocksHash,
            List<FleetState> fleets) {

        public Observation {
            fleets = List.copyOf(fleets);
        }
    }

    /**
     * 艦種 ID が給糧艦なら true。
     *
     * @param masterId 艦種 ID
     * @return 給糧艦なら true
     */
    public static boolean isSupplyShip(Integer masterId) {
        return masterId != null && SUPPLY_SHIP_GAINS.containsKey(masterId);
    }

    /**
     * 給糧艦の 1 回あたりの増加。対象外は 0。
     *
     * @param masterId 艦種 ID
     * @return 増加量
     */
    public static int gainOf(int masterId) {
        return SUPPLY_SHIP_GAINS.getOrDefault(masterId, 0);
    }

    /**
     * {@code steps} 回母港に戻ったあとの COND。
     * 自然回復上限（49）到達後は給糧のみなので閉形式で足す。
     *
     * @param cond 現在の COND
     * @param gain 1 回の給糧
     * @param steps 帰港回数
     * @param condOriginMillis 自然回復の起点（epoch milli）。0 以下なら {@code timerMillis} を使う
     * @param timerMillis 野埼タイマー（epoch milli）
     * @return 到達後の COND
     */
    public static int projectedCond(int cond, int gain, int steps, long condOriginMillis, long timerMillis) {
        if (steps <= 0) {
            return cond;
        }
        if (cond >= NATURAL_CAP) {
            return applySupply(cond, gain, steps);
        }
        int current = cond;
        long origin = condOriginMillis > 0 ? condOriginMillis : timerMillis;
        for (int i = 1; i <= steps; i++) {
            long visitAt = timerMillis + TICK.toMillis() * i;
            int natTicks = (int) Math.max(0L, (visitAt - origin) / NATURAL_TICK.toMillis());
            current = Math.min(NATURAL_CAP, current + NATURAL_GAIN * natTicks);
            current = Math.min(COND_CAP, current + gain);
            origin = visitAt;
            if (current >= NATURAL_CAP) {
                return applySupply(current, gain, steps - i);
            }
        }
        return current;
    }

    /**
     * 1 隻（または艦隊の最小 COND）が COND 54 に達する最小ステップ。すでに 54 以上なら 0。
     * 49 以上は給糧のみなので {@link #supplyStepsToCap(int, int)} で一括計算する。
     *
     * @param cond 現在の COND
     * @param gain 1 回の給糧
     * @param condOriginMillis 自然回復の起点（epoch milli）
     * @param timerMillis 野埼タイマー（epoch milli）
     * @return ステップ数
     */
    public static int stepsToCap(int cond, int gain, long condOriginMillis, long timerMillis) {
        if (cond >= COND_CAP) {
            return 0;
        }
        if (cond >= NATURAL_CAP) {
            return supplyStepsToCap(cond, gain);
        }
        int current = cond;
        long origin = condOriginMillis > 0 ? condOriginMillis : timerMillis;
        for (int steps = 1; steps <= MAX_STEPS; steps++) {
            long visitAt = timerMillis + TICK.toMillis() * steps;
            int natTicks = (int) Math.max(0L, (visitAt - origin) / NATURAL_TICK.toMillis());
            current = Math.min(NATURAL_CAP, current + NATURAL_GAIN * natTicks);
            current = Math.min(COND_CAP, current + gain);
            origin = visitAt;
            if (current >= COND_CAP) {
                return steps;
            }
            if (current >= NATURAL_CAP) {
                int remain = supplyStepsToCap(current, gain);
                return Math.min(MAX_STEPS, steps + remain);
            }
        }
        return MAX_STEPS;
    }

    /**
     * 自然回復上限到達後の給糧ステップ数。
     *
     * @param cond 現在の COND
     * @param gain 1 回の給糧
     * @return ステップ数
     */
    static int supplyStepsToCap(int cond, int gain) {
        if (cond >= COND_CAP) {
            return 0;
        }
        if (gain <= 0) {
            return MAX_STEPS;
        }
        return -Math.floorDiv(COND_CAP - cond, -gain);
    }

    /**
     * デッキを 1 周し、再計算判定用ハッシュと給糧対象艦隊を返す。
     *
     * @return 走査結果
     */
    public static Observation observe() {
        Map<Integer, Ship> shipMap = ShipCollection.get().getShipMap();
        Set<Integer> ndockSet = NdockCollection.get().getNdockSet();
        int portsHash = 1;
        int shipsHash = 1;
        List<FleetState> fleets = new ArrayList<>();
        for (DeckPort port : DeckPortCollection.get().getDeckPortMap().values()) {
            if (port == null) {
                continue;
            }
            portsHash = 31 * portsHash + port.hashCode();
            List<Ship> ships = resolveShips(port.getShip(), shipMap);
            shipsHash = 31 * shipsHash + ships.hashCode();
            int gain = gainOfFleet(port, ships);
            if (gain == 0) {
                continue;
            }
            OptionalInt minCond = minTargetCond(ships, ndockSet);
            if (minCond.isPresent()) {
                int fleetId = port.getId() != null ? port.getId() : 0;
                fleets.add(new FleetState(fleetId, fleetName(port, fleetId), gain, minCond.getAsInt()));
            }
        }
        return new Observation(portsHash, shipsHash, ndockSet.hashCode(), fleets);
    }

    /**
     * 艦隊ごとの到達時刻（epoch milli）。既に上限の艦隊は含めない。
     *
     * @param nosakiTimerMillis 野埼タイマー
     * @param condOriginMillis 自然回復の起点
     * @param fleets 対象艦隊
     * @return 艦隊番号から到達時刻
     */
    public static Map<Integer, Long> dueAtByFleet(
            long nosakiTimerMillis, long condOriginMillis, List<FleetState> fleets) {
        Map<Integer, Long> dueAt = new LinkedHashMap<>();
        for (FleetState fleet : fleets) {
            if (fleet.gain() == 0) {
                continue;
            }
            int steps = stepsToCap(fleet.minCond(), fleet.gain(), condOriginMillis, nosakiTimerMillis);
            if (steps > 0) {
                dueAt.put(fleet.fleetId(), nosakiTimerMillis + TICK.toMillis() * steps);
            }
        }
        return dueAt;
    }

    /**
     * 1 艦隊の到達時刻。既に上限なら empty。
     *
     * @param nosakiTimerMillis 野埼タイマー
     * @param condOriginMillis 自然回復の起点
     * @param gain 1 回の給糧
     * @param minCond 最小 COND
     * @return 到達時刻
     */
    public static OptionalLong dueAtMillis(
            long nosakiTimerMillis, long condOriginMillis, int gain, int minCond) {
        if (gain == 0) {
            return OptionalLong.empty();
        }
        int steps = stepsToCap(minCond, gain, condOriginMillis, nosakiTimerMillis);
        if (steps == 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(nosakiTimerMillis + TICK.toMillis() * steps);
    }

    private static int applySupply(int cond, int gain, int steps) {
        if (steps <= 0 || cond >= COND_CAP) {
            return Math.min(COND_CAP, cond);
        }
        return Math.min(COND_CAP, cond + gain * steps);
    }

    static String fleetName(DeckPort port, int fleetId) {
        if (port != null && port.getName() != null && !port.getName().isEmpty()) {
            return port.getName();
        }
        return "艦隊 #" + fleetId;
    }

    static int missionState(DeckPort port) {
        if (port == null || port.getMission() == null || port.getMission().isEmpty()
                || port.getMission().get(0) == null) {
            return 0;
        }
        return port.getMission().get(0).intValue();
    }

    private static List<Ship> resolveShips(List<Integer> ids, Map<Integer, Ship> shipMap) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Ship> ships = new ArrayList<>(ids.size());
        for (Integer sid : ids) {
            if (sid == null || sid <= 0) {
                continue;
            }
            Ship ship = shipMap.get(sid);
            if (ship != null) {
                ships.add(ship);
            }
        }
        return ships;
    }

    private static int gainOfFleet(DeckPort port, List<Ship> ships) {
        if (missionState(port) != 0 || ships.isEmpty()) {
            return 0;
        }
        int gain = 0;
        int limit = Math.min(2, ships.size());
        for (int i = 0; i < limit; i++) {
            Integer masterId = ships.get(i).getShipId();
            if (masterId != null) {
                gain = Math.max(gain, gainOf(masterId));
            }
        }
        return gain;
    }

    /**
     * 給糧対象の随伴艦（給糧艦本人と入渠中を除く）の最小 COND。
     */
    private static OptionalInt minTargetCond(List<Ship> ships, Set<Integer> ndockSet) {
        int min = Integer.MAX_VALUE;
        for (Ship ship : ships) {
            Integer sid = ship.getId();
            if (sid == null || sid <= 0 || ndockSet.contains(sid)) {
                continue;
            }
            Integer masterId = ship.getShipId();
            if (masterId == null || SUPPLY_SHIP_GAINS.containsKey(masterId)) {
                continue;
            }
            Integer cond = ship.getCond();
            if (cond == null) {
                continue;
            }
            min = Math.min(min, cond);
        }
        return min == Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of(min);
    }
}
