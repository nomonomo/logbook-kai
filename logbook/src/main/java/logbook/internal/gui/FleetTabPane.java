package logbook.internal.gui;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import logbook.bean.DeckPort;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.internal.Items;
import logbook.internal.LoggerHolder;
import logbook.internal.fleet.FleetShipRow;
import logbook.internal.fleet.FleetShipRows;
import logbook.internal.fleet.FleetSnapshot;
import logbook.plugin.gui.FleetTabRemark;
import logbook.plugin.gui.Plugin;
import logbook.plugin.gui.Updateable;
import lombok.val;

/**
 * 艦隊タブ
 *
 */
public class FleetTabPane extends ScrollPane {

    private static final PseudoClass ENABLE_BG = PseudoClass.getPseudoClass("enablebgimage");

    /** 表示値 */
    private final FleetModel model;

    /** 注釈プラグインへ渡す艦隊 */
    private DeckPort port;

    /** 分岐点係数 */
    private double branchCoefficient = 1;

    /** 前回描画した艦の行 */
    private List<FleetShipRow> rowState;

    /** メッセージ */
    @FXML
    private Label message;

    /** 艦娘達 */
    @FXML
    private VBox ships;

    /** 制空・判定式などの数値エリア */
    @FXML
    private GridPane fleetStats;

    /** 制空値アイコン */
    @FXML
    private ImageView airSuperiorityImg;

    /** 制空値 */
    @FXML
    private Label airSuperiority;

    /** 触接開始率アイコン */
    @FXML
    private ImageView touchPlaneStartProbabilityImg;

    /** 触接開始率 */
    @FXML
    private Label touchPlaneStartProbability;

    /** 判定式(33)アイコン */
    @FXML
    private ImageView decision33Img;

    /** 判定式(33) */
    @FXML
    private Label decision33;

    /** 分岐点係数ボタン */
    @FXML
    private Button branchCoefficientButton;

    /** 艦娘レベル計アイコン */
    @FXML
    private ImageView lvsumImg;

    /** 艦娘レベル計 */
    @FXML
    private Label lvsum;

    /** 疲労回復ラベル（アイコン無しのため左余白で揃える） */
    @FXML
    private Label condRecoverLabel;

    /** 疲労 */
    @FXML
    private Label cond;

    /** 火力合計アイコン */
    @FXML
    private ImageView karyokusumImg;

    /** 火力合計 */
    @FXML
    private Label karyokusum;

    /** 対空合計アイコン */
    @FXML
    private ImageView taikusumImg;

    /** 対空合計 */
    @FXML
    private Label taikusum;

    /** 対潜合計アイコン */
    @FXML
    private ImageView taissumImg;

    /** 対潜合計 */
    @FXML
    private Label taissum;

    /** 索敵合計アイコン */
    @FXML
    private ImageView sakutekisumImg;

    /** 索敵合計 */
    @FXML
    private Label sakutekisum;

    /** TP合計アイコン */
    @FXML
    private ImageView tpsumImg;

    /** TP合計 */
    @FXML
    private Label tpsum;

    /** 艦隊速度アイコン */
    @FXML
    private ImageView speedImg;

    /** 艦隊速度 */
    @FXML
    private Label speed;

    /** 注釈 */
    @FXML
    private VBox remark;

    /**
     * 艦隊ペインのコンストラクタ
     *
     * @param model 表示値
     */
    public FleetTabPane(FleetModel model) {
        this.model = model;
        try {
            FXMLLoader loader = InternalFXMLLoader.load("logbook/gui/fleet_tab.fxml");
            loader.setRoot(this);
            loader.setController(this);
            loader.load();
        } catch (IOException e) {
            LoggerHolder.get().error("FXMLのロードに失敗しました", e);
        }
    }

    @FXML
    void initialize() {
        this.applyFontScale();
        this.bind();
        this.setIcon();
        this.installDecision33Popover();
        this.initializeRemarkPlugin();
    }

    /**
     * 母港の内容をモデルへ反映し、変わった艦の行だけ作り直します。
     *
     * @param port 艦隊
     */
    public void refresh(DeckPort port) {
        this.port = port;
        FleetSnapshot snapshot = FleetSnapshot.of(port, this.branchCoefficient);
        boolean changed = this.model.apply(snapshot);
        if (this.rowState == null || changed || !snapshot.getRows().equals(this.rowState)) {
            this.syncRows();
            this.updateRemarkPlugin(port);
        }
    }

    /**
     * 分岐点係数を変更する
     *
     * @param event ActionEvent
     */
    @FXML
    void changeBranchCoefficient(ActionEvent event) {
        TextInputDialog dialog = new TextInputDialog(Double.toString(this.branchCoefficient));
        dialog.getDialogPane().getStylesheets().add("logbook/gui/application.css");
        InternalFXMLLoader.setGlobal(dialog.getDialogPane());
        dialog.initOwner(this.getScene().getWindow());
        dialog.setTitle("分岐点係数を変更");
        dialog.setHeaderText("分岐点係数を数値で入力してください 例)\n"
                + "沖ノ島沖 G,I,Jマス 係数: 1.0\n"
                + "北方AL海域 Gマス 係数: 4.0\n"
                + "中部海域哨戒線 G,Hマス 係数: 4.0\n"
                + "MS諸島沖 E,Iマス 係数: 3.0\n"
                + "グアノ環礁沖海域 Hマス 係数: 3.0");

        val result = dialog.showAndWait();
        if (result.isPresent()) {
            String value = result.get();
            if (!value.isEmpty()) {
                try {
                    this.branchCoefficient = Double.parseDouble(value);
                    if (this.port != null) {
                        this.refresh(this.port);
                    }
                } catch (NumberFormatException e) {
                }
            }
        }
    }

