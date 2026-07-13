package logbook.internal.capture;

import logbook.bean.AppConfig;
import logbook.internal.DevMode;

/**
 * API キャプチャ機能の UI 表示および実行可否を判定する。
 * <p>
 * 一般配布では {@link #UI_ENV} / {@link #UI_PROPERTY} 未設定時に UI を出さない。
 * {@link #DEV_GATE_PROPERTY} が true のときは {@link DevMode} または {@link #FORCE_PROPERTY} が必要。
 * </p>
 */
public final class ApiCaptureGate {

    /** 環境変数: {@code 1} または {@code true} で UI を表示 */
    public static final String UI_ENV = "LOGBOOK_API_CAPTURE_UI";

    /** システムプロパティ: {@code true} で UI を表示 */
    public static final String UI_PROPERTY = "logbook.apiCapture.ui";

    /** システムプロパティ: {@code true} で UI 表示に dev モードまたは force を要求 */
    public static final String DEV_GATE_PROPERTY = "logbook.apiCapture.devGate";

    /** システムプロパティ: {@code true} で devGate をバイパス */
    public static final String FORCE_PROPERTY = "logbook.apiCapture.force";

    private ApiCaptureGate() {
    }

    /**
     * 設定画面に API キャプチャ UI を表示してよいか。
     */
    public static boolean isUiAvailable() {
        if (!isUiFlagSet()) {
            return false;
        }
        if (Boolean.getBoolean(DEV_GATE_PROPERTY)) {
            return DevMode.isEnabled() || Boolean.getBoolean(FORCE_PROPERTY);
        }
        return true;
    }

    /**
     * devGate が有効か（メッセージ表示用）。
     */
    public static boolean isDevGateEnabled() {
        return Boolean.getBoolean(DEV_GATE_PROPERTY);
    }

    /**
     * 設定に基づき API レスポンスの記録を行うか。
     */
    public static boolean isCaptureActive(AppConfig config) {
        return config.isApiCaptureEnabled()
                && config.isApiCaptureConsentAccepted()
                && config.getApiCaptureDir() != null
                && !config.getApiCaptureDir().isBlank();
    }

    /**
     * 現在の設定で記録が有効か（{@link AppConfig#get()} を参照）。
     */
    public static boolean isCaptureActive() {
        return isCaptureActive(AppConfig.get());
    }

    private static boolean isUiFlagSet() {
        String env = System.getenv(UI_ENV);
        if (env != null && ("1".equals(env) || "true".equalsIgnoreCase(env))) {
            return true;
        }
        return Boolean.getBoolean(UI_PROPERTY);
    }
}
