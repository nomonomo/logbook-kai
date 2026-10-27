package logbook.internal.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import logbook.bean.AppCondition;
import logbook.bean.AppConfig;
import logbook.internal.FleetStats;
import logbook.internal.fleet.FleetSnapshot;

/**
 * 1 艦隊の表示値。画面はここへバインドする。
 */
public class FleetModel {

    private static final DateTimeFormatter COND_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private FleetSnapshot snapshot;

    /** 疲労通知を出した、または出す対象の回復予定。通知しないときは {@link FleetSnapshot#NO_COND_RECOVER} */
    private long armedCondEpoch = FleetSnapshot.NO_COND_RECOVER;

    /** 前回適用した回復予定。同じ値の再適用では通知予約をやり直さない */
    private long trackedCondEpoch = FleetSnapshot.NO_COND_RECOVER;

    private boolean trackedCond;

    private final StringProperty name = new SimpleStringProperty("");

    private final StringProperty airSuperiority = new SimpleStringProperty("");

    private final StringProperty touchPlaneStartProbability = new SimpleStringProperty("");

    private final StringProperty decision33 = new SimpleStringProperty("");

    private final StringProperty decision33Detail = new SimpleStringProperty("");

    private final StringProperty branchCoefficient = new SimpleStringProperty("");

    private final StringProperty lvSum = new SimpleStringProperty("");

    private final StringProperty karyokuSum = new SimpleStringProperty("");

    private final StringProperty taikuSum = new SimpleStringProperty("");

    private final StringProperty taisenSum = new SimpleStringProperty("");

    private final StringProperty sakutekiSum = new SimpleStringProperty("");

    private final StringProperty tpSum = new SimpleStringProperty("");

    private final StringProperty speed = new SimpleStringProperty("");

    private final StringProperty speedStyle = new SimpleStringProperty("");

    private final StringProperty tabStyle = new SimpleStringProperty("");

    private final StringProperty backgroundStyle = new SimpleStringProperty("-fx-background-image: null");

    private final BooleanProperty backgroundEnabled = new SimpleBooleanProperty(false);

    private final StringProperty condText = new SimpleStringProperty("");

    private final StringProperty missionTitle = new SimpleStringProperty("");

    private final StringProperty idleTimeText = new SimpleStringProperty("");

    private final BooleanProperty onMission = new SimpleBooleanProperty(false);

    private final BooleanProperty missionEmpty = new SimpleBooleanProperty(false);

    private final LongProperty missionReturnEpoch = new SimpleLongProperty(0);

    private final IntegerProperty missionMinutes = new SimpleIntegerProperty(0);

    private int missionState;

    /**
     * 直近のコピーを返します。
     *
     * @return 未適用なら null
     */
    public FleetSnapshot getSnapshot() {
        return this.snapshot;
    }

    /**
     * コピーを反映します。前回と同じなら何もしません。
     *
     * @param snapshot コピー
     * @return 内容が変わった場合 true
     */
    public boolean apply(FleetSnapshot snapshot) {
        if (snapshot.equals(this.snapshot)) {
            return false;
        }
        this.snapshot = snapshot;
        this.trackCond(snapshot);
        FleetStats stats = snapshot.getStats();
        this.name.set(snapshot.getName());
        this.airSuperiority.set(stats.airSuperiorityText());
        this.touchPlaneStartProbability.set(stats.touchPlaneStartText());
        this.decision33.set(stats.decision33Text());
        this.decision33Detail.set(stats.decision33Detail());
        this.branchCoefficient.set(stats.branchCoefficientText());
        this.lvSum.set(Integer.toString(stats.getLvSum()));
        this.karyokuSum.set(Integer.toString(stats.getKaryokuSum()));
        this.taikuSum.set(Integer.toString(stats.getTaikuSum()));
        this.taisenSum.set(Integer.toString(stats.getTaisenSum()));
        this.sakutekiSum.set(Integer.toString(stats.getSakutekiSum()));
        this.tpSum.set(stats.transportPointText());
        this.speed.set(stats.getSpeedLabel());
        this.speedStyle.set(stats.getSpeedStyle());
        this.tabStyle.set(snapshot.getTabStyle());
        this.backgroundStyle.set(snapshot.getBackgroundStyle());
        this.backgroundEnabled.set(snapshot.isBackgroundEnabled());
        this.missionTitle.set(snapshot.getMissionTitle());
        this.idleTimeText.set(snapshot.getIdleTimeText());
        this.onMission.set(snapshot.isOnMission());
        this.missionEmpty.set(snapshot.isMissionEmpty());
        this.missionReturnEpoch.set(snapshot.getMissionReturnEpoch());
        this.missionMinutes.set(snapshot.getMissionMinutes());
        this.missionState = snapshot.getMissionState();
        return true;
    }

    /**
     * 疲労表示を現在時刻で更新し、回復時刻を過ぎていれば通知してよいかを返します。
     *
     * @param nowEpoch 現在時刻（エポック秒）
     * @return 通知する場合 true
     */
    public boolean pollCondRecover(long nowEpoch) {
        this.condText.set(this.condText(nowEpoch));
        if (this.armedCondEpoch == FleetSnapshot.NO_COND_RECOVER || nowEpoch <= this.armedCondEpoch) {
            return false;
        }
        this.armedCondEpoch = FleetSnapshot.NO_COND_RECOVER;
        return !AppCondition.get().isMapStart() && this.missionState == 0;
    }

