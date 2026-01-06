package logbook.internal;

import java.util.jar.Attributes;
import java.util.jar.Manifest;

import lombok.extern.slf4j.Slf4j;

/**
 * プラットフォーム情報を取得するユーティリティクラス
 * 
 * <p>MANIFEST.MFからBuild-Platform属性を読み取り、ビルド時のプラットフォーム情報を取得します。
 * 取得できない場合は、実行時の環境から推測します。</p>
 * 
 * <p>また、OS情報（Windows, Mac, Linux）とアーキテクチャ情報（x86_64, aarch64など）も取得できます。</p>
 */
@Slf4j
public final class SystemPlatform {
    
    /** OS種別 */
    public enum OsType {
        WINDOWS, MAC, LINUX, UNKNOWN
    }
    
    /** キャッシュされたビルドプラットフォーム（static初期化時に読み込み） */
    private static final String cachedBuildPlatform;
    
    /** キャッシュされたOS情報（static初期化時に読み込み） */
    private static final OsType cachedOsType;
    
    /** キャッシュされたアーキテクチャ情報（static初期化時に読み込み） */
    private static final String cachedArch;
    
    // static初期化ブロック: クラスロード時に一度だけ実行される
    static {
        PlatformInfo info = getPlatformInfoInternal();
        cachedBuildPlatform = info.buildPlatform;
        cachedOsType = info.osType;
        cachedArch = info.arch;
    }
    
    /**
     * プラットフォーム情報を保持する内部クラス
     */
    private static final class PlatformInfo {
        final String buildPlatform;
        final OsType osType;
        final String arch;
        
        PlatformInfo(String buildPlatform, OsType osType, String arch) {
            this.buildPlatform = buildPlatform;
            this.osType = osType;
            this.arch = arch;
        }
    }
    
    /**
     * ビルド時のプラットフォーム情報を取得します
     * 
     * <p>MANIFEST.MFからBuild-Platform属性を優先的に取得します。
     * 取得できない場合は、実行時の環境から推測します。</p>
     * 
     * <p>プラットフォーム値:
     * <ul>
     *   <li>win - Windows</li>
     *   <li>mac - Intel Mac</li>
     *   <li>mac-aarch64 - Apple Silicon (M1/M2/M3) Mac</li>
     *   <li>linux - Linux</li>
     * </ul>
     * </p>
     * 
     * @return プラットフォーム情報（win, mac, mac-aarch64, linux のいずれか）
     */
    public static String getBuildPlatform() {
        return cachedBuildPlatform;
    }
    
    /**
     * OS種別を取得します
     * 
     * <p>実行時の環境からOS種別を判定します。</p>
     * 
     * @return OS種別（WINDOWS, MAC, LINUX, UNKNOWN のいずれか）
     */
    public static OsType getOs() {
        return cachedOsType;
    }
    
    /**
     * OS種別を文字列で取得します
     * 
     * <p>実行時の環境からOS種別を判定し、文字列で返します。</p>
     * 
     * @return OS種別の文字列（"Windows", "Mac", "Linux", "Unknown" のいずれか）
     */
    public static String getOsString() {
        return switch (cachedOsType) {
        case WINDOWS -> "Windows";
        case MAC -> "Mac";
        case LINUX -> "Linux";
        case UNKNOWN -> "Unknown";
        };
    }
    
    /**
     * アーキテクチャ情報を取得します
     * 
     * <p>実行時の環境からアーキテクチャ情報を取得します。</p>
     * 
     * <p>アーキテクチャ値の例:
     * <ul>
     *   <li>x86_64 - Intel/AMD 64bit</li>
     *   <li>aarch64 - ARM 64bit（Apple Silicon Macなど）</li>
     *   <li>arm64 - ARM 64bit（別表記）</li>
     * </ul>
     * </p>
     * 
     * @return アーキテクチャ情報（os.archプロパティの値）
     */
    public static String getArch() {
        return cachedArch;
    }
    
    /**
     * プラットフォーム情報を取得する内部実装
     * MANIFEST.MFを一度だけ読み込んで、プラットフォーム情報を取得します
     * 
     * @return プラットフォーム情報
     */
    private static PlatformInfo getPlatformInfoInternal() {
        String buildPlatform = null;
        
        // MANIFEST.MFからBuild-Platform属性を読み取る
        Manifest manifest = ManifestUtil.getManifest(SystemPlatform.class);
        if (manifest != null) {
            Attributes attrs = manifest.getMainAttributes();
            buildPlatform = attrs.getValue("Build-Platform");
            
            if (buildPlatform != null && !buildPlatform.isEmpty()) {
                log.debug("ビルドプラットフォーム情報を取得しました（ソース: MANIFEST.MF）: {}", buildPlatform);
            } else {
                log.debug("MANIFEST.MFにBuild-Platformが見つかりませんでした");
            }
        } else {
            log.debug("MANIFEST.MFの直接読み取りに失敗しました");
        }
        
        // 実行時の環境から情報を取得
        RuntimePlatformInfo runtimeInfo = detectPlatformFromRuntime();
        
        // MANIFEST.MFから取得できない場合、実行時環境から推測したプラットフォームを使用
        if (buildPlatform == null || buildPlatform.isEmpty()) {
            buildPlatform = runtimeInfo.platform;
            log.debug("実行時環境からプラットフォームを推測しました: {}", buildPlatform);
        }
        
        return new PlatformInfo(buildPlatform, runtimeInfo.osType, runtimeInfo.arch);
    }
    
    /**
     * 実行時の環境からプラットフォーム情報を取得する
     * 
     * <p>os.archプロパティの値:
     * <ul>
     *   <li>Intel Mac: "x86_64"</li>
     *   <li>Apple Silicon Mac: "aarch64" または "arm64"</li>
     * </ul>
     * </p>
     * 
     * @return 実行時プラットフォーム情報
     */
    private static RuntimePlatformInfo detectPlatformFromRuntime() {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        
        OsType osType;
        String platform;
        
        if (osName.contains("windows")) {
            osType = OsType.WINDOWS;
            platform = "win";
        } else if (osName.contains("mac")) {
            osType = OsType.MAC;
            // os.archでIntel MacとApple Silicon Macを区別
            // Apple Silicon (M1/M2/M3) の場合は aarch64 または arm64
            if ("aarch64".equals(osArch) || "arm64".equals(osArch)) {
                platform = "mac-aarch64";
            } else if ("x86_64".equals(osArch)) {
                // Intel Mac
                platform = "mac";
            } else {
                // 不明なアーキテクチャの場合は、デフォルトでmacとする
                log.warn("不明なMacアーキテクチャ: os.arch={}, macとして扱います", osArch);
                platform = "mac";
            }
        } else if (osName.contains("linux")) {
            osType = OsType.LINUX;
            platform = "linux";
        } else {
            osType = OsType.UNKNOWN;
            platform = "unknown";
        }
        
        return new RuntimePlatformInfo(platform, osType, osArch);
    }
    
    /**
     * 実行時プラットフォーム情報を保持する内部クラス
     */
    private static final class RuntimePlatformInfo {
        final String platform;
        final OsType osType;
        final String arch;
        
        RuntimePlatformInfo(String platform, OsType osType, String arch) {
            this.platform = platform;
            this.osType = osType;
            this.arch = arch;
        }
    }
    
}

