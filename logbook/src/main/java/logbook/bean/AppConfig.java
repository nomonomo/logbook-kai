package logbook.bean;

import java.io.File;
import java.io.Serializable;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import logbook.internal.Config;
import logbook.internal.Rank;
import logbook.internal.ShipImageCacheStrategy;
import lombok.Data;

/**
 * アプリケーションの設定
 *
 */
@Data
public final class AppConfig implements Serializable, ConfigDefaults {

    private static final long serialVersionUID = -158061123666406172L;

    /** ウインドウスタイル */
    private String windowStyle = "main";

    /** フォント */
    private String fontFamily;

    /** フォントサイズ */
    private String fontSize = "default";

    /** 遠征・入渠完了時に通知をする。集合が無い JSON の移行にだけ使う */
    private boolean useNotification = true;

    /** 出撃時に大破艦がいる場合に通知をする */
    private boolean alertBadlyStart = true;

    /** 進撃時に大破艦がいる場合に通知をする */
    private boolean alertBadlyNext = true;

    /** 第二艦隊の旗艦の大破は通知しない */
    private boolean ignoreSecondFlagship = false;

    /** 通知でサウンドを鳴らす */
    private boolean useSound = true;

    /** 通知でトーストを表示 */
    private boolean useToast = true;

    /** 疲労回復予想時刻にトーストを表示 */
    private boolean useCondRecoverToast = false;

    /**
     * 通知の種別。トーストとサウンドで別々に選ぶ。
     * サウンドの既定ディレクトリを持つ。
     */
    public enum NotifyKind {
        /** 遠征完了 */
        MISSION("./sounds/mission/"),
        /** 入渠完了 */
        NDOCK("./sounds/ndock/"),
        /** 遠征条件を満たしていない */
        MISSION_CONDITION("./sounds/mission-condition/"),
        /** 母港給糧 */
        PORT_SUPPLY("./sounds/port-supply/"),
        /** 疲労回復 */
        COND("./sounds/cond/"),
        /** 出撃時の大破 */
        BADLY_SORTIE("./sounds/alert/"),
        /** 進撃時の大破 */
        BADLY_ADVANCE("./sounds/alert/"),
        /** ギミック */
        GIMMICK("./sounds/gimmick/");

        /** サウンドの既定ディレクトリ */
        private final String defaultSoundDir;

        NotifyKind(String defaultSoundDir) {
            this.defaultSoundDir = defaultSoundDir;
        }

        /**
         * サウンドの既定ディレクトリを返します。
         *
         * @return 既定ディレクトリ
         */
        public String defaultSoundDir() {
            return this.defaultSoundDir;
        }
    }

    /**
     * トーストで出す通知。
     * JSON に項目が無い場合は null のままデシリアライズされ、{@link #applyDefaults()} で旧フラグから埋める。
     * 大破出撃と大破進撃は常に含まれる。
     */
    private EnumSet<NotifyKind> toastNotify;

    /**
     * サウンドで出す通知。
     * JSON に項目が無い場合は null のままデシリアライズされ、{@link #applyDefaults()} で旧フラグから埋める。
     * 移行時の疲労回復と母港給糧はオフ。大破出撃と大破進撃は常に含まれる。
     */
    private EnumSet<NotifyKind> soundNotify;

    /** トーストの位置 (default: BOTTOM_RIGHT) */
    private String toastLocation;

    /** 遠征完了時のリマインド。トーストとサウンドの指定が無い JSON の移行にだけ使う */
    private boolean useRemind = true;

    /** 遠征完了時のリマインド(秒)。トーストとサウンドで共通 */
    private int remind = 60;

    /**
     * 遠征完了のトーストを繰り返す。
     * JSON に項目が無いときは null のままにし、{@link #applyDefaults()} で {@link #useRemind} をコピーする。
     */
    private Boolean useToastRemind;

