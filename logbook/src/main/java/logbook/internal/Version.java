package logbook.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.module.ModuleDescriptor;
import java.util.Arrays;
import java.util.Iterator;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import lombok.extern.slf4j.Slf4j;

/**
 * Version info
 *
 */
@Slf4j
public final class Version implements Comparable<Version>, Serializable {

    private static final long serialVersionUID = 770093260258378309L;

    /** UNKNOWN Version */
    public static final Version UNKNOWN = new Version(Integer.MAX_VALUE, 0, 0);
    
    /** キャッシュされた現在のバージョン（static初期化時に読み込み） */
    private static final Version cachedCurrent;
    
    // static初期化ブロック: クラスロード時に一度だけ実行される
    static {
        cachedCurrent = getCurrentInternal();
    }

    /** major */
    private final int major;

    /** minor */
    private final int minor;

    /** revision */
    private final int revision;
    
    /** title */
    private final String title;
    
    /** vendor */
    private final String vendor;

    /**
     * Version constructor
     *
     * @param major Major
     * @param minor Minor
     * @param revision Revision
     */
    public Version(int major, int minor, int revision) {
        this(major, minor, revision, null, null);
    }
    
    /**
     * Version constructor
     *
     * @param major Major
     * @param minor Minor
     * @param revision Revision
     * @param title Title
     * @param vendor Vendor
     */
    public Version(int major, int minor, int revision, String title, String vendor) {
        this.major = major;
        this.minor = minor;
        this.revision = revision;
        this.title = title;
        this.vendor = vendor;
    }

    /**
     * Version constructor
     *
     * @param version Version String
     */
    public Version(String version) {
        this(version, null, null);
    }
    
    /**
     * Version constructor
     *
     * @param version Version String
     * @param title Title
     * @param vendor Vendor
     */
    public Version(String version, String title, String vendor) {
        int major = 0, minor = 0, revision = 0;
        try {
            Iterator<String> ite = Arrays.asList(version.trim().split("\\.")).iterator(); //$NON-NLS-1$
            if (ite.hasNext()) {
                major = Integer.parseInt(ite.next());
            }
            if (ite.hasNext()) {
                minor = Integer.parseInt(ite.next());
            }
            if (ite.hasNext()) {
                revision = Integer.parseInt(ite.next());
            }
        } catch (Exception e) {
            major = Integer.MAX_VALUE;
            minor = 0;
            revision = 0;
        }
        this.major = major;
        this.minor = minor;
        this.revision = revision;
        this.title = title;
        this.vendor = vendor;
    }

    /**
     * majorを取得します。
     * @return major
     */
    public int getMajor() {
        return this.major;
    }

    /**
     * minorを取得します。
     * @return minor
     */
    public int getMinor() {
        return this.minor;
    }

    /**
     * revisionを取得します。
     * @return revision
     */
    public int getRevision() {
        return this.revision;
    }
    
    /**
     * titleを取得します。
     * @return title
     */
    public String getTitle() {
        return this.title;
    }
    
    /**
     * vendorを取得します。
     * @return vendor
     */
    public String getVendor() {
        return this.vendor;
    }

    @Override
    public String toString() {
        if (this.equals(UNKNOWN)) {
            return "unknown";
        }
        String version = this.major + "." + this.minor; //$NON-NLS-1$
        if (this.revision > 0) {
            version += "." + this.revision; //$NON-NLS-1$
        }
        return version;
    }

