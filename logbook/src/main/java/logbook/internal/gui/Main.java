package logbook.internal.gui;

import java.awt.Desktop;
import java.awt.desktop.SystemSleepEvent;
import java.awt.desktop.SystemSleepListener;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import logbook.bean.AppConfig;
import logbook.bean.WindowLocation;
import logbook.internal.CheckUpdate;
import logbook.internal.Version;
import lombok.extern.slf4j.Slf4j;

/**
 * JavaFx エントリ・ポイント クラス
 *
 */
@Slf4j
public class Main extends Application implements SystemSleepListener {

    // メインウィンドウのコントローラー
    private static WindowController mainController;
    
    // ディスプレイ構成変更処理中のフラグ（位置変更リスナーでの保存を防ぐため）
    private boolean isProcessingScreenChange = false;
    
    // ディスプレイ構成変更前のウィンドウ位置（復元用）
    private WindowLocation lastValidWindowLocation;
    
    // 起動時に読み込んだ元の位置情報（ディスプレイ構成が完全に戻った時に復元するため）
    private WindowLocation originalWindowLocation;
    
    @Override
    public void start(Stage stage) throws Exception {
        // CheckUpdateのシングルトンインスタンスを取得し、HTTPクライアントを明示的に初期化
        CheckUpdate.getInstance().initializeHttpClient();
        
        String fxmlName = "main";
        if (AppConfig.get().getWindowStyle() != null) {
            fxmlName = AppConfig.get().getWindowStyle();
        }
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().addAppEventListener(this);
        }
        FXMLLoader loader = InternalFXMLLoader.load("logbook/gui/" + fxmlName + ".fxml"); //$NON-NLS-1$
        Parent root = InternalFXMLLoader.setGlobal(loader.load());
        stage.setScene(new Scene(root));

        WindowController controller = loader.getController();
        controller.initWindow(stage);
        setMainController(controller);
        // アイコンの設定
        Tools.Windows.setIcon(stage);
        // 最前面に表示する
        stage.setAlwaysOnTop(AppConfig.get().isOnTop());

        stage.setTitle("航海日誌 " + Version.getCurrent());

