package logbook.internal.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.controlsfx.control.SegmentedButton;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import logbook.bean.DeckPort;
import logbook.bean.DeckPortCollection;
import logbook.bean.Maparea;
import logbook.bean.MapareaCollection;
import logbook.bean.Mission;
import logbook.bean.MissionCollection;
import logbook.bean.MissionCondition;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.bean.Stype;
import logbook.bean.StypeCollection;
import logbook.internal.Missions;
import logbook.internal.gui.MissionConditionNode.IconState;
import logbook.plugin.PluginServices;
import lombok.extern.slf4j.Slf4j;

/**
 * 遠征条件確認（条件木共有・艦隊切替で再評価・Timeline 監視）
 */
@Slf4j
public class MissionCheck extends WindowController {

    /** クライアント遠征画面と同じ通常海域の表示順（イベント海域は含まない） */
    private static final List<Integer> MAPAREA_DISPLAY_ORDER = List.of(1, 2, 3, 7, 4, 5, 6);

    private SegmentedButton fleet;

    private TreeView<MissionConditionNode> conditionTree;

    /** 全艦隊で共有する条件木と評価対象 */
    private SharedContent sharedContent;

    private Timeline timeline;

    private int fleetHashCode;

    /**
     * ウインドウを開く（FXML なし）
     *
     * @param parent 親ウインドウ
     * @throws IOException アイコン読み込みなどで入出力例外が発生した場合
     */
    public static void open(Stage parent) throws IOException {
        MissionCheck controller = new MissionCheck();
        Parent root = InternalFXMLLoader.setGlobal(controller.createRoot());
        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        controller.initWindow(stage);
        stage.initOwner(parent);
        stage.setTitle("遠征条件確認");
        Tools.Windows.setIcon(stage);
        Tools.Windows.defaultCloseAction(controller);
        Tools.Windows.defaultOpenAction(controller);
        // ツリー構築を show 前に済ませ、空ウインドウが一瞬出るのを防ぐ
        controller.start();
        stage.show();
    }

    private Parent createRoot() {
        this.fleet = new SegmentedButton();
        HBox fleetBar = new HBox(this.fleet);
        VBox.setMargin(fleetBar, new Insets(3));

        this.conditionTree = new TreeView<>();
        this.conditionTree.setShowRoot(false);
        VBox.setVgrow(this.conditionTree, Priority.ALWAYS);

        VBox root = new VBox(fleetBar, this.conditionTree);
        root.setPrefSize(600, 400);
        Optional.ofNullable(PluginServices.getResource("logbook/gui/application.css"))
                .map(java.net.URL::toExternalForm)
                .ifPresent(root.getStylesheets()::add);
        return root;
    }

