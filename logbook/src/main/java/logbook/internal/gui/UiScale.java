package logbook.internal.gui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import logbook.bean.AppConfig;

/**
 * 文字サイズ設定に連動する UI 寸法の解決と適用。
 */
public final class UiScale {

    private static final int BANNER_WIDTH_DEFAULT = 160;
    private static final int BANNER_HEIGHT_DEFAULT = 40;
    private static final int BANNER_WIDTH_LARGE1 = 200;
    private static final int BANNER_HEIGHT_LARGE1 = 50;
    private static final int BANNER_WIDTH_LARGE2 = 240;
    private static final int BANNER_HEIGHT_LARGE2 = 60;
    private static final double INFO_COLUMN_WIDTH_DEFAULT = 100;
    private static final double SUPPLY_WIDTH_DEFAULT = 36;
    private static final double SUPPLY_HEIGHT_DEFAULT = 12;
    private static final double FLEET_STATS_MIN = 10;

    /** 艦隊タブの制空・判定式などのアイコンと、アイコン無し行の左余白 */
    static final double FLEET_STAT_ICON_SIZE = 24;
    /** 所有艦娘の艦種チェック折り返し幅（標準時） */
    static final double SHIP_TYPE_WRAP_WIDTH = 370;
    /** 所有艦娘のテキスト欄幅（標準時） */
    static final double SHIP_TEXT_FIELD_WIDTH = 200;

    private UiScale() {
    }

    /**
     * 文字サイズ設定の倍率。標準は 1.0。
     *
     * @param fontSize AppConfig の fontSize
     * @return 標準 1.0、少し大きい 1.2、大きい 1.3
     */
    static double sizeFactor(String fontSize) {
        if ("large2".equals(fontSize)) {
            return 1.3;
        }
        if ("large1".equals(fontSize)) {
            return 1.2;
        }
        return 1.0;
    }

    /**
     * 艦娘バナーの表示幅。標準 160、少し大きい 200、大きい 240。
     *
     * @param fontSize AppConfig の fontSize
     * @return ピクセル幅
     */
    static int bannerWidth(String fontSize) {
        if ("large2".equals(fontSize)) {
            return BANNER_WIDTH_LARGE2;
        }
        if ("large1".equals(fontSize)) {
            return BANNER_WIDTH_LARGE1;
        }
        return BANNER_WIDTH_DEFAULT;
    }

    /**
     * 艦娘バナーの表示高さ。標準 40、少し大きい 50、大きい 60。
     *
     * @param fontSize AppConfig の fontSize
     * @return ピクセル高さ
     */
    static int bannerHeight(String fontSize) {
        if ("large2".equals(fontSize)) {
            return BANNER_HEIGHT_LARGE2;
        }
        if ("large1".equals(fontSize)) {
            return BANNER_HEIGHT_LARGE1;
        }
        return BANNER_HEIGHT_DEFAULT;
    }

    /**
     * バナー右の情報列幅。標準 100 に倍率を掛ける。
     *
     * @param fontSize AppConfig の fontSize
     * @return ピクセル幅
     */
    static double infoColumnWidth(String fontSize) {
        return INFO_COLUMN_WIDTH_DEFAULT * sizeFactor(fontSize);
    }

    /**
     * 補給ゲージの表示幅。標準 36 に倍率を掛ける。
     *
     * @param fontSize AppConfig の fontSize
     * @return ピクセル幅
     */
    static double supplyWidth(String fontSize) {
        return SUPPLY_WIDTH_DEFAULT * sizeFactor(fontSize);
    }

    /**
     * 補給ゲージの表示高さ。標準 12 に倍率を掛ける。
     *
     * @param fontSize AppConfig の fontSize
     * @return ピクセル高さ
     */
    static double supplyHeight(String fontSize) {
        return SUPPLY_HEIGHT_DEFAULT * sizeFactor(fontSize);
    }