    /**
     * 遠征完了のサウンドを繰り返す。
     * JSON に項目が無いときは null のままにし、{@link #applyDefaults()} で {@link #useRemind} をコピーする。
     */
    private Boolean useSoundRemind;

    /** 戦果ペインの表示 */
    private boolean showAchievement = false;

    /** 遠征ペインの表示 */
    private boolean showMission = true;

    /** 入渠ペインの表示 */
    private boolean showNdock = true;

    /** 任務ペインの表示 */
    private boolean showQuest = true;

    /** 艦娘の画像に経験値バーを表示する */
    private boolean visibleExpGauge = true;

    /** 母港枠の空きがこれ以下でボタンを警告色に変える */
    private int shipFullyThreshold = 4;

    /** 装備枠の空きがこれ以下でボタンを警告色に変える */
    private int itemFullyThreshold = 16;

    /** 艦娘や装備の画像の拡大縮小割合(%) */
    private int imageZoomRate = 100;

    /** 戦闘開始時に結果を反映 */
    private boolean applyBattle;

    /** 戦闘結果時に結果を反映 */
    private boolean applyResult = true;

    /** 艦隊タブに全艦隊のタブを追加 */
    private boolean allDecksTab = false;

    /** 艦隊タブに艦隊単位のタブを追加 */
    private boolean deckTabs = false;

    /** 艦隊タブにラベル単位のタブを追加 */
    private boolean labelTabs = true;

    /** 艦隊タブの色（無傷） */
    private String tabColorNoDamage;
    /** 艦隊タブの色（健在） */
    private String tabColorLessThanSlightDamage;
    /** 艦隊タブの色（小破） */
    private String tabColorSlightDamage;
    /** 艦隊タブの色（中破） */
    private String tabColorHalfDamage;
    /** 艦隊タブの色（大破） */
    private String tabColorBadlyDamage;
    /** 艦隊タブの色（未遠征） */
    private String tabColorNoMission;
    /** 艦隊タブの色（要補給） */
    private String tabColorNeedRefuel;
    
    /** 音量 */
    private int soundLevel = 85;

    /** 報告書の保存先 */
    private String reportPath = new File("").getAbsolutePath();

    /** 資材ログ保存間隔 */
    private int materialLogInterval = 600;

    /** 最前面に表示する */
    private boolean onTop = false;

    /** 終了時に確認する */
    private boolean checkDoit = true;

    /** 起動時にアップデートチェック */
    private boolean checkUpdate = true;

    /** ゲームデータ（マップ・任務・識別札）の更新チェック（起動時・設定画面・前回から12時間） */
    private boolean checkGameDataUpdate = true;

    /** ゲームデータのローカル外部ディレクトリ */
    private String gameDataDir = "./config/gamedata/";

    /** 通信エラーの抑止 */
    private boolean connectionClose = true;

    /** ポート番号 */
    private int listenPort = 8888;

    /** ルート証明書ファイルパス */
    private String rootCertificatePath = "logbook-ca.p12";

    /** サーバー証明書ファイルパス(互換用) */
    private String serverCertificatePath;

    /**
     * プロキシSSLで使用する証明書の種別。
     * true: ルート証明書（メモリ上でサーバー証明書を生成）、false: サーバー証明書ファイルを直接読込（従来方式）。
     * JSON に項目が無い場合は null のままデシリアライズされ、{@link #applyDefaults()} で補完する。
     */
    private Boolean proxySslUseRootCertificate;

    /** ローカルループバックアドレスからの接続のみ受け入れる */
    private boolean allowOnlyFromLocalhost = true;

    /** プロキシ利用 */
    private boolean useProxy;

    /** プロキシホスト */
    private String proxyHost = "localhost"; //$NON-NLS-1$

    /** プロキシポート */
    private int proxyPort = 8080;

    /** プラグインを有効にする */
    private boolean usePlugin = true;

