package logbook.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Manifest;

import lombok.extern.slf4j.Slf4j;

/**
 * MANIFEST.MFを読み取るユーティリティクラス
 * 
 * <p>モジュールシステムとクラスパスの両方に対応して、MANIFEST.MFを読み取ります。</p>
 * 
 * <p>読み取り順序:
 * <ol>
 *   <li>モジュールシステム経由（Module.getResourceAsStream()）</li>
 *   <li>クラスパス経由（Class.getResourceAsStream()）</li>
 * </ol>
 * </p>
 */
@Slf4j
public final class ManifestUtil {
    
    /**
     * クラスパスからMANIFEST.MFを取得する
     * 
     * <p>モジュールシステムとクラスパスの両方に対応しています。
     * まずモジュールシステム経由で取得を試み、失敗した場合はクラスパス経由で取得を試みます。</p>
     * 
     * @param clazz リソースを取得するための基準となるクラス（通常は呼び出し元のクラス）
     * @return Manifestオブジェクト、取得できない場合はnull
     */
    public static Manifest getManifest(Class<?> clazz) {
        // モジュールシステム経由で取得を試みる
        Module module = clazz.getModule();
        if (module != null && module.isNamed()) {
            try (InputStream is = module.getResourceAsStream("META-INF/MANIFEST.MF")) {
                if (is != null) {
                    return new Manifest(is);
                }
            } catch (IOException e) {
                log.debug("Module.getResourceAsStream()でMANIFEST.MFの読み取りに失敗しました", e);
            }
        }
        
        // クラスパス経由で取得を試みる
        try (InputStream is = clazz.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (is != null) {
                return new Manifest(is);
            }
        } catch (IOException e) {
            log.debug("Class.getResourceAsStream()でMANIFEST.MFの読み取りに失敗しました", e);
        }
        
        return null;
    }
    
    /**
     * MANIFEST.MFから指定された属性値を取得する
     * 
     * @param clazz リソースを取得するための基準となるクラス
     * @param attributeName 属性名（例: "Build-Platform", "Implementation-Version"）
     * @return 属性値、取得できない場合はnull
     */
    public static String getAttribute(Class<?> clazz, String attributeName) {
        Manifest manifest = getManifest(clazz);
        if (manifest != null) {
            return manifest.getMainAttributes().getValue(attributeName);
        }
        return null;
    }
}

