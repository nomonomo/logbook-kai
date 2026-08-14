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
}