    @Override
    public int compareTo(Version o) {
        if (this.major != o.major)
            return Integer.compare(this.major, o.major);
        if (this.minor != o.minor)
            return Integer.compare(this.minor, o.minor);
        if (this.revision != o.revision)
            return Integer.compare(this.revision, o.revision);
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Version) && (this.compareTo((Version) o) == 0);
    }

    @Override
    public int hashCode() {
        return (this.major << 16) | (this.minor << 8) | this.revision;
    }

    /**
     * アプリケーションのバージョンを取得します
     * 
     * <p>MANIFEST.MFから優先的に取得します。
     * titleとvendorはMANIFEST.MF（Package.getImplementationTitle/Vendor）からしか取得できないため、
     * MANIFEST.MFを優先することで、バージョン情報とともにタイトルとベンダーも確実に取得できます。</p>
     * 
     * <p>static初期化ブロックでクラスロード時に一度だけMANIFEST.MFを読み込み、結果をキャッシュします。
     * このメソッドはキャッシュされた値を返すだけです。</p>
     * 
     * @return アプリケーションの現在のバージョン
     */
    public static Version getCurrent() {
        return cachedCurrent;
    }
    
    /**
     * アプリケーションのバージョンを取得する内部実装
     * MANIFEST.MFを読み込んでVersionオブジェクトを生成します
     * 
     * @return アプリケーションの現在のバージョン
     */
    private static Version getCurrentInternal() {
        String version = null;
        String title = null;
        String vendor = null;
        String source = null;
        
        // MANIFEST.MFを直接読み取る（Package.getImplementationVersion()はモジュールシステムでは機能しないため）
        // getManifestFromClassPath()は既に例外を処理しているため、try-catchは不要
        Manifest manifest = getManifestFromClassPath();
        if (manifest != null) {
            Attributes attrs = manifest.getMainAttributes();
            version = attrs.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            title = attrs.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
            vendor = attrs.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
            
            if (version != null && !version.isEmpty()) {
                source = "MANIFEST.MF (直接読み取り)";
                log.debug("バージョン情報を取得しました（ソース: {}）: version={}, title={}, vendor={}", 
                    source, version, title, vendor);
                return new Version(version, title, vendor);
            } else {
                log.debug("MANIFEST.MFにImplementation-Versionが見つかりませんでした");
            }
        } else {
            log.debug("MANIFEST.MFの直接読み取りに失敗しました");
        }
        
        // MANIFEST.MFから取得できない場合、ModuleDescriptorから取得を試みる
        // ただし、ModuleDescriptorにはtitleとvendorの情報がないため、これらはnullになる
        ModuleDescriptor moduleDescriptor = Version.class.getModule().getDescriptor();
        if (moduleDescriptor != null) {
            String moduleVersion = moduleDescriptor.version()
                    .map(ModuleDescriptor.Version::toString)
                    .orElse(null);
            if (moduleVersion != null && !moduleVersion.isEmpty()) {
                version = moduleVersion;
                log.debug("バージョンを取得しました（ソース: ModuleDescriptor）: {}", version);
                log.debug("最終的なバージョン情報（ソース: ModuleDescriptor）: version={}, title={}, vendor={}", 
                    version, title, vendor);
                return new Version(version, title, vendor);
            } else {
                log.debug("ModuleDescriptorからバージョン情報を取得できませんでした");
            }
        }
        
        // どちらからも取得できない場合
        log.debug("すべての方法でバージョン情報を取得できませんでした。UNKNOWNを返します。");
        return UNKNOWN;
    }
    
    /**
     * クラスパスからMANIFEST.MFを取得する。
     * モジュールシステム対応: Module.getResourceAsStream()またはClass.getResourceAsStream()を使用。
     * 
     * @return Manifestオブジェクト、取得できない場合はnull
     */
    private static Manifest getManifestFromClassPath() {
        Module module = Version.class.getModule();
        if (module != null && module.isNamed()) {
            try (InputStream is = module.getResourceAsStream("META-INF/MANIFEST.MF")) {
                if (is != null) {
                    return new Manifest(is);
                }
            } catch (IOException e) {
                log.debug("Module.getResourceAsStream()でMANIFEST.MFの読み取りに失敗しました", e);
            }
        }
        
        try (InputStream is = Version.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (is != null) {
                return new Manifest(is);
            }
        } catch (IOException e) {
            log.debug("Class.getResourceAsStream()でMANIFEST.MFの読み取りに失敗しました", e);
        }
        
        return null;
    }
}
