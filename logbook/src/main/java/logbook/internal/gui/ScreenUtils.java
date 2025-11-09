package logbook.internal.gui;

import javafx.collections.ObservableList;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import logbook.bean.WindowLocation;

/**
 * ディスプレイ関連のユーティリティクラス
 */
public final class ScreenUtils {

    private ScreenUtils() {
        // インスタンス化を防ぐ
    }

    /**
     * 指定された位置が有効なディスプレイ内にあるかどうかを確認します。
     * @param x X座標
     * @param y Y座標
     * @return 位置が有効なディスプレイ内にある場合true
     */
    public static boolean isLocationValid(double x, double y) {
        return Screen.getScreens().stream()
                .anyMatch(screen -> screen.getVisualBounds().contains(x, y));
    }

    /**
     * 指定された位置情報が有効なディスプレイ内にあるかどうかを確認します。
     * @param location 位置情報（nullの場合はfalseを返す）
     * @return 位置が有効なディスプレイ内にある場合true
     */
    public static boolean isLocationValid(WindowLocation location) {
        if (location == null) {
            return false;
        }
        return isLocationValid(location.getX(), location.getY());
    }

    /**
     * 指定された位置を含むディスプレイを検索します。
     * @param x X座標
     * @param y Y座標
     * @return 位置を含むディスプレイ、見つからない場合はnull
     */
    public static Screen findScreenContaining(double x, double y) {
        return Screen.getScreens().stream()
                .filter(screen -> screen.getVisualBounds().contains(x, y))
                .findFirst()
                .orElse(null);
    }

    /**
     * 指定された位置情報を含むディスプレイを検索します。
     * @param location 位置情報（nullの場合はnullを返す）
     * @return 位置を含むディスプレイ、見つからない場合またはlocationがnullの場合はnull
     */
    public static Screen findScreenContaining(WindowLocation location) {
        if (location == null) {
            return null;
        }
        return findScreenContaining(location.getX(), location.getY());
    }

    /**
     * プライマリディスプレイの表示可能範囲を取得します。
     * @return プライマリディスプレイの表示可能範囲
     */
    public static Rectangle2D getPrimaryScreenBounds() {
        ObservableList<Screen> screens = Screen.getScreens();
        Screen primaryScreen = Screen.getPrimary();
        return primaryScreen != null 
                ? primaryScreen.getVisualBounds() 
                : screens.get(0).getVisualBounds();
    }
}

