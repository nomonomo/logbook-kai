package logbook.internal.gui;

import java.io.IOException;
import java.util.Optional;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import logbook.bean.Ship;
import logbook.bean.ShipCollection;
import logbook.bean.ShipMst;
import logbook.internal.LoggerHolder;
import logbook.internal.Ships;

/**
 * 艦隊タブの艦娘
 *
 */
public class FleetTabShipPane extends HBox {

    private Ship ship;

    @FXML
    private ImageView img;

    @FXML
    private Label name;

    @FXML
    private Label level;

    @FXML
    private Label hp;

    @FXML
    private ImageView supply;

    @FXML
    private Label cond;

    /**
     * 艦隊タブのコンストラクタ
     *
     * @param ship 艦娘
     */
    public FleetTabShipPane(Ship ship) {
        this.ship = ship;
        try {
            FXMLLoader loader = InternalFXMLLoader.load("logbook/gui/fleet_ship.fxml");
            loader.setRoot(this);
            loader.setController(this);
            loader.load();
        } catch (IOException e) {
            LoggerHolder.get().error("FXMLのロードに失敗しました", e);
        }
    }

    @FXML
    void initialize() {
        UiScale.applyShipBanner(this.img);
        this.img.setImage(Ships.shipWithItemImage(this.ship));

        Optional<ShipMst> mst = Ships.shipMst(this.ship);

        // 名前
        String name = mst.map(ShipMst::getName)
                .orElse("");
        this.name.setText(name);
        this.level.setText("(Lv" + this.ship.getLv() + ")");
        this.hp.setText(this.ship.getNowhp() + "/" + this.ship.getMaxhp());
        this.supply.setImage(Ships.supplyGaugeImage(this.ship));
        this.cond.setText(this.ship.getCond() + "cond.");
        if (this.ship.getNowhp() == this.ship.getMaxhp()) {
            this.hp.getStyleClass().add("hp-max");
        }
        ObservableList<String> styleClass = this.cond.getStyleClass();
        styleClass.clear();
        styleClass.add("label");
        styleClass.add("cond");
        if (Ships.isDeepGreen(this.ship)) {
            styleClass.add("deepgreen");
        } else if (Ships.isGreen(this.ship)) {
            styleClass.add("green");
        } else if (Ships.isOrange(this.ship)) {
            styleClass.add("orange");
        } else if (Ships.isRed(this.ship)) {
            styleClass.add("red");
        }

        // マウスオーバーでのポップアップ。開いたときに母港の艦を引き直す
        int shipId = this.ship.getId() == null ? 0 : this.ship.getId();
        PopOver<Ship> popover = new PopOver<>((node, ship) -> {
            Ship current = ShipCollection.get().getShipMap().get(shipId);
            Ship shown = current != null ? current : ship;
            String title = Ships.shipMst(shown).map(ShipMst::getName).orElse(name);
            return new PopOverPane(title, new FleetTabShipPopup(shown));
        });
        popover.install(this, this.ship);
    }
}