    /**
     * 標準ピクセル値に文字サイズ倍率を掛ける。
     *
     * @param base 標準時のピクセル
     * @param fontSize AppConfig の fontSize
     * @return 倍率後のピクセル
     */
    static double scaledPx(double base, String fontSize) {
        return base * sizeFactor(fontSize);
    }

    /**
     * ImageView の fit を標準サイズ×文字サイズ倍率にする。
     *
     * @param view 対象
     * @param baseSize 標準時の幅・高さ
     */
    static void applyFit(ImageView view, double baseSize) {
        if (view == null) {
            return;
        }
        double size = scaledPx(baseSize, AppConfig.get().getFontSize());
        view.setFitWidth(size);
        view.setFitHeight(size);
    }

    /**
     * Region の prefWidth を標準値×文字サイズ倍率にする。
     *
     * @param region 対象
     * @param base 標準時の幅
     */
    static void applyPrefWidth(Region region, double base) {
        if (region == null) {
            return;
        }
        region.setPrefWidth(scaledPx(base, AppConfig.get().getFontSize()));
    }

    /**
     * 左余白を標準値×文字サイズ倍率にする（アイコン列と揃える）。
     *
     * @param region 対象
     * @param baseLeft 標準時の左余白
     */
    static void applyLeftPadding(Region region, double baseLeft) {
        if (region == null) {
            return;
        }
        double left = scaledPx(baseLeft, AppConfig.get().getFontSize());
        region.setPadding(new Insets(0, 0, 0, left));
    }

    /**
     * 制空・判定式グリッドの列・行 min を標準 10×文字サイズ倍率にする。
     *
     * @param grid 対象
     */
    static void applyFleetStatsGrid(GridPane grid) {
        if (grid == null) {
            return;
        }
        double min = scaledPx(FLEET_STATS_MIN, AppConfig.get().getFontSize());
        for (ColumnConstraints column : grid.getColumnConstraints()) {
            column.setMinWidth(min);
        }
        for (RowConstraints row : grid.getRowConstraints()) {
            row.setMinHeight(min);
        }
    }

    /**
     * 艦娘バナーの表示サイズと、同じ行の情報列・補給ゲージを現在の文字サイズ設定に合わせる。
     *
     * @param view バナー用 ImageView
     */
    static void applyShipBanner(ImageView view) {
        if (view == null) {
            return;
        }
        String fontSize = AppConfig.get().getFontSize();
        int width = bannerWidth(fontSize);
        int height = bannerHeight(fontSize);
        view.setFitWidth(width);
        view.setFitHeight(height);

        Parent parent = view.getParent();
        if (!(parent instanceof Region bannerPane)) {
            return;
        }
        bannerPane.setPrefSize(width, height);
        bannerPane.setMinSize(width, height);
        bannerPane.setMaxWidth(width);

        Parent row = bannerPane.getParent();
        if (!(row instanceof HBox hbox)) {
            return;
        }
        double infoWidth = infoColumnWidth(fontSize);
        for (Node child : hbox.getChildren()) {
            if (child != bannerPane && child instanceof Region info) {
                // min は子（艦名）に任せる。固定すると戦闘ログなどで名前が切れる
                info.setMinWidth(Region.USE_COMPUTED_SIZE);
                info.setPrefWidth(infoWidth);
                info.setPrefHeight(height);
                info.setMinHeight(height);
            }
        }
        applySupplyGauge(hbox, fontSize);
    }

    /**
     * 行内の補給ゲージだけを拡縮する（他画面の .supply には触れない）。
     */
    private static void applySupplyGauge(Node node, String fontSize) {
        if (node instanceof ImageView view && view.getStyleClass().contains("supply")) {
            view.setFitWidth(supplyWidth(fontSize));
            view.setFitHeight(supplyHeight(fontSize));
            return;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applySupplyGauge(child, fontSize);
            }
        }
    }
}
