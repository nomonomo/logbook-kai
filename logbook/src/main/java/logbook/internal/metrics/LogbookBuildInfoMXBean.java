package logbook.internal.metrics;

/**
 * 起動時に確定するビルド識別情報を JMX 経由で公開する MXBean インターフェースです。
 */
public interface LogbookBuildInfoMXBean {

    /**
     * ビルド情報メトリクス用の定数値（Prometheus の info 系メトリクス出力用）。
     *
     * @return 常に {@code 1}
     */
    int getInfo();

    /**
     * セマンティックバージョン（ビルド日時を含まない）。
     *
     * @return バージョン文字列（例: {@code 26.6.3}）
     */
    String getVersion();

    /**
     * ビルド日時。
     *
     * @return MANIFEST.MF の Build-Timestamp、未取得時は空文字
     */
    String getBuildTimestamp();

    /**
     * 開発モードが有効かどうか。
     *
     * @return 有効な場合 {@code 1}、それ以外 {@code 0}
     */
    int getDevMode();
}
