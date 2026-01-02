package logbook.internal.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;
import logbook.bean.AppCondition;
import logbook.bean.AppConfig;
import logbook.bean.BattleLog;
import logbook.internal.BattleLogs;
import logbook.internal.BattleLogs.SimpleBattleLog;
import logbook.internal.CheckUpdate;
import logbook.internal.log.BattleResultLogFormat;
import lombok.extern.slf4j.Slf4j;
import logbook.internal.log.LogWriter;
import logbook.plugin.gui.MainCalcMenu;
import logbook.plugin.gui.MainCommandMenu;
import logbook.plugin.gui.MainExtMenu;
import logbook.plugin.gui.Plugin;

/**
 * UIコントローラー
 *
 */
@Slf4j
public class MainMenuController extends WindowController {

    // メイン画面のコントローラ
    private MainController parentController;

    /** コマンドメニュー */
    @FXML
    private Menu command;

    /** 計算機 */
    @FXML
    private Menu calc;

    /** その他 */
    @FXML
    private Menu ext;

    @FXML
    void initialize() {
        try {
            // プラグインによるメニューの追加
            this.addMenuItem(MainCommandMenu.class, this.command.getItems());
            this.addMenuItem(MainCalcMenu.class, this.calc.getItems());
            this.addMenuItem(MainExtMenu.class, this.ext.getItems());
        } catch (Exception e) {
            log.error("FXMLの初期化に失敗しました", e);
        }
    }

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    /**
     * キャプチャ
     *
     * @param e ActionEvent
     */
    @FXML
    void capture(ActionEvent e) {
        try {
            InternalFXMLLoader.showWindow("logbook/gui/capture.fxml", this.parentController.getWindow(), "キャプチャ");
        } catch (Exception ex) {
            log.error("キャプチャの初期化に失敗しました", ex);
        }
    }

    /**
     * 現在の戦闘
     *
     * @param e ActionEvent
     */
    @FXML
    void battleStatus(ActionEvent e) {
        try {
            BattleLog battleLog = AppCondition.get()
                    .getBattleResult();
            if (battleLog == null || battleLog.getBattle() == null) {
                Path dir = Paths.get(AppConfig.get().getReportPath());
                Path path = dir.resolve(new BattleResultLogFormat().fileName());
                if (Files.exists(path)) {
                    try (Stream<String> lines = Files.lines(path, LogWriter.DEFAULT_CHARSET)) {
                        SimpleBattleLog simpleBattleLog = lines.skip(1).reduce((first, second) -> second)
                                .map(SimpleBattleLog::new)
                                .orElse(null);
                        if (simpleBattleLog != null) {
                            battleLog = BattleLogs.read(BattleLogDetail.toBattleLogDetail(simpleBattleLog).getDate());
                        }
                    } catch (Exception ex) {
                        battleLog = null;
                    }
                }
            }
            if (battleLog != null && battleLog.getBattle() != null) {
                BattleLog sendlog = battleLog;
                InternalFXMLLoader.showWindow("logbook/gui/battle_detail.fxml", this.parentController.getWindow(),
                        "現在の戦闘", c -> {
                            BattleDetail battleDetail = (BattleDetail) c;
                            // イベント駆動型の更新を設定（API処理完了時に自動更新）
                            // 一度だけEvent登録を行い、ウィンドウが閉じられた時に解除される
                            battleDetail.setEventDrivenUpdate(sendlog);
                            log.debug("battleStatus: setEventDrivenUpdate完了");
                        }, null);
            } else {
                Tools.Controls.alert(AlertType.INFORMATION, "現在の戦闘", "戦闘のデータがありません", this.parentController.getWindow());
            }
        } catch (Exception ex) {
            log.error("現在の戦闘の表示に失敗しました", ex);
        }
    }

    /**
     * 現在の演習
     * 
     * @param e ActionEvent
     */
    @FXML
    void practiceStatus(ActionEvent e) {
        try {
            BattleLog battleLog = AppCondition.get().getPracticeBattleResult();
            if (battleLog != null && battleLog.getBattle() != null) {
                BattleLog sendlog = battleLog;
                InternalFXMLLoader.showWindow("logbook/gui/battle_detail.fxml", this.parentController.getWindow(),
                        "演習詳細", "practice", null, c -> {
                            // イベント駆動型の更新を設定（初期表示データも設定）
                            ((BattleDetail) c).setEventDrivenUpdateForPractice(sendlog);
                        }, null);
            } else {
                Tools.Controls.alert(AlertType.INFORMATION, "演習詳細", "演習のデータがありません", this.parentController.getWindow());
            }
        } catch (Exception ex) {
            log.error("演習詳細の表示に失敗しました", ex);
        }
    }

    /**
     * 戦闘ログ
     *
     * @param e ActionEvent
     */
    @FXML
    void battlelog(ActionEvent e) {
        try {
            InternalFXMLLoader.showWindow("logbook/gui/battlelog.fxml", this.parentController.getWindow(), "戦闘ログ");
        } catch (Exception ex) {
            log.error("戦闘ログの初期化に失敗しました", ex);
        }
    }

    /**
     * 遠征ログ
     *
     * @param e ActionEvent
     */
    @FXML
    void missionlog(ActionEvent e) {
        try {
            InternalFXMLLoader.showWindow("logbook/gui/missionlog.fxml", this.parentController.getWindow(), "遠征ログ");
        } catch (Exception ex) {
            log.error("遠征ログの初期化に失敗しました", ex);
        }
    }

    /**
     * 開発ログ
     *
     * @param e ActionEvent
     */
    @FXML
    void createitemlog(ActionEvent e) {
        try {
            InternalFXMLLoader.showWindow("logbook/gui/createitemlog.fxml", this.parentController.getWindow(), "開発ログ");
        } catch (Exception ex) {
            log.error("開発ログの初期化に失敗しました", ex);
        }
    }

