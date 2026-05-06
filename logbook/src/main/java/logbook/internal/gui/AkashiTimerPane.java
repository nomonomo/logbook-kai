package logbook.internal.gui;

import java.io.IOException;
import java.time.Duration;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import logbook.bean.AppCondition;
import logbook.internal.LoggerHolder;
import logbook.internal.Time;

/**
 * 泊地修理・母港給糧（野埼）タイマー表示
 */
public class AkashiTimerPane extends VBox {

    @FXML
    private Label time;

    @FXML
    private Label portSupplyTime;

    /**
     * 泊地修理・母港給糧タイマーペインのコンストラクタ
     */
    public AkashiTimerPane() {
        try {
            FXMLLoader loader = InternalFXMLLoader.load("logbook/gui/akashi_timer.fxml");
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
            this.update();
        } catch (Exception e) {
            LoggerHolder.get().error("FXMLの初期化に失敗しました", e);
        }
    }

    /**
     * 画面を更新します
     */
    public void update() {
        long now = System.currentTimeMillis();
        long akashi = AppCondition.get().getAkashiTimer();
        if (akashi > 0) {
            this.time.setText(Time.toString(Duration.ofMillis(now - akashi), ""));
        } else {
            this.time.setText("");
        }
        long nosaki = AppCondition.get().getNosakiTimer();
        if (nosaki > 0) {
            this.portSupplyTime.setText(Time.toString(Duration.ofMillis(now - nosaki), ""));
        } else {
            this.portSupplyTime.setText("");
        }
    }
}