    private void trackCond(FleetSnapshot snapshot) {
        long end = snapshot.getCondRecoverEpoch();
        this.trackedCond = true;
        this.trackedCondEpoch = end;
        long now = ZonedDateTime.now(ZoneId.systemDefault()).toEpochSecond();
        boolean inPort = snapshot.getMissionState() == 0 && !AppCondition.get().isMapStart();
        if (end != FleetSnapshot.NO_COND_RECOVER && end > now && AppConfig.get().isUseCondRecoverToast() && inPort) {
            this.armedCondEpoch = end;
        } else {
            this.armedCondEpoch = FleetSnapshot.NO_COND_RECOVER;
        }
    }

    private String condText(long nowEpoch) {
        long end = this.trackedCond ? this.trackedCondEpoch : FleetSnapshot.NO_COND_RECOVER;
        if (end == FleetSnapshot.NO_COND_RECOVER || nowEpoch >= end) {
            return "";
        }
        ZonedDateTime time = ZonedDateTime.ofInstant(Instant.ofEpochSecond(end), ZoneId.systemDefault());
        return COND_FORMAT.format(time);
    }

    /**
     * 艦隊名を返します。
     *
     * @return 艦隊名
     */
    public String getName() {
        return this.name.get();
    }

    /**
     * 艦隊名を返します。
     *
     * @return 艦隊名
     */
    public StringProperty nameProperty() {
        return this.name;
    }

    /**
     * 制空値を返します。
     *
     * @return 制空値
     */
    public StringProperty airSuperiorityProperty() {
        return this.airSuperiority;
    }

    /**
     * 触接開始率を返します。
     *
     * @return 触接開始率
     */
    public StringProperty touchPlaneStartProbabilityProperty() {
        return this.touchPlaneStartProbability;
    }

    /**
     * 判定式(33) を返します。
     *
     * @return 判定式(33)
     */
    public StringProperty decision33Property() {
        return this.decision33;
    }

    /**
     * 判定式(33) の内訳を返します。
     *
     * @return 内訳
     */
    public String getDecision33Detail() {
        return this.decision33Detail.get();
    }

    /**
     * 分岐点係数を返します。
     *
     * @return 分岐点係数
     */
    public StringProperty branchCoefficientProperty() {
        return this.branchCoefficient;
    }

    /**
     * レベル合計を返します。
     *
     * @return レベル合計
     */
    public StringProperty lvSumProperty() {
        return this.lvSum;
    }

    /**
     * 火力合計を返します。
     *
     * @return 火力合計
     */
    public StringProperty karyokuSumProperty() {
        return this.karyokuSum;
    }

    /**
     * 対空合計を返します。
     *
     * @return 対空合計
     */
    public StringProperty taikuSumProperty() {
        return this.taikuSum;
    }

    /**
     * 対潜合計を返します。
     *
     * @return 対潜合計
     */
    public StringProperty taisenSumProperty() {
        return this.taisenSum;
    }

    /**
     * 索敵合計を返します。
     *
     * @return 索敵合計
     */
    public StringProperty sakutekiSumProperty() {
        return this.sakutekiSum;
    }

    /**
     * TP 合計を返します。
     *
     * @return TP 合計
     */
    public StringProperty tpSumProperty() {
        return this.tpSum;
    }

    /**
     * 速度を返します。
     *
     * @return 速度
     */
    public StringProperty speedProperty() {
        return this.speed;
    }

    /**
     * 速度のスタイルを返します。
     *
     * @return 速度のスタイル
     */
    public StringProperty speedStyleProperty() {
        return this.speedStyle;
    }

    /**
     * タブ色を返します。
     *
     * @return タブ色
     */
    public StringProperty tabStyleProperty() {
        return this.tabStyle;
    }

    /**
     * 立ち絵背景を返します。
     *
     * @return 立ち絵背景
     */
    public StringProperty backgroundStyleProperty() {
        return this.backgroundStyle;
    }

    /**
     * 立ち絵背景の有効を返します。
     *
     * @return 有効なら true
     */
    public BooleanProperty backgroundEnabledProperty() {
        return this.backgroundEnabled;
    }

    /**
     * 疲労回復時刻を返します。
     *
     * @return 疲労回復時刻
     */
    public StringProperty condTextProperty() {
        return this.condText;
    }

    /**
     * 遠征の表示名を返します。
     *
     * @return 遠征の表示名
     */
    public StringProperty missionTitleProperty() {
        return this.missionTitle;
    }

    /**
     * 未出撃時の時刻欄を返します。
     *
     * @return 連合艦隊の種類、または空
     */
    public String getIdleTimeText() {
        return this.idleTimeText.get();
    }

    /**
     * 遠征中かを返します。
     *
     * @return 遠征中なら true
     */
    public boolean isOnMission() {
        return this.onMission.get();
    }

    /**
     * 未出撃の色にするかを返します。
     *
     * @return 未出撃なら true
     */
    public boolean isMissionEmpty() {
        return this.missionEmpty.get();
    }

    /**
     * 帰還エポックミリ秒を返します。
     *
     * @return 帰還エポックミリ秒
     */
    public long getMissionReturnEpoch() {
        return this.missionReturnEpoch.get();
    }

    /**
     * 遠征の所要時間（分）を返します。
     *
     * @return 所要時間（分）
     */
    public int getMissionMinutes() {
        return this.missionMinutes.get();
    }
}
