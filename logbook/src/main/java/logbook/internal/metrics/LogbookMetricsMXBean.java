package logbook.internal.metrics;

/**
 * 実行中のアプリケーション状態を JMX 経由で公開する MXBean インターフェースです。
 */
public interface LogbookMetricsMXBean {

    /**
     * JVM 起動からの経過秒数。
     *
     * @return 経過秒数
     */
    long getUptimeSeconds();

    /**
     * リッスンポート番号。
     *
     * @return {@link logbook.bean.AppConfig#getListenPort()}
     */
    int getListenPort();

    /**
     * プロキシサーバーが稼働中かどうか。
     *
     * @return 稼働中の場合 {@code 1}、それ以外 {@code 0}
     */
    int getServerRunning();

    /**
     * 読み込み済みプラグイン数。
     *
     * @return プラグイン数
     */
    int getPluginCount();

    /**
     * JVM 起動から {@code Launcher.main} 先頭までのミリ秒。未計測は 0。
     *
     * @return ミリ秒
     */
    long getStartupJvmToLauncherMillis();

    /**
     * Launcher 先頭からメインウィンドウ表示までのミリ秒。未確定は 0。
     *
     * @return ミリ秒
     */
    long getStartupToWindowShownMillis();

    /**
     * Launcher 先頭から初回 UI 更新完了までのミリ秒。未確定は 0。
     *
     * @return ミリ秒
     */
    long getStartupToUiReadyMillis();

    /**
     * JVM 起動からメインウィンドウ表示までのミリ秒。未確定は 0。
     *
     * @return ミリ秒
     */
    long getStartupJvmToWindowShownMillis();

    /**
     * JVM 起動から初回 UI 更新完了までのミリ秒。未確定は 0。
     *
     * @return ミリ秒
     */
    long getStartupJvmToUiReadyMillis();
}
