package logbook.internal.gamedata;

import java.nio.file.Path;
import java.nio.file.Paths;

import logbook.bean.AppConfig;

/**
 * ゲームデータの相対パス定数とローカルルート解決。
 */
public final class GameDataPaths {

    /** デフォルトのローカル外部データディレクトリ */
    public static final String DEFAULT_DIR = "./config/gamedata/";

    /** デフォルトのリモート配信ベース URL（リポジトリ data/） */
    public static final String DEFAULT_BASE_URL =
            "https://raw.githubusercontent.com/nomonomo/logbook-kai/master/data";

    public static final String MANIFEST = "manifest.json";
    public static final String MAPPING = "map/mapping.json";
    public static final String SEAAREA = "seaarea/seaarea.json";
    /** 配信用の任務条件バンドル */
    public static final String QUESTS = "quest/quests.json";

    /** クラスパス上のマニフェスト */
    public static final String CLASSPATH_MANIFEST = "logbook/manifest.json";
    /** クラスパス上のマッピング */
    public static final String CLASSPATH_MAPPING = "logbook/map/mapping.json";
    /** クラスパス上の識別札 */
    public static final String CLASSPATH_SEAAREA = "logbook/seaarea/seaarea.json";
    /** クラスパス上の任務条件バンドル */
    public static final String CLASSPATH_QUESTS = "logbook/quest/quests.json";

    private GameDataPaths() {
    }

    /**
     * ローカル外部データのルートディレクトリを返します。
     *
     * @return ルート Path
     */
    public static Path root() {
        String dir = AppConfig.get().getGameDataDir();
        if (dir == null || dir.isBlank()) {
            dir = DEFAULT_DIR;
        }
        return Paths.get(dir).toAbsolutePath().normalize();
    }

    /**
     * ローカル外部データのファイル Path を返します。
     *
     * @param relativePath data/ 配下の相対パス
     * @return 絶対 Path
     */
    public static Path resolveLocal(String relativePath) {
        return root().resolve(relativePath).normalize();
    }
}
