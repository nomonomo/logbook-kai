package logbook.internal.ssl;

import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.ssl.X509;

import lombok.extern.slf4j.Slf4j;

/**
 * SSL証明書関連のユーティリティクラス
 */
@Slf4j
public final class SslCertificateUtil {

    /** サーバー証明書（kancolle.p12）のパスワード */
    static final String SERVER_CERTIFICATE_PASSWORD = "changeit";

    /** ルート証明書（logbook-ca.p12）のパスワード */
    public static final String ROOT_CERTIFICATE_PASSWORD = "capassword";

    /** サーバー証明書の Common Name */
    public static final String DEFAULT_CERT_CN = "*.kancolle-server.com";

    /** サーバー証明書の Organization */
    public static final String DEFAULT_CERT_ORG = "Logbook-Kai";

    public static final String SERVER_CERT_ALIAS = "kancolle-cert";
    public static final String ROOT_CERT_ALIAS = "logbook-ca";
    
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
        serverFactory.setKeyStorePassword(SERVER_CERTIFICATE_PASSWORD);
        serverFactory.setKeyStoreType("PKCS12");
        serverFactory.setCertAlias(SERVER_CERT_ALIAS);
        serverFactory.setSniRequired(false); // SNI検証を無効化
        
        try {
            serverFactory.start();
            logCertificateInfo(serverFactory, keystorePath);
        } catch (Exception e) {
            log.info("証明書の読み込みに失敗しました（パス: {}）", keystorePath, e);
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
            log.warn("証明書パスが空です");
            return null;
        }
        
        if (existingFactory == null) {
            // ファクトリが初期化されていない場合、新規に作成
            log.debug("SSLサーバーファクトリが未初期化のため、新規作成します");
            return tryLoadServerFactory(keystorePath);
        }

        // 既存のファクトリがある場合はreload()を使用
        try {
            existingFactory.reload(scf -> {
                // 新しい証明書パスを設定
                scf.setKeyStorePath(keystorePath);
                scf.setKeyStorePassword(SERVER_CERTIFICATE_PASSWORD);
                scf.setKeyStoreType("PKCS12");
                scf.setCertAlias(SERVER_CERT_ALIAS);
                log.debug("新しい証明書パスでSSL設定を更新しました: {}", keystorePath);
            });
                
            log.info("証明書を正常にリロードしました");
            logCertificateInfo(existingFactory, keystorePath);
            return existingFactory;
        } catch (Exception e) {
            log.error("サーバー証明書のリロードに失敗しました", e);
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
            log.info("証明書を読み込みました（パス: {}）", keystorePath);
            log.info("証明書エイリアス: {}", aliases);
            
            // 各エイリアスの証明書情報を表示
            aliases.forEach(alias -> logCertificateAlias(serverFactory, alias));
        } catch (Exception e) {
            log.warn("証明書チェーンの詳細情報のログ出力に失敗しました", e);
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
        
        log.info("  エイリアス '{}': SANホスト={}, SANワイルドカード={}", alias, hostsCount, wildsCount);
        
        if (hostsCount > 0) log.info("    ホスト: {}", x509.getHosts());
        if (wildsCount > 0) log.info("    ワイルドカード: {}", x509.getWilds());
    }
    
    /**
     * サーバー証明書の有効期限（notAfter）を取得する。
     * {@code start()} 済みの Jetty サーバー SSL ファクトリから読み取る。
     *
     * @param serverFactory SSL サーバーファクトリ
     * @return 有効期限。読み込みに失敗した場合は空
     */
    public static Optional<Date> getServerCertificateNotAfter(SslContextFactory.Server serverFactory) {
        if (serverFactory == null) {
            return Optional.empty();
        }
        try {
            X509Certificate certificate = findServerCertificate(serverFactory);
            if (certificate == null) {
                return Optional.empty();
            }
            return Optional.of(certificate.getNotAfter());
        } catch (Exception e) {
            log.debug("サーバー証明書の有効期限取得に失敗しました", e);
            return Optional.empty();
        }
    }

    /**
     * サーバー証明書の有効期限が指定日数以内かどうかを判定する。
     *
     * @param notAfter 有効期限（notAfter）
     * @param warningDays 警告閾値（日）
     * @return 有効期限が現在から {@code warningDays} 日以内の場合 {@code true}
     */
    public static boolean isServerCertificateExpiringWithinDays(Date notAfter, int warningDays) {
        if (notAfter == null || warningDays < 0) {
            return false;
        }
        LocalDate expiryDate = notAfter.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate warningThreshold = LocalDate.now().plusDays(warningDays);
        return expiryDate.isBefore(warningThreshold);
    }

    private static X509Certificate findServerCertificate(SslContextFactory.Server serverFactory) throws Exception {
        X509 x509 = serverFactory.getX509(SERVER_CERT_ALIAS);
        if (x509 != null) {
            return x509.getCertificate();
        }
        KeyStore keyStore = serverFactory.getKeyStore();
        if (keyStore != null) {
            return findServerCertificate(keyStore);
        }
        return null;
    }

    private static X509Certificate findServerCertificate(KeyStore keyStore) throws Exception {
        if (keyStore.containsAlias(SERVER_CERT_ALIAS) && keyStore.isKeyEntry(SERVER_CERT_ALIAS)) {
            Certificate certificate = keyStore.getCertificate(SERVER_CERT_ALIAS);
            if (certificate instanceof X509Certificate x509) {
                return x509;
            }
        }
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!keyStore.isKeyEntry(alias)) {
                continue;
            }
            Key key = keyStore.getKey(alias, SERVER_CERTIFICATE_PASSWORD.toCharArray());
            if (!(key instanceof PrivateKey)) {
                continue;
            }
            Certificate certificate = keyStore.getCertificate(alias);
            if (certificate instanceof X509Certificate x509) {
                return x509;
            }
        }
        return null;
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
                log.warn("キーストアからエイリアスの取得に失敗しました", e);
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
                        log.debug("エイリアスの証明書取得に失敗しました: {}", alias, e);
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
                            log.debug("証明書の詳細情報取得に失敗しました（エイリアス: {}）", alias, e);
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
                                log.debug("SAN情報の取得に失敗しました（エイリアス: {}）", alias, e);
                            }
                        }
                    }
                    
                    info.append("\n");
                } catch (Exception e) {
                    log.warn("エイリアスの処理に失敗しました: {}", alias, e);
                    info.append("エイリアス情報の取得に失敗しました\n\n");
                }
            }
            
            return info.toString();
        } finally {
            // 確実にリソースをクリーンアップ
            try {
                tempFactory.stop();
            } catch (Exception stopEx) {
                log.debug("一時的なSslContextFactoryの停止に失敗しました", stopEx);
            }
        }
    }
}