    private void bind() {
        this.message.textProperty().bind(this.model.nameProperty());
        this.airSuperiority.textProperty().bind(this.model.airSuperiorityProperty());
        this.touchPlaneStartProbability.textProperty().bind(this.model.touchPlaneStartProbabilityProperty());
        this.decision33.textProperty().bind(this.model.decision33Property());
        this.branchCoefficientButton.textProperty().bind(this.model.branchCoefficientProperty());
        this.lvsum.textProperty().bind(this.model.lvSumProperty());
        this.karyokusum.textProperty().bind(this.model.karyokuSumProperty());
        this.taikusum.textProperty().bind(this.model.taikuSumProperty());
        this.taissum.textProperty().bind(this.model.taisenSumProperty());
        this.sakutekisum.textProperty().bind(this.model.sakutekiSumProperty());
        this.tpsum.textProperty().bind(this.model.tpSumProperty());
        this.speed.textProperty().bind(this.model.speedProperty());
        this.speed.styleProperty().bind(this.model.speedStyleProperty());
        this.cond.textProperty().bind(this.model.condTextProperty());
        this.styleProperty().bind(this.model.backgroundStyleProperty());
        this.model.backgroundEnabledProperty().addListener((obs, prev, enabled) -> this.pseudoClassStateChanged(ENABLE_BG, enabled));
        this.pseudoClassStateChanged(ENABLE_BG, this.model.backgroundEnabledProperty().get());
    }

    private void installDecision33Popover() {
        PopOver<String> popover = new PopOver<>((node, data) -> new PopOverPane("判定式(33)", this.model.getDecision33Detail()));
        popover.install(this.decision33, "");
    }

    private void syncRows() {
        FleetSnapshot snapshot = this.model.getSnapshot();
        if (snapshot == null) {
            return;
        }
        List<FleetShipRow> rows = snapshot.getRows();
        List<Integer> changed = FleetShipRows.changedIndices(this.rowState, rows);
        Map<Integer, Ship> shipMap = ShipCollection.get().getShipMap();
        ObservableList<Node> children = this.ships.getChildren();
        while (children.size() > rows.size()) {
            children.remove(children.size() - 1);
        }
        for (int index : changed) {
            Ship ship = shipMap.get(rows.get(index).getShipId());
            if (ship == null) {
                continue;
            }
            FleetTabShipPane pane = new FleetTabShipPane(ship);
            if (index < children.size()) {
                children.set(index, pane);
            } else {
                while (children.size() < index) {
                    children.add(new javafx.scene.layout.Pane());
                }
                children.add(pane);
            }
        }
        this.rowState = rows;
    }

    private void setIcon() {
        this.airSuperiorityImg.setImage(Items.itemImageByType(6));
        this.touchPlaneStartProbabilityImg.setImage(Items.itemImageByType(10));
        this.decision33Img.setImage(Items.itemImageByType(9));
        this.lvsumImg.setImage(Items.itemImageByType(28));
        this.karyokusumImg.setImage(Items.itemImageByType(3));
        this.taikusumImg.setImage(Items.itemImageByType(15));
        this.taissumImg.setImage(Items.itemImageByType(17));
        this.sakutekisumImg.setImage(Items.itemImageByType(11));
        this.tpsumImg.setImage(Items.itemImageByType(25));
        this.speedImg.setImage(Items.itemImageByType(19));
    }

    /**
     * 制空・判定式エリアのアイコンと左余白を文字サイズ倍率に合わせる。
     */
    private void applyFontScale() {
        double icon = UiScale.FLEET_STAT_ICON_SIZE;
        UiScale.applyFit(this.airSuperiorityImg, icon);
        UiScale.applyFit(this.touchPlaneStartProbabilityImg, icon);
        UiScale.applyFit(this.decision33Img, icon);
        UiScale.applyFit(this.lvsumImg, icon);
        UiScale.applyFit(this.karyokusumImg, icon);
        UiScale.applyFit(this.taikusumImg, icon);
        UiScale.applyFit(this.taissumImg, icon);
        UiScale.applyFit(this.sakutekisumImg, icon);
        UiScale.applyFit(this.tpsumImg, icon);
        UiScale.applyFit(this.speedImg, icon);
        UiScale.applyLeftPadding(this.condRecoverLabel, icon);
        if (this.fleetStats != null) {
            UiScale.applyFleetStatsGrid(this.fleetStats);
        }
    }

    /**
     * 注釈プラグインの初期化
     */
    private void initializeRemarkPlugin() {
        for (Updateable<DeckPort> plugin : Plugin.getContent(FleetTabRemark.class)) {
            Node node;
            if (plugin instanceof Node) {
                node = ((Node) plugin);
            } else {
                node = new RemarkLabel(plugin);
            }
            this.remark.getChildren().add(node);
        }
    }

    /**
     * 注釈プラグインを更新する
     *
     * @param port 艦隊
     */
    @SuppressWarnings("unchecked")
    private void updateRemarkPlugin(DeckPort port) {
        for (Node node : this.remark.getChildren()) {
            if (node instanceof Updateable) {
                ((Updateable<DeckPort>) node).updateItem(port);
            }
        }
    }

    /**
     * Labelを使う注釈の実装
     */
    private static class RemarkLabel extends Label implements Updateable<DeckPort> {

        private Updateable<DeckPort> plugin;

        public RemarkLabel(Updateable<DeckPort> plugin) {
            this.plugin = plugin;
            this.setText(this.plugin.toString());
        }

        @Override
        public void updateItem(DeckPort item) {
            this.plugin.updateItem(item);
            this.setText(this.plugin.toString());
        }
    }
}
