package logbook.internal.ssl;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.ssl.X509;

import lombok.extern.slf4j.Slf4j;

/**
 * SSL証明書関連のユーティリティクラス
 */
@Slf4j
public final class SslCertificateUtil {
    
    private SslCertificateUtil() {
        // ユーティリティクラスのためインスタンス化禁止
    }
    
    /**
     * 指定されたパスからServerファクトリのロードを試行する。
     * 
     * @param keystorePath keystoreファイルのパス
     * @return ロード成功時はServerファクトリ、失敗時はnull
     */
    public static SslContextFactory.Server tryLoadServerFactory(String keystorePath) {
        SslContextFactory.Server serverFactory = new SslContextFactory.Server();
        serverFactory.setKeyStorePath(keystorePath);
        serverFactory.setKeyStorePassword("changeit");
        serverFactory.setKeyStoreType("PKCS12");
        serverFactory.setCertAlias("kancolle-cert");
        serverFactory.setSniRequired(false); // SNI検証を無効化
        
        try {
            serverFactory.start();
            logCertificateInfo(serverFactory, keystorePath);
        } catch (Exception e) {
            log.info("Failed to load keystore from path: {}", keystorePath, e);
            return null;
        }
        return serverFactory;
    }
    
    /**
     * 既存のServerファクトリをリロード、または新規作成する。
     * 
     * @param keystorePath keystoreファイルのパス
     * @param existingFactory 既存のServerファクトリ（nullの場合は新規作成）
     * @return リロード/作成成功時はServerファクトリ、失敗時はnull
     */
    public static SslContextFactory.Server reloadServerFactory(String keystorePath, 
                                                                SslContextFactory.Server existingFactory) {
        if (keystorePath == null || keystorePath.isEmpty()) {
            log.warn("Certificate path is empty");
            return null;
        }
        
        if (existingFactory == null) {
            // ファクトリが初期化されていない場合、新規に作成
            log.info("SSL server factory is not initialized, creating new instance");
            return tryLoadServerFactory(keystorePath);
        }

        // 既存のファクトリがある場合はreload()を使用
        try {
            existingFactory.reload(scf -> {
                // 新しい証明書パスを設定
                scf.setKeyStorePath(keystorePath);
                scf.setKeyStorePassword("changeit");
                scf.setKeyStoreType("PKCS12");
                scf.setCertAlias("kancolle-cert");
                log.debug("Updated server SSL configuration with new certificate path: {}", keystorePath);
            });
                
            log.info("Server certificate reloaded successfully using Jetty reload() API");
            logCertificateInfo(existingFactory, keystorePath);
            return existingFactory;
        } catch (Exception e) {
            log.error("Failed to reload server certificate", e);
            return null;
        }
    }
    
    /**
     * ロード成功した証明書の情報をログ出力する。
     * 
     * @param serverFactory SSL Context Factory
     * @param keystorePath 証明書ファイルパス
     */
    public static void logCertificateInfo(SslContextFactory.Server serverFactory, String keystorePath) {
        try {
            Set<String> aliases = serverFactory.getAliases();
            log.info("Successfully loaded keystore from: {}", keystorePath);
            log.info("Certificate aliases in keystore: {}", aliases);
            
            // 各エイリアスの証明書情報を表示
            aliases.forEach(alias -> logCertificateAlias(serverFactory, alias));
        } catch (Exception e) {
            log.warn("Failed to log certificate chain details", e);
        }
    }
    
    /**
     * 証明書エイリアスの詳細情報をログ出力する。
     * 
     * @param serverFactory SSL Context Factory
     * @param alias 証明書エイリアス
     */
    public static void logCertificateAlias(SslContextFactory.Server serverFactory, String alias) {
        X509 x509 = serverFactory.getX509(alias);
        if (x509 == null) return;
        
        int hostsCount = (x509.getHosts() != null && x509.getHosts().size() > 0) ? x509.getHosts().size() : 0;
        int wildsCount = (x509.getWilds() != null && x509.getWilds().size() > 0) ? x509.getWilds().size() : 0;
        
        log.info("  Alias '{}': SAN hosts={}, SAN wilds={}", alias, hostsCount, wildsCount);
        
        if (hostsCount > 0) log.info("    Hosts: {}", x509.getHosts());
        if (wildsCount > 0) log.info("    Wilds: {}", x509.getWilds());
    }
    
