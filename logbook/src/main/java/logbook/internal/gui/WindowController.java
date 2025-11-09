package logbook.internal.gui;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import logbook.bean.AppConfig;
import logbook.bean.WindowLocation;

/**
 * ウインドウを持つコントローラー
 *
 */
public abstract class WindowController {

    /** このコントローラーに紐づくウインドウ */
    private Stage window;

    /**
     * このコントローラーに紐づくウインドウを取得します。
     * @return このコントローラーに紐づくウインドウ
     */
    public Stage getWindow() {
        return this.window;
    }

    /**
     * このコントローラーに紐づくウインドウを設定します。
     * @param window このコントローラーに紐づくウインドウ
     */
    public void setWindow(Stage window) {
        this.window = window;
    }

    /**
     * ウインドウの位置とサイズを設定します。
     * @param location ウインドウの位置とサイズ
     */
    public void setWindowLocation(WindowLocation location) {
        if (location != null && ScreenUtils.isLocationValid(location)) {
            this.window.setX(location.getX());
            this.window.setY(location.getY());
            this.window.setWidth(location.getWidth());
            this.window.setHeight(location.getHeight());
        }
    }

    /**
     * ウインドウの位置とサイズを取得します。
     * @return ウインドウの位置とサイズ
     */
    public WindowLocation getWindowLocation() {
        WindowLocation location = new WindowLocation();
        location.setX(this.window.getX());
        location.setY(this.window.getY());
        location.setWidth(this.window.getWidth());
        location.setHeight(this.window.getHeight());
        return location;
    }

    /**
     * ディスプレイ構成変更後にウィンドウの位置とサイズを検証・修正します。
     * ウィンドウが無効な位置にある場合は有効なディスプレイに移動し、
     * サイズがディスプレイサイズを超えている場合は調整します。
     * 
     * 位置情報の優先順位: 1. 起動時に読み込んだ元の位置情報（originalLocation）、
     * 2. 最後に有効だった位置（lastValidLocation）、3. AppConfigに保存された位置
     * 
     * @param originalLocation 起動時に読み込んだ元の位置情報（nullの場合は無視）
     * @param lastValidLocation 最後に有効だった位置情報（nullの場合は無視）
     * @param configKey AppConfigから位置情報を取得する際のキー（nullの場合は無視）
     */
    public void validateAndFixWindowIfNeeded(WindowLocation originalLocation, 
            WindowLocation lastValidLocation, String configKey) {
        if (this.window == null) {
            return;
        }

        // 保存されたウィンドウ位置情報を取得（リモートデスクトップ接続後に元の位置を復元するため）
        WindowLocation savedLocation = null;
        if (originalLocation != null && ScreenUtils.isLocationValid(originalLocation)) {
            // 起動時に読み込んだ元の位置情報が、現在のディスプレイ構成で有効な場合
            savedLocation = originalLocation;
        }
        
        if (savedLocation == null && lastValidLocation != null) {
            savedLocation = lastValidLocation;
        }
        
        if (savedLocation == null && configKey != null) {
            savedLocation = AppConfig.get().getWindowLocationMap().get(configKey);
        }

        // ウィンドウの位置とサイズを検証・修正
        validateAndFixWindowBounds(savedLocation);
    }

    /**
     * ディスプレイ構成変更後にウィンドウの位置とサイズを検証・修正します。
     * ウィンドウが無効な位置にある場合は有効なディスプレイに移動し、
     * サイズがディスプレイサイズを超えている場合は調整します。
     * 
     * @param savedLocation 保存されたウィンドウ位置情報（nullの場合は無視）
     */
    public void validateAndFixWindowBounds(WindowLocation savedLocation) {
        if (this.window == null) {
            return;
        }

        var screens = Screen.getScreens();
        if (screens.isEmpty()) {
            return;
        }

        final double initialX = this.window.getX();
        final double initialY = this.window.getY();
        double windowWidth = this.window.getWidth();
        double windowHeight = this.window.getHeight();

        // 保存された位置が有効なディスプレイ内にあるか確認
        Screen savedLocationScreen = ScreenUtils.findScreenContaining(savedLocation);
        
        // 現在の位置が有効なディスプレイ内にあるか確認
        Screen containingScreen = ScreenUtils.findScreenContaining(initialX, initialY);

        Rectangle2D targetBounds;
        double windowX;
        double windowY;
        
        // 優先順位: 1. 保存された位置（有効な場合）、2. 現在の位置（有効な場合）、3. プライマリディスプレイ
        if (savedLocationScreen != null) {
            // 保存された位置が有効な場合、その位置に復元
            targetBounds = savedLocationScreen.getVisualBounds();
            windowX = savedLocation.getX();
            windowY = savedLocation.getY();
            windowWidth = savedLocation.getWidth();
            windowHeight = savedLocation.getHeight();
        } else if (containingScreen != null) {
            // 現在の位置が有効な場合、現在の位置を維持
            targetBounds = containingScreen.getVisualBounds();
            windowX = initialX;
            windowY = initialY;
        } else {
            // どちらも無効な場合、プライマリディスプレイに移動
            targetBounds = ScreenUtils.getPrimaryScreenBounds();
            windowX = targetBounds.getMinX();
            windowY = targetBounds.getMinY();
        }

        // ウィンドウサイズと位置を調整
        adjustWindowSizeAndPosition(targetBounds, windowX, windowY, windowWidth, windowHeight);
    }


    /**
     * ウィンドウのサイズと位置をディスプレイの範囲内に調整します。
     * @param targetBounds ターゲットディスプレイの表示可能範囲
     * @param windowX ウィンドウのX座標
     * @param windowY ウィンドウのY座標
     * @param windowWidth ウィンドウの幅
     * @param windowHeight ウィンドウの高さ
     */
    private void adjustWindowSizeAndPosition(Rectangle2D targetBounds, double windowX, double windowY, 
            double windowWidth, double windowHeight) {
        // ウィンドウサイズがディスプレイサイズを超えている場合は調整
        double maxWidth = targetBounds.getWidth();
        double maxHeight = targetBounds.getHeight();
        
        double adjustedWidth = Math.min(windowWidth, maxWidth);
        double adjustedHeight = Math.min(windowHeight, maxHeight);

        // ウィンドウがディスプレイの範囲外に出ないように位置を調整
        double maxX = targetBounds.getMaxX() - adjustedWidth;
        double maxY = targetBounds.getMaxY() - adjustedHeight;
        
        double adjustedX = Math.max(targetBounds.getMinX(), Math.min(windowX, maxX));
        double adjustedY = Math.max(targetBounds.getMinY(), Math.min(windowY, maxY));

        // ウィンドウの位置とサイズを設定
        this.window.setX(adjustedX);
        this.window.setY(adjustedY);
        this.window.setWidth(adjustedWidth);
        this.window.setHeight(adjustedHeight);
    }

    /**
     * このウィンドウが非表示になった直後に処理するイベント・ハンドラ
     * @param e WindowEvent
     */
    protected void onWindowHidden(WindowEvent e) {
    }

    /**
     * このウィンドウを閉じるリクエストを処理するイベント・ハンドラ
     * @param e WindowEvent
     */
    protected void onWindowCloseRequest(WindowEvent e) {
    }

    final void initWindow(Stage window) {
        window.addEventHandler(WindowEvent.WINDOW_HIDDEN, this::onWindowHidden);
        window.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, this::onWindowCloseRequest);
        this.setWindow(window);
    }
}
