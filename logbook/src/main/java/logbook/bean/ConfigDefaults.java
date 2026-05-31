package logbook.bean;

/**
 * {@link logbook.internal.Config} が JSON 読み込み後に適用するデフォルト値・バージョン差異補正。
 */
public interface ConfigDefaults {

    /**
     * デシリアライズ後の未設定項目に初期値を設定する。
     */
    void applyDefaults();
}
