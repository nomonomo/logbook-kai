package logbook.internal.capture;

import logbook.bean.AppConfig;
import logbook.internal.DevMode;
import logbook.internal.Version;

/**
 * ウィンドウタイトル等の API キャプチャ状態表示。
 */
public final class ApiCaptureTitles {

    private static final String RECORDING_SUFFIX = " [API記録中]";

    private ApiCaptureTitles() {
    }

    /**
     * 航海日誌のメインウィンドウタイトルを返す。
     */
    public static String mainWindowTitle() {
        String base = "航海日誌 " + DevMode.formatVersionDisplay(Version.getCurrent());
        if (ApiCaptureGate.isCaptureActive(AppConfig.get())) {
            return base + RECORDING_SUFFIX;
        }
        return base;
    }
}