    /**
     * 基地航空隊
     *
     * @param e ActionEvent
     */
    @FXML
    void airBase(ActionEvent e) {
        try {
            InternalFXMLLoader.showWindow("logbook/gui/airbase.fxml", this.parentController.getWindow(), "基地航空隊");
        } catch (Exception ex) {
            log.error("基地航空隊の初期化に失敗しました", ex);
        }
    }

    /**
     * 所有装備
     *
     * @param e ActionEvent
     */
    @FXML
    void items(ActionEvent e) {
        this.parentController.items(e);
    }

    /**
     * 所有艦娘
     *
     * @param e ActionEvent
     */
    @FXML
    void ships(ActionEvent e) {
        this.parentController.ships(e);
    }

    /**
     * アイテム一覧
     *
     * @param e ActionEvent
     */
    @FXML
    void useitems(ActionEvent e) {
        this.parentController.useitems(e);
    }

    /**
     * お風呂に入りたい艦娘
     *
     * @param e ActionEvent
     */
    @FXML
    void ndock(ActionEvent e) {
        try {
            InternalFXMLLoader.showWindow("logbook/gui/require_ndock.fxml", this.parentController.getWindow(), "お風呂に入りたい艦娘");
        } catch (Exception ex) {
            log.error("お風呂に入りたい艦娘の初期化に失敗しました", ex);
        }
    }

    /**
     * 経験値計算機
     *
     * @param e ActionEvent
     */
    @FXML
    void calcExp(ActionEvent e) {
        try {
            InternalFXMLLoader.showWindow("logbook/gui/calc_exp.fxml", this.parentController.getWindow(), "経験値計算機");
        } catch (Exception ex) {
            log.error("経験値計算機の初期化に失敗しました", ex);
        }
    }

    /**
     * 遠征条件確認
     *
     * @param e ActionEvent
     */
    @FXML
    void missionCheck(ActionEvent e) {
        try {
            InternalFXMLLoader.showWindow("logbook/gui/missioncheck.fxml", this.parentController.getWindow(), "遠征条件確認");
        } catch (Exception ex) {
            log.error("遠征条件確認の初期化に失敗しました", ex);
        }
    }

    /**
     * 資材チャート
     *
     * @param e ActionEvent
     */
    @FXML
    void resourceChart(ActionEvent e) {
        try {
            InternalFXMLLoader.showWindow("logbook/gui/resource_chart.fxml", this.parentController.getWindow(), "資材チャート");
        } catch (Exception ex) {
            log.error("資材チャートの初期化に失敗しました", ex);
        }
    }

    /**
     * 経験値チャート
     *
     * @param e ActionEvent
     */
    @FXML
    void expChart(ActionEvent e) {
        try {
            InternalFXMLLoader.showWindow("logbook/gui/exp_chart.fxml", this.parentController.getWindow(), "経験値チャート");
        } catch (Exception ex) {
            log.error("経験値チャートの初期化に失敗しました", ex);
        }
    }

    /**
     * 編成記録
     *
     * @param e ActionEvent
     */
    @FXML
    void deck(ActionEvent e) {
        try {
            InternalFXMLLoader.showWindow("logbook/gui/deck.fxml", this.parentController.getWindow(), "編成記録");
        } catch (Exception ex) {
            log.error("編成記録の初期化に失敗しました", ex);
        }
    }

    /**
     * 自動プロキシ構成スクリプトファイル生成
     *
     * @param e ActionEvent
     */
    @FXML
    void createPacFile(ActionEvent e) {
        try {
            InternalFXMLLoader.showWindow("logbook/gui/create_pac_file.fxml", this.parentController.getWindow(), "自動プロキシ構成スクリプトファイル生成");
        } catch (Exception ex) {
            log.error("自動プロキシ構成スクリプトファイル生成の初期化に失敗しました", ex);
        }
    }

    /**
     * 設定
     *
     * @param e ActionEvent
     */
    @FXML
    void config(ActionEvent e) {
        try {
            InternalFXMLLoader.showWindow("logbook/gui/config.fxml", this.parentController.getWindow(), "設定");
        } catch (Exception ex) {
            log.error("設定の初期化に失敗しました", ex);
        }
    }

    /**
     * 更新を確認
     *
     * @param e ActionEvent
     */
    @FXML
    void updateCheck(ActionEvent e) {
        try {
            CheckUpdate.getInstance().run(this.parentController.getWindow());
        } catch (Exception ex) {
            log.error("更新情報の取得に失敗しました", ex);
        }
    }

    /**
     * バージョン情報
     *
     * @param e ActionEvent
     */
    @FXML
    void version(ActionEvent e) {
        try {
            InternalFXMLLoader.showWindow("logbook/gui/version.fxml", this.parentController.getWindow(), "バージョン情報",
                    root -> new Scene(root, Color.TRANSPARENT),
                    null,
                    stage -> {
                        stage.initStyle(StageStyle.TRANSPARENT);
                        stage.focusedProperty().addListener((ob, o, n) -> {
                            if (!n) {
                                stage.close();
                            }
                        });
                    });
        } catch (Exception ex) {
            log.error("設定の初期化に失敗しました", ex);
        }
    }

    /**
     * プラグインのMenuItemを追加します
     *
     * @param serviceClass サービスプロバイダインターフェイス
     * @param items MenuItemの追加先
     */
    private <S extends Plugin<MenuItem>> void addMenuItem(Class<S> serviceClass, ObservableList<MenuItem> items) {
        List<MenuItem> addItem = Plugin.getContent(serviceClass);
        if (!addItem.isEmpty()) {
            items.add(new SeparatorMenuItem());
            addItem.forEach(items::add);
        }
    }
}