        stage.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, e -> {
            if (AppConfig.get().isCheckDoit()) {
                Alert alert = new Alert(AlertType.INFORMATION);
                alert.getDialogPane().getStylesheets().add("logbook/gui/application.css");
                InternalFXMLLoader.setGlobal(alert.getDialogPane());
                alert.initOwner(stage);
                alert.setTitle("終了の確認");
                alert.setHeaderText("終了の確認");
                alert.setContentText("航海日誌を終了しますか？");
                alert.getButtonTypes().clear();
                alert.getButtonTypes().addAll(ButtonType.YES, ButtonType.NO);
                alert.showAndWait()
                        .filter(ButtonType.NO::equals)
                        .ifPresent(t -> e.consume());
            }
            if (!e.isConsumed()) {
                AppConfig.get()
                        .getWindowLocationMap()
                        .put(controller.getClass().getCanonicalName(), controller.getWindowLocation());
            }
        });
        logScreenInfo();
        
        // 起動時に読み込んだ位置情報を保存（ディスプレイ構成が完全に戻った時に復元するため）
        String controllerKey = controller.getClass().getCanonicalName();
        originalWindowLocation = AppConfig.get().getWindowLocationMap().get(controllerKey);
        
        Tools.Windows.defaultOpenAction(controller, null);
        
        // ディスプレイ構成変更を監視
        setupScreenChangeListener();
        
        // ウィンドウ表示時にもチェック
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> {
            Platform.runLater(() -> validateAndFixWindowIfNeeded());
        });
        
        // ウィンドウ位置変更時に有効な位置を保存（リモートデスクトップ接続前の位置を保持するため）
        // ただし、validateAndFixWindowBounds()による自動修正時は保存しない
        stage.xProperty().addListener((obs, oldVal, newVal) -> {
            if (!isProcessingScreenChange) {
                updateLastValidLocationIfNeeded();
            }
        });
        stage.yProperty().addListener((obs, oldVal, newVal) -> {
            if (!isProcessingScreenChange) {
                updateLastValidLocationIfNeeded();
            }
        });
        
        stage.show();
    }
    
    @Override
    public void stop() throws Exception {
        // JavaFXアプリケーション終了時にHTTPクライアントを明示的に停止
        // Platform.exit()が呼ばれると、このメソッドが自動的に呼ばれる
        CheckUpdate.getInstance().shutdown();
    }

    /**
     * JavaFx アプリケーションの起動を行う
     *
     * @param args コマンドライン引数
     */
    public static void main(String[] args) {
        // Bitmapped font should not be sub-pixel rendered.
        // 注意: JavaFX 8時代の設定。現在（JavaFX 23）では使用フォントがアウトラインフォントのため、
        // この設定の必要性は低い可能性がある。プラットフォーム依存の動作も確認が必要。
        // System.setProperty("prism.lcdtext", "false"); //$NON-NLS-1$ //$NON-NLS-2$
        
        launch(args);
    }

    /**
     * メインウィンドウのStageを取得します。
     * @return メインウィンドウのStage（mainControllerがnullの場合はnull）
     */
    public static Stage getPrimaryStage() {
        return mainController != null ? mainController.getWindow() : null;
    }

    public static WindowController getMainController() {
        return mainController;
    }

    public static void setMainController(WindowController controller) {
        Main.mainController = controller;
    }

    @Override
    public void systemAboutToSleep(SystemSleepEvent e) {
        // システムスリープ前の処理は不要（何も処理しない）
    }

    @Override
    public void systemAwoke(SystemSleepEvent e) {
        log.debug("システムがスリープから復帰しました");
        logScreenInfo();
        
        // システム復帰後、次のフレームでウィンドウを検証・修正
        // 注意: ディスプレイ構成変更リスナーがシステム復帰時の変更を検出できない場合に備えて、
        // システム復帰時にも明示的に検証・修正を行う
        Platform.runLater(() -> validateAndFixWindowIfNeeded());
    }

    /**
     * ディスプレイ構成変更を監視するリスナーを設定します。
     */
    private void setupScreenChangeListener() {
        Screen.getScreens().addListener((ListChangeListener<Screen>) c -> {
            while (c.next()) {
                if (c.wasAdded() || c.wasRemoved() || c.wasReplaced()) {
                    log.info("ディスプレイ構成が変更されました");
                    
                    // ディスプレイ構成変更前に、現在のウィンドウ位置を保存
                    // 注意: Screen.getScreens()は既に更新されているため、変更前の有効性はチェックできない
                    // そのため、現在の位置を保存し、後で有効性をチェックする
                    if (mainController != null && mainController.getWindow() != null) {
                        var currentLocation = mainController.getWindowLocation();
                        // 現在の位置を保存（後で有効性をチェックする）
                        lastValidWindowLocation = currentLocation;
                    }
                    
                    logScreenInfo();
                    
                    // 次のフレームで処理を実行
                    Platform.runLater(() -> validateAndFixWindowIfNeeded());
                }
            }
        });
    }

    /**
     * ウィンドウが有効な位置にある場合、その位置を保存します。
     * リモートデスクトップ接続後に元の位置を復元するために使用します。
     */
    private void updateLastValidLocationIfNeeded() {
        var currentLocation = mainController.getWindowLocation();
        // 現在の位置が有効なディスプレイ内にある場合のみ保存
        if (ScreenUtils.isLocationValid(currentLocation)) {
            lastValidWindowLocation = currentLocation;
        }
    }

    /**
     * ディスプレイ情報をログに出力します。
     */
    private void logScreenInfo() {
        var screens = Screen.getScreens();
        log.debug("ディスプレイ数: {}", screens.size());

        for (int i = 0; i < screens.size(); i++) {
            Rectangle2D bounds = screens.get(i).getBounds();
            Rectangle2D visualBounds = screens.get(i).getVisualBounds();
            log.debug("ディスプレイ {} の全体範囲: x={}, y={}, 幅={}, 高さ={}", 
                i, bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
            log.debug("ディスプレイ {} の表示可能範囲: x={}, y={}, 幅={}, 高さ={}", 
                i, visualBounds.getMinX(), visualBounds.getMinY(), visualBounds.getWidth(), visualBounds.getHeight());
        }
    }

    /**
     * ウィンドウの位置とサイズを検証・修正し、レイアウトを再計算します。
     */
    private void validateAndFixWindowIfNeeded() {
        if (mainController == null || mainController.getWindow() == null) {
            log.debug("validateAndFixWindowIfNeeded: mainControllerまたはそのウィンドウがnullのため、処理をスキップします");
            return;
        }

        // ウィンドウの位置とサイズを検証・修正
        // この処理中は位置変更リスナーで保存しないようにする
        isProcessingScreenChange = true;
        try {
            String controllerKey = mainController.getClass().getCanonicalName();
            mainController.validateAndFixWindowIfNeeded(originalWindowLocation, lastValidWindowLocation, controllerKey);
        } finally {
            isProcessingScreenChange = false;
        }
    }
}