    /**
     * 証明書情報を取得する（UI表示用）。
     * ファイルシステム上の最新の証明書を読み込んで情報を表示する。
     * 
     * @param certificatePath 証明書ファイルパス
     * @return 証明書情報の文字列、エラー時はnull
     */
    public static String getCertificateInfo(String certificatePath) {
        if (certificatePath == null || certificatePath.isEmpty()) {
            return null;
        }
        
        SslContextFactory.Server tempFactory = tryLoadServerFactory(certificatePath);
        if (tempFactory == null) {
            return null;
        }
        
        try {
            StringBuilder info = new StringBuilder();
            
            // 基本情報の構築
            info.append("ファイルパス: ").append(certificatePath).append("\n");
            info.append("証明書タイプ: PKCS#12 (P12/PFX)\n");
            info.append("\n");
            
            // KeyStoreから全エイリアスを取得
            KeyStore keyStore;
            List<String> allAliases;
            try {
                // SslContextFactory.getAliases()は秘密鍵を持つエイリアスのみを返すため、
                // setCertificateEntry()で追加したCA証明書などが含まれない
                // そのため、KeyStoreから直接全エイリアスを取得する
                keyStore = tempFactory.getKeyStore();
                allAliases = new ArrayList<>();
                Enumeration<String> aliasEnum = keyStore.aliases();
                while (aliasEnum.hasMoreElements()) {
                    allAliases.add(aliasEnum.nextElement());
                }
            } catch (Exception e) {
                log.warn("Failed to get aliases from keystore", e);
                return null;
            }
            
            info.append("証明書エイリアス: ").append(allAliases).append("\n\n");
            
            // 各エイリアスの証明書情報を表示
            for (String alias : allAliases) {
                try {
                    info.append("=== エイリアス: ").append(alias).append(" ===\n");
                    
                    // エイリアスのタイプを判定
                    boolean isKey = keyStore.isKeyEntry(alias);
                    boolean isCert = keyStore.isCertificateEntry(alias);
                    
                    if (isKey) {
                        info.append("タイプ: 秘密鍵 + 証明書チェーン\n");
                    } else if (isCert) {
                        info.append("タイプ: 証明書のみ (trustedCertEntry)\n");
                    }
                    
                    // 証明書の取得
                    X509 x509 = tempFactory.getX509(alias);
                    X509Certificate cert = null;
                    
                    try {
                        if (x509 != null) {
                            cert = x509.getCertificate();
                        } else if (isCert) {
                            // 証明書のみのエントリから直接取得
                            if (keyStore.getCertificate(alias) instanceof X509Certificate rawCert) {
                                cert = rawCert;
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to get certificate for alias: {}", alias, e);
                    }
                    
                    // 証明書詳細情報の構築
                    if (cert != null) {
                        try {
                            info.append("Subject: ").append(cert.getSubjectX500Principal().getName()).append("\n");
                            info.append("Issuer: ").append(cert.getIssuerX500Principal().getName()).append("\n");
                            info.append("Serial: ").append(cert.getSerialNumber().toString(16).toUpperCase()).append("\n");
                            info.append("有効期限: ").append(cert.getNotBefore()).append(" ～ ").append(cert.getNotAfter()).append("\n");
                            info.append("署名アルゴリズム: ").append(cert.getSigAlgName()).append("\n");
                        } catch (Exception e) {
                            log.debug("Failed to get certificate details for alias: {}", alias, e);
                            info.append("証明書詳細の取得に失敗しました\n");
                        }
                        
                        // SAN情報の構築
                        if (x509 != null) {
                            try {
                                int hostsCount = (x509.getHosts() != null && x509.getHosts().size() > 0) ? x509.getHosts().size() : 0;
                                int wildsCount = (x509.getWilds() != null && x509.getWilds().size() > 0) ? x509.getWilds().size() : 0;
                                
                                info.append("\n");
                                info.append("SAN (Subject Alternative Name):\n");
                                if (hostsCount > 0) {
                                    info.append("  完全一致ホスト (").append(hostsCount).append("件): ").append(x509.getHosts()).append("\n");
                                }
                                if (wildsCount > 0) {
                                    info.append("  ワイルドカード (").append(wildsCount).append("件): ").append(x509.getWilds()).append("\n");
                                }
                                if (hostsCount == 0 && wildsCount == 0) {
                                    info.append("  (なし)\n");
                                }
                            } catch (Exception e) {
                                log.debug("Failed to get SAN info for alias: {}", alias, e);
                            }
                        }
                    }
                    
                    info.append("\n");
                } catch (Exception e) {
                    log.warn("Failed to process alias: {}", alias, e);
                    info.append("エイリアス情報の取得に失敗しました\n\n");
                }
            }
            
            return info.toString();
        } finally {
            // 確実にリソースをクリーンアップ
            try {
                tempFactory.stop();
            } catch (Exception stopEx) {
                log.debug("Failed to stop temporary SslContextFactory", stopEx);
            }
        }
    }
}
