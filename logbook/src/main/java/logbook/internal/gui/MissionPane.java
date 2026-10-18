package logbook.internal.gui;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.AnchorPane;
import logbook.internal.LoggerHolder;
import logbook.internal.Time;

/**
 * 遠征
 *
 */
public class MissionPane extends AnchorPane {

    /** 色変化1段階目 */
    private final Duration stage1 = Duration.ofMinutes(20);

    /** 色変化2段階目 */
    private final Duration stage2 = Duration.ofMinutes(10);

    /** 色変化3段階目 */
    private final Duration stage3 = Duration.ofMinutes(1);

    /** 表示値 */
    private final FleetModel model;

    @FXML
    private ProgressBar progress;

    @FXML
    private Label fleet;

    @FXML
    private Label name;

    @FXML
    private Label time;

    /**
     * 遠征ペインのコンストラクタ
     *
     * @param model 表示値
     */
    public MissionPane(FleetModel model) {
        this.model = model;
        try {
            FXMLLoader loader = InternalFXMLLoader.load("logbook/gui/mission.fxml");
            loader.setRoot(this);
            loader.setController(this);
            loader.load();
        } catch (IOException e) {
            LoggerHolder.get().error("FXMLのロードに失敗しました", e);
        }
    }

    @FXML
    void initialize() {
        try {
            this.fleet.textProperty().bind(this.model.nameProperty());
            this.name.textProperty().bind(this.model.missionTitleProperty());
            this.update();
            PopOver<FleetModel> popover = new PopOver<>((node, model) -> {
                return new PopOverPane(this.model.getName(), this.returnMessage());
            });
            popover.install(this, this.model);
        } catch (Exception e) {
            LoggerHolder.get().error("FXMLの初期化に失敗しました", e);
        }
    }

    /**
     * 残り時間と進捗を更新します。艦隊名と遠征名はモデルにバインドしています。
     */
    public void update() {
        ObservableList<String> styleClass = this.getStyleClass();
        styleClass.removeAll("stage1", "stage2", "stage3", "empty");

        if (!this.model.isOnMission()) {
            this.progress.setProgress(0);
            this.progress.setVisible(false);
            this.time.setText(this.model.getIdleTimeText());
            if (this.model.isMissionEmpty()) {
                styleClass.add("empty");
            }
            return;
        }

        long time = this.model.getMissionReturnEpoch();
        Duration max = Duration.ofMinutes(this.model.getMissionMinutes());
        Duration now = Duration.ofMillis(time - System.currentTimeMillis());
        if (!max.isZero()) {
            double p = (double) (max.toMillis() - now.toMillis()) / (double) max.toMillis();
            this.progress.setProgress(p);
            this.progress.setVisible(true);
        } else {
            this.progress.setProgress(0);
            this.progress.setVisible(false);
        }
        this.time.setText(Time.toString(now, "まもなく帰還します"));
        if (now.compareTo(this.stage3) < 0) {
            styleClass.add("stage3");
        } else if (now.compareTo(this.stage2) < 0) {
            styleClass.add("stage2");
        } else if (now.compareTo(this.stage1) < 0) {
            styleClass.add("stage1");
        }
    }

    private String returnMessage() {
        long time = this.model.getMissionReturnEpoch();
        if (time > 0 && this.model.isOnMission()) {
            ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneOffset.systemDefault());
            if (dateTime.toLocalDate().equals(ZonedDateTime.now().toLocalDate())) {
                return "今日 " + DateTimeFormatter.ofPattern("H時m分s秒").format(dateTime) + " 頃に帰投します";
            }
            return DateTimeFormatter.ofPattern("M月d日 H時m分s秒").format(dateTime) + " 頃に帰投します";
        }
        return "任務がありません";
    }
}