    /**
     * 種別ごとのサウンドディレクトリ。
     * JSON に項目が無い、またはキーが足りないときは {@link #applyDefaults()} で既定を入れる。
     * すでに書いてあるパスは残す。
     */
    private Map<NotifyKind, String> notifySoundDir;

    /** デフォルトサウンド */
    private String defaultNotifySound = "C:\\Windows\\media\\Windows Notify System Generic.wav"; //$NON-NLS-1$

    /** プラグインディレクトリ */
    private String pluginsDir = "./plugins/"; //$NON-NLS-1$

    /** リソースディレクトリ */
    private String resourcesDir = "./resources/"; //$NON-NLS-1$

    /** 戦闘ログディレクトリ */
    private String battleLogDir = "./battlelog/"; //$NON-NLS-1$

    /** 戦闘ログの保存期限 */
    private int battleLogExpires = 60;

    /** 戦闘ログの保存期間無期限 */
    private boolean indefiniteExpires = false;

    /** 戦闘ログの圧縮 */
    private boolean compressBattleLogs = false;

    /** 戦闘ログにローデータを含める */
    private boolean includeRawData = false;

    /** レベリング海域 */
    private int seaAreaIndex = 0;

    /** レベリング評価 */
    private Rank resultRank = Rank.S勝利;

    /** ウインドウ位置 */
    private Map<String, WindowLocation> windowLocationMap = new HashMap<>();

    /** テーブル列の表示・非表示 */
    private Map<String, Set<String>> columnVisibleMap = new HashMap<>();

    /** テーブル列の幅 */
    private Map<String, Map<String, Double>> columnWidthMap = new HashMap<>();

    /** テーブル列のソート順 */
    private Map<String, Map<String, String>> columnSortOrderMap = new LinkedHashMap<>();

    /** テーブル列の並び順 */
    private Map<String, List<String>> columnOrderMap = new LinkedHashMap<>();

    /** SplitPaneの分割サイズ */
    private Map<String, Double> dividerPositionMap = new HashMap<>();

    /** キャプチャの保存先 */
    private String captureDir;

    /** キャプチャの画像形式 */
    private String captureFormat = "jpg";

    /** FFmpeg 実行ファイル */
    private String ffmpegPath;

    /** FFmpeg 引数 */
    private String ffmpegArgs;

    /** FFmpeg 拡張子 */
    private String ffmpegExt;

    /** 艦娘画像キャッシュ設定 */
    private ShipImageCacheStrategy shipImageCacheStrategy = ShipImageCacheStrategy.ALL;

    /** 画像ファイルを再圧縮 */
    private boolean shipImageCompress = false;

    /** 所有艦娘一覧から艦娘画像を隠す */
    private boolean hideShipImageFromShipTablePane = false;

    /** 所有艦娘一覧から装備画像を隠す */
    private boolean hideItemImageFromShipTablePane = false;

    /** 艦隊タブに旗艦の立ち絵を表示 */
    private boolean visiblePoseImageOnFleetTab = false;

    /** store api_start2 */
    private boolean storeApiStart2 = false;

    /** store api_start2 directory */
    private String storeApiStart2Dir = "";

    /** API レスポンス記録を有効にする */
    private boolean apiCaptureEnabled = false;

    /** API 記録の同意済みフラグ */
    private boolean apiCaptureConsentAccepted = false;

    /** API 記録の保存先ディレクトリ */
    private String apiCaptureDir = "";

    /**
     * JSON デシリアライズ後に呼び出し、新規フィールドのバージョン差異を解消する。
     * {@link logbook.internal.Config} から読み込み時および新規生成時に適用される。
     */
    @Override
    public void applyDefaults() {
        applyProxySslUseRootCertificateDefault();
        applyNotifyChannels();
        applyNotifySoundDirs();
        applyRemindChannels();
    }

    /**
     * トーストでその種別を出すかを返します。
     *
     * @param kind 通知の種別
     * @return 出すなら true
     */
    public boolean notifiesByToast(NotifyKind kind) {
        return this.toastNotify != null && this.toastNotify.contains(kind);
    }