    private void start() {
        this.conditionTree.setCellFactory(tv -> new MissionConditionTreeCell());
        this.sharedContent = this.buildSharedContent();
        this.conditionTree.setRoot(this.sharedContent.root);

        for (DeckPort deck : DeckPortCollection.get().getDeckPortMap().values()) {
            ToggleButton button = new ToggleButton(deck.getName());
            button.setUserData(deck.getId());
            this.fleet.getButtons().add(button);
        }
        this.fleet.getToggleGroup().selectedToggleProperty().addListener((ob, o, n) -> this.showSelectedFleet(n));

        this.timeline = new Timeline();
        this.timeline.setCycleCount(Timeline.INDEFINITE);
        this.timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1), this::onTimeline));

        this.fleet.getButtons().stream()
                .skip(1)
                .findFirst()
                .ifPresent(b -> b.setSelected(true));

        this.timeline.play();
    }

    private void showSelectedFleet(Toggle toggle) {
        if (toggle == null) {
            this.fleetHashCode = 0;
            return;
        }
        Integer deckId = (Integer) toggle.getUserData();
        this.fleetHashCode = this.computeFleetHash(deckId);
        this.sharedContent.applyFleet(this.shipsOf(deckId));
    }

    private void onTimeline(ActionEvent e) {
        Toggle toggle = this.fleet.getToggleGroup().getSelectedToggle();
        if (toggle == null) {
            return;
        }
        Integer deckId = (Integer) toggle.getUserData();
        int hash = this.computeFleetHash(deckId);
        if (hash == this.fleetHashCode) {
            return;
        }
        this.fleetHashCode = hash;
        this.sharedContent.applyFleet(this.shipsOf(deckId));
    }

    private int computeFleetHash(Integer deckId) {
        DeckPort port = DeckPortCollection.get().getDeckPortMap().get(deckId);
        if (port == null) {
            return 0;
        }
        List<Ship> ships = this.shipsOf(deckId);
        return Objects.hash(port.getShip(), ships);
    }

    private List<Ship> shipsOf(Integer deckId) {
        Map<Integer, Ship> shipMap = ShipCollection.get().getShipMap();
        DeckPort port = DeckPortCollection.get().getDeckPortMap().get(deckId);
        if (port == null) {
            return List.of();
        }
        return port.getShip().stream()
                .map(shipMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private SharedContent buildSharedContent() {
        TreeItem<MissionConditionNode> root = new TreeItem<>(new MissionConditionNode());
        List<MissionEval> evals = new ArrayList<>();

        Map<Integer, List<Mission>> missionMap = MissionCollection.get().getMissionMap().values().stream()
                .sorted(Comparator.comparing(Mission::getMapareaId, MissionCheck::compareMapareaId)
                        .thenComparing(Mission::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.groupingBy(Mission::getMapareaId, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<Integer, List<Mission>> missionEntry : missionMap.entrySet()) {
            List<Mission> missions = missionEntry.getValue();
            Integer mapareaId = missions.get(0).getMapareaId();
            String area = Optional.ofNullable(MapareaCollection.get().getMaparea().get(mapareaId))
                    .filter(map -> map.getId() != null && map.getId() <= 40)
                    .map(Maparea::getName)
                    .orElse("イベント海域");

            MissionConditionNode areaNode = new MissionConditionNode();
            areaNode.setText(area);
            TreeItem<MissionConditionNode> areaItem = new TreeItem<>(areaNode);
            areaItem.setExpanded(true);

            for (Mission mission : missions) {
                MissionEval eval = this.buildMission(mission);
                if (eval != null) {
                    areaItem.getChildren().add(eval.missionItem);
                    evals.add(eval);
                }
            }
            if (!areaItem.getChildren().isEmpty()) {
                root.getChildren().add(areaItem);
            }
        }

        return new SharedContent(root, evals);
    }

    /**
     * 海域タブの並び。通常海域はクライアント順、それ以外（イベント等）は従来どおり id 昇順で後ろ。
     */
    private static int compareMapareaId(Integer a, Integer b) {
        return Integer.compare(mapareaSortKey(a), mapareaSortKey(b));
    }

    private static int mapareaSortKey(Integer mapareaId) {
        if (mapareaId == null) {
            return Integer.MAX_VALUE;
        }
        int index = MAPAREA_DISPLAY_ORDER.indexOf(mapareaId);
        if (index >= 0) {
            return index;
        }
        return MAPAREA_DISPLAY_ORDER.size() + mapareaId;
    }

    private MissionEval buildMission(Mission mission) {
        try {
            Optional<MissionCondition> conditionOpt = Missions.getMissionCondition(mission.getId());
            if (conditionOpt.isEmpty() && mission.getSampleFleet() == null) {
                return null;
            }

            MissionEval eval = new MissionEval();

            TreeItem<MissionConditionNode> missionItem;
            if (conditionOpt.isPresent()) {
                MissionCondition cond = conditionOpt.get();
                if (cond.getGreatSuccessCondition() == null) {
                    cond.setGreatSuccessCondition(createDefaultGreatSuccessCondition());
                }
                eval.rootCondition = cond;
                LeafBuild leaf = this.buildLeaf(cond, eval);
                missionItem = leaf.item;
                // 遠征名は Mission 固定。根ノードの文言は艦隊再評価で上書きしない
                for (FleetBinding b : eval.bindings) {
                    if (b instanceof ConditionBinding cb) {
                        if (cb.condition == cond) {
                            cb.updateText = false;
                            break;
                        }
                    }
                }
            } else {
                MissionConditionNode node = new MissionConditionNode();
                node.setIcon(IconState.UNKNOWN);
                missionItem = new TreeItem<>(node);
            }

            MissionConditionNode missionNode = missionItem.getValue();
            missionNode.setText(mission.toString() + " [" + Missions.getDurationText(mission) + "]");
            if (mission.getDamageType() != null && mission.getDamageType().intValue() > 0) {
                missionNode.setDamageType(mission.getDamageType());
            }

            if (mission.getSampleFleet() != null) {
                MissionConditionNode sampleNode = new MissionConditionNode();
                sampleNode.setText("サンプル編成");
                sampleNode.setIcon(IconState.INFO);
                TreeItem<MissionConditionNode> sample = new TreeItem<>(sampleNode);
                for (Integer type : mission.getSampleFleet()) {
                    Optional.ofNullable(StypeCollection.get().getStypeMap().get(type))
                            .map(Stype::getName)
                            .ifPresent(name -> {
                                MissionConditionNode stypeNode = new MissionConditionNode();
                                stypeNode.setText(name);
                                sample.getChildren().add(new TreeItem<>(stypeNode));
                            });
                }
                missionItem.getChildren().add(sample);
            }

            eval.missionItem = missionItem;
            return eval;
        } catch (Exception e) {
            log.error("遠征確認画面で例外", e);
            return null;
        }
    }

    private LeafBuild buildLeaf(MissionCondition condition, MissionEval eval) {
        MissionConditionNode node = new MissionConditionNode();
        TreeItem<MissionConditionNode> item = new TreeItem<>(node);

        ConditionBinding binding = new ConditionBinding(condition, node, false, null);
        eval.bindings.add(binding);

        Optional.ofNullable(condition.getGreatSuccessCondition())
                .ifPresent(gs -> {
                    MissionConditionNode gsNode = new MissionConditionNode();
                    TreeItem<MissionConditionNode> gsItem = new TreeItem<>(gsNode);
                    LeafBuild gsLeaf = this.buildLeaf(gs, eval);
                    gsItem.getChildren().add(gsLeaf.item);
                    item.getChildren().add(gsItem);
                    eval.bindings.add(new GreatSuccessBinding(condition, gs, gsNode, node));
                });

        if (condition.getConditions() != null) {
            if (condition.getConditions().size() == 1 && !condition.getOperator().startsWith("N")) {
                MissionCondition only = condition.getConditions().get(0);
                binding.flattened = true;
                binding.flattenSource = only;
            } else {
                for (MissionCondition subcondition : condition.getConditions()) {
                    LeafBuild sub = this.buildLeaf(subcondition, eval);
                    item.getChildren().add(sub.item);
                }
            }
        }
        return new LeafBuild(item);
    }

    private static String damageTypeLabel(Integer damageType) {
        switch (damageType) {
        case 1:
            return "交戦型";
        case 2:
            return "交戦II型";
        default:
            return "交戦型(" + damageType + ")";
        }
    }

    private static MissionCondition createDefaultGreatSuccessCondition() {
        MissionCondition success = new MissionCondition();
        success.setType("艦隊");
        success.setCountType("キラキラ");
        success.setValue(6);
        return success;
    }

    @Override
    protected void onWindowHidden(WindowEvent e) {
        if (this.timeline != null) {
            this.timeline.stop();
        }
    }

    /**
     * 全艦隊で共有するツリーと評価対象
     */
    private static final class SharedContent {
        private final TreeItem<MissionConditionNode> root;
        private final List<MissionEval> evals;

        private SharedContent(TreeItem<MissionConditionNode> root, List<MissionEval> evals) {
            this.root = root;
            this.evals = evals;
        }

        private void applyFleet(List<Ship> fleet) {
            for (MissionEval eval : this.evals) {
                eval.apply(fleet);
            }
        }
    }

    /**
     * 1 遠征分の条件木と UI バインド
     */
    private static final class MissionEval {
        private MissionCondition rootCondition;
        private TreeItem<MissionConditionNode> missionItem;
        private final List<FleetBinding> bindings = new ArrayList<>();

        private void apply(List<Ship> fleet) {
            if (this.rootCondition != null) {
                this.rootCondition.test(new ArrayList<>(fleet));
                MissionCondition gs = this.rootCondition.getGreatSuccessCondition();
                if (gs != null) {
                    gs.test(new ArrayList<>(fleet));
                }
            }
            for (FleetBinding binding : this.bindings) {
                binding.apply();
            }
        }
    }

    private interface FleetBinding {
        void apply();
    }

    /**
     * 通常条件ノード（文言・成否アイコン）
     */
    private static final class ConditionBinding implements FleetBinding {
        private final MissionCondition condition;
        private final MissionConditionNode node;
        private boolean flattened;
        private MissionCondition flattenSource;
        /** false のとき文言は更新しない（遠征タイトルなど Mission 固定） */
        private boolean updateText = true;

        private ConditionBinding(MissionCondition condition, MissionConditionNode node, boolean flattened,
                MissionCondition flattenSource) {
            this.condition = condition;
            this.node = node;
            this.flattened = flattened;
            this.flattenSource = flattenSource;
        }

        @Override
        public void apply() {
            if (this.updateText) {
                if (this.flattened && this.flattenSource != null) {
                    this.node.setText(this.flattenSource.toString());
                } else {
                    this.node.setText(this.condition.toString());
                }
            }
            this.node.setIconFromResult(this.condition.getResult());
        }
    }

    /**
     * 大成功行ラベルと親アイコン（オレンジ成功）
     */
    private static final class GreatSuccessBinding implements FleetBinding {
        private final MissionCondition parent;
        private final MissionCondition greatSuccess;
        private final MissionConditionNode labelNode;
        private final MissionConditionNode parentNode;

        private GreatSuccessBinding(MissionCondition parent, MissionCondition greatSuccess,
                MissionConditionNode labelNode, MissionConditionNode parentNode) {
            this.parent = parent;
            this.greatSuccess = greatSuccess;
            this.labelNode = labelNode;
            this.parentNode = parentNode;
        }

        @Override
        public void apply() {
            Boolean parentOk = this.parent.getResult();
            Boolean gsOk = this.greatSuccess.getResult();
            if (Boolean.TRUE.equals(parentOk)) {
                if (Boolean.TRUE.equals(gsOk)) {
                    this.labelNode.setText("大成功");
                    this.labelNode.setIcon(IconState.SUCCESS);
                } else {
                    this.labelNode.setText("成功");
                    this.labelNode.setIcon(IconState.SUCCESS_ORANGE);
                    this.parentNode.setIcon(IconState.SUCCESS_ORANGE);
                }
            } else {
                this.labelNode.setText("失敗");
                this.labelNode.setIcon(IconState.FAILURE);
            }
        }
    }

    private static final class LeafBuild {
        private final TreeItem<MissionConditionNode> item;

        private LeafBuild(TreeItem<MissionConditionNode> item) {
            this.item = item;
        }
    }

    /**
     * text / icon / 交戦型アイコンを購読する TreeCell。
     * レイアウト: [成否アイコン] [文言] [交戦型アイコン]
     */
    private static final class MissionConditionTreeCell extends TreeCell<MissionConditionNode> {

        private final InvalidationListener syncListener = ob -> this.syncContent();
        private MissionConditionNode bound;

        @Override
        protected void updateItem(MissionConditionNode item, boolean empty) {
            this.unbind();
            super.updateItem(item, empty);
            if (empty || item == null) {
                this.setText(null);
                this.setGraphic(null);
                return;
            }
            this.bound = item;
            item.textProperty().addListener(this.syncListener);
            item.iconProperty().addListener(this.syncListener);
            item.damageTypeProperty().addListener(this.syncListener);
            this.syncContent();
        }

        private void unbind() {
            if (this.bound != null) {
                this.bound.textProperty().removeListener(this.syncListener);
                this.bound.iconProperty().removeListener(this.syncListener);
                this.bound.damageTypeProperty().removeListener(this.syncListener);
                this.bound = null;
            }
        }

        private void syncContent() {
            if (this.bound == null) {
                return;
            }
            this.setText(null);
            HBox box = new HBox(4);
            box.setAlignment(Pos.CENTER_LEFT);
            Node status = this.bound.createGraphic();
            if (status != null) {
                box.getChildren().add(status);
            }
            Label label = new Label(this.bound.getText());
            box.getChildren().add(label);
            Integer damageType = this.bound.getDamageType();
            if (damageType != null && damageType.intValue() > 0) {
                ImageView image = new ImageView(Missions.damageTypeIcon(damageType));
                image.setFitWidth(18);
                image.setFitHeight(18);
                box.getChildren().add(image);
                box.getChildren().add(new Label(damageTypeLabel(damageType)));
            }
            this.setGraphic(box);
        }
    }
}
