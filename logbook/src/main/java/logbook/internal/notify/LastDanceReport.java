package logbook.internal.notify;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import logbook.bean.BattleLog;
import logbook.bean.BattleResult;
import logbook.bean.Enemy;
import logbook.bean.MapStartNext;
import logbook.bean.MapTypes;
import logbook.bean.Mapinfo;
import logbook.bean.Mapinfo.MapInfo;
import logbook.internal.PhaseState;
import logbook.notify.LastDance;
import logbook.notify.Notifications;
import lombok.extern.slf4j.Slf4j;

/**
 * 戦闘結果からラストダンス通知を作ります。
 * 戦力ゲージのボス戦で、この戦闘によって次の1回で割り切れる状態になったときだけ出します。
 */
@Slf4j
public final class LastDanceReport {

    /** api_gauge_type。戦力ゲージ */
    private static final int POWER_GAUGE = 2;

    private LastDanceReport() {
    }

    /**
     * 旗艦の被ダメージです。
     *
     * @param damage 戦闘開始時 HP から減った量。オーバーキルは含まない
     * @param maxHp 最大HP
     */
    record FlagshipDamage(int damage, int maxHp) {
    }

    /**
     * 結果の揃った戦闘を通知します。出せないときは何もしません。
     *
     * @param battleLog 結果まで入った戦闘ログ
     */
    public static void publish(BattleLog battleLog) {
        from(battleLog, gaugeType(battleLog)).ifPresent(Notifications::publish);
    }

    /**
     * 戦闘結果から通知を作ります。
     *
     * @param battleLog 戦闘ログ
     * @param gaugeType 母港の api_gauge_type。戦力は 2
     * @return ラストダンスに入ったときだけ通知
     */
    static Optional<LastDance> from(BattleLog battleLog, Integer gaugeType) {
        if (battleLog == null || gaugeType == null || gaugeType != POWER_GAUGE) {
            return Optional.empty();
        }
        MapStartNext cell = lastCell(battleLog);
        if (cell == null || !bossCell(cell)) {
            return Optional.empty();
        }
        MapTypes.Eventmap eventmap = cell.getEventmap();
        if (eventmap == null || eventmap.getNowMaphp() == null || eventmap.getNowMaphp() < 1) {
            return Optional.empty();
        }
        Optional<FlagshipDamage> hit = flagshipDamage(battleLog);
        if (hit.isEmpty()) {
            return Optional.empty();
        }
        int before = eventmap.getNowMaphp();
        int damage = hit.get().damage();
        int bossMaxHp = hit.get().maxHp();
        if (!entered(before, damage, bossMaxHp)) {
            return Optional.empty();
        }
        return Optional.of(new LastDance(
                mapName(battleLog, cell),
                before,
                damage,
                before - damage,
                bossMaxHp));
    }

    /**
     * この戦闘でラストダンスに入ったかを返します。
     * 戦闘前はまだ超えていて、減算後が 1 以上かつボス最大HP以下のときだけ true です。
     *
     * @param before 戦闘前のゲージ残量
     * @param damage ボス旗艦に与えたダメージ
     * @param bossMaxHp ボス旗艦の最大HP
     * @return 入ったなら true
     */
    static boolean entered(int before, int damage, int bossMaxHp) {
        if (before < 1 || bossMaxHp < 1 || damage < 0) {
            return false;
        }
        int after = before - damage;
        return before > bossMaxHp && after > 0 && after <= bossMaxHp;
    }

    /**
     * 敵主力艦隊の旗艦が失った HP を返します。昼戦と夜戦を含め、終了時 HP は 0 未満を 0 にします。
     *
     * @param battleLog 戦闘ログ
     * @return 旗艦 HP が取れないときは empty
     */
    static Optional<FlagshipDamage> flagshipDamage(BattleLog battleLog) {
        if (battleLog == null || battleLog.getBattle() == null || battleLog.getDeckMap() == null) {
            return Optional.empty();
        }
        try {
            PhaseState phase = new PhaseState(battleLog);
            if (phase.getAfterEnemy().isEmpty() || phase.getAfterEnemy().get(0) == null) {
                return Optional.empty();
            }
            Enemy flagship = phase.getAfterEnemy().get(0);
            if (flagship.getNowhp() == null || flagship.getMaxhp() == null || flagship.getMaxhp() < 1
                    || flagship.getNowhp() < 1) {
                return Optional.empty();
            }
            int startNow = flagship.getNowhp();
            int maxHp = flagship.getMaxhp();
            phase.apply(battleLog.getBattle());
            phase.apply(battleLog.getMidnight());
            int end = flagship.getNowhp() == null ? 0 : Math.max(0, flagship.getNowhp());
            int damage = startNow - end;
            if (damage < 0) {
                return Optional.empty();
            }
            return Optional.of(new FlagshipDamage(damage, maxHp));
        } catch (RuntimeException e) {
            log.warn("旗艦ダメージを計算できませんでした", e);
            return Optional.empty();
        }
    }

    /**
     * 母港の mapinfo から、この戦闘の海域のゲージ種別を返します。
     * マップ ID は海域番号×10＋マップ番号です。62-5 なら 625。
     *
     * @param battleLog 戦闘ログ
     * @return api_gauge_type。海域が無いときは null
     */
    static Integer gaugeType(BattleLog battleLog) {
        MapStartNext cell = lastCell(battleLog);
        if (cell == null || cell.getMapareaId() == null || cell.getMapinfoNo() == null) {
            return null;
        }
        int mapId = cell.getMapareaId() * 10 + cell.getMapinfoNo();
        List<MapInfo> maps = Mapinfo.get().getMapInfo();
        if (maps == null) {
            return null;
        }
        for (MapInfo info : maps) {
            if (info != null && Objects.equals(info.getId(), mapId)) {
                return info.getGaugeType();
            }
        }
        return null;
    }

    private static MapStartNext lastCell(BattleLog battleLog) {
        if (battleLog == null || battleLog.getNext() == null || battleLog.getNext().isEmpty()) {
            return null;
        }
        return battleLog.getNext().get(battleLog.getNext().size() - 1);
    }

    private static boolean bossCell(MapStartNext cell) {
        if (cell.getEventId() != null && cell.getEventId() == 5) {
            return true;
        }
        return cell.getNo() != null && cell.getNo().equals(cell.getBosscellNo());
    }

    private static String mapName(BattleLog battleLog, MapStartNext cell) {
        BattleResult result = battleLog.getResult();
        if (result != null && result.getQuestName() != null && !result.getQuestName().isBlank()) {
            return result.getQuestName();
        }
        if (cell.getMapareaId() != null && cell.getMapinfoNo() != null) {
            return cell.getMapareaId() + "-" + cell.getMapinfoNo();
        }
        return "";
    }
}