    /**
     * サウンドでその種別を出すかを返します。
     *
     * @param kind 通知の種別
     * @return 出すなら true
     */
    public boolean notifiesBySound(NotifyKind kind) {
        return this.soundNotify != null && this.soundNotify.contains(kind);
    }

    /**
     * その種別のサウンドディレクトリを返します。空の指定は既定に戻します。
     *
     * @param kind 通知の種別
     * @return サウンドディレクトリ
     */
    public String soundDir(NotifyKind kind) {
        String dir = this.notifySoundDir == null ? null : this.notifySoundDir.get(kind);
        if (dir == null || dir.isEmpty()) {
            return kind.defaultSoundDir();
        }
        return dir;
    }

    private void applyRemindChannels() {
        if (this.useToastRemind == null) {
            this.useToastRemind = this.useRemind;
        }
        if (this.useSoundRemind == null) {
            this.useSoundRemind = this.useRemind;
        }
    }

    private void applyNotifySoundDirs() {
        if (this.notifySoundDir == null) {
            this.notifySoundDir = new EnumMap<>(NotifyKind.class);
        }
        for (NotifyKind kind : NotifyKind.values()) {
            String dir = this.notifySoundDir.get(kind);
            if (dir == null || dir.isEmpty()) {
                this.notifySoundDir.put(kind, kind.defaultSoundDir());
            }
        }
    }

    private void applyNotifyChannels() {
        if (this.toastNotify == null) {
            boolean channel = this.useToast;
            EnumSet<NotifyKind> toast = EnumSet.noneOf(NotifyKind.class);
            // 遠征と入渠は、develop では useNotification が両方を見ていた
            addKind(toast, NotifyKind.MISSION, channel && this.useNotification);
            addKind(toast, NotifyKind.NDOCK, channel && this.useNotification);
            addKind(toast, NotifyKind.MISSION_CONDITION, channel);
            addKind(toast, NotifyKind.PORT_SUPPLY, channel);
            addKind(toast, NotifyKind.COND, channel && this.useCondRecoverToast);
            addKind(toast, NotifyKind.GIMMICK, channel);
            this.toastNotify = toast;
        }
        if (this.soundNotify == null) {
            boolean channel = this.useSound;
            EnumSet<NotifyKind> sound = EnumSet.noneOf(NotifyKind.class);
            addKind(sound, NotifyKind.MISSION, channel && this.useNotification);
            addKind(sound, NotifyKind.NDOCK, channel && this.useNotification);
            addKind(sound, NotifyKind.MISSION_CONDITION, channel);
            addKind(sound, NotifyKind.GIMMICK, channel);
            this.soundNotify = sound;
        }
        this.toastNotify.add(NotifyKind.BADLY_SORTIE);
        this.toastNotify.add(NotifyKind.BADLY_ADVANCE);
        this.soundNotify.add(NotifyKind.BADLY_SORTIE);
        this.soundNotify.add(NotifyKind.BADLY_ADVANCE);
    }

    private static void addKind(EnumSet<NotifyKind> target, NotifyKind kind, boolean enabled) {
        if (enabled) {
            target.add(kind);
        }
    }

    private void applyProxySslUseRootCertificateDefault() {
        if (this.proxySslUseRootCertificate != null) {
            return;
        }
        // 旧設定互換: サーバー証明書パス未設定ならルート証明書方式
        String server = this.serverCertificatePath;
        this.proxySslUseRootCertificate = server == null || server.isEmpty();
    }

    /**
     * アプリケーションのデフォルト設定ディレクトリからアプリケーション設定を取得します、
     * これは次の記述と同等です
     * <blockquote>
     *     <code>Config.getDefault().get(AppConfig.class, AppConfig::new)</code>
     * </blockquote>
     *
     * @return アプリケーションの設定
     */
    public static AppConfig get() {
        return Config.getDefault().get(AppConfig.class, AppConfig::new);
    }
}
