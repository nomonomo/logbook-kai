package logbook.internal.gui;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * 証明書作成・管理サービス
 */
@Slf4j
public class CertificateService {

    /**
     * 証明書情報を保持するレコード
     */
    public record CertificateInfo(
        String filePath,
        String subject,
        String issuer,
        String serialNumber,
        Date notBefore,
        Date notAfter,
        String sigAlgName
    ) {}

    /**
     * BouncyCastleプロバイダーを初期化
     */
    private void initializeBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * 既存のCA証明書を使用してサーバー証明書を作成
     *
     * @param caCertPath CA証明書のパス
     * @param caKeyPath CA秘密鍵のパス
     * @param caKeyPassword CA秘密鍵のパスワード
     * @param cn Common Name
     * @param org Organization
     * @param outputDir 出力先ディレクトリ
     * @param serverPassword サーバー証明書のパスワード
     * @param caPasswordDefault CA証明書のデフォルトパスワード（未使用）
     * @throws Exception 証明書作成に失敗した場合
     */
    public void createServerCertificateWithExistingCA(String caCertPath, String caKeyPath, String caKeyPassword,
            String cn, String org, String outputDir, String serverPassword, String caPasswordDefault) throws Exception {
        log.info("既存のCA証明書を使用してサーバー証明書を作成します");
        
        initializeBouncyCastle();
        
        // CA証明書を読み込む（PEM形式）
        X509Certificate caCert = loadCertificateFromPEM(caCertPath);
        log.info("CA証明書を読み込みました: {}", caCert.getSubjectX500Principal().getName());
        
        // CA秘密鍵を読み込む（PEM形式）
        PrivateKey caPrivateKey = loadPrivateKeyFromPEM(caKeyPath, caKeyPassword);
        log.info("CA秘密鍵を読み込みました");
        
        // サーバー証明書用のキーペアを生成
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair serverKeyPair = keyGen.generateKeyPair();
        
        // サーバー証明書を作成（CAで署名）
        X509Certificate serverCert = createServerCertificate(
            serverKeyPair,
            caPrivateKey,
            caCert,
            cn, org
        );
        
        // サーバー証明書をP12ファイルに保存
        KeyStore serverKeyStore = KeyStore.getInstance("PKCS12");
        serverKeyStore.load(null, null);
        
        // batスクリプトと同じ順序で処理：
        // 1. サーバー証明書の鍵ペアと証明書チェーンを追加（Step 3 + Step 6-2相当）
        Certificate[] chain = new Certificate[]{serverCert, caCert};
        serverKeyStore.setKeyEntry("kancolle-cert", serverKeyPair.getPrivate(), 
            serverPassword.toCharArray(), chain);
        
        // 2. CA証明書を別エイリアスで追加（Step 6-1相当）
        serverKeyStore.setCertificateEntry("logbook-ca", caCert);
        
        // P12ファイルに保存
        String serverCertPath = Paths.get(outputDir, "kancolle.p12").toString();
        try (FileOutputStream fos = new FileOutputStream(serverCertPath)) {
            serverKeyStore.store(fos, serverPassword.toCharArray());
        }
        
        log.info("サーバー証明書を作成しました: {}", serverCertPath);
        
        // 検証: 作成した証明書のエイリアスを確認
        try {
            java.util.List<String> aliases = getKeystoreAliases(serverCertPath, serverPassword);
            log.info("作成したkancolle.p12のエイリアス数: {}", aliases.size());
            log.info("エイリアス一覧: {}", aliases);
        } catch (Exception e) {
            log.warn("証明書エイリアスの検証に失敗", e);
        }
    }

    /**
     * 新規にCA証明書とサーバー証明書を作成
     *
     * @param cn Common Name
     * @param org Organization
     * @param outputDir 出力先ディレクトリ
     * @param serverPassword サーバー証明書のパスワード
     * @param caPassword CA証明書のパスワード
     * @throws Exception 証明書作成に失敗した場合
     */
    public void createNewCAAndServerCertificate(String cn, String org, String outputDir, 
            String serverPassword, String caPassword) throws Exception {
        log.info("新規にCA証明書とサーバー証明書を作成します");
        
        initializeBouncyCastle();
        
        // 1. CA証明書用のキーペアを生成
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair caKeyPair = keyGen.generateKeyPair();
        
        // 2. CA証明書を作成（自己署名）
        X509Certificate caCert = createCACertificate(caKeyPair);
        
        // 3. CA証明書をP12ファイルに保存
        KeyStore caKeyStore = KeyStore.getInstance("PKCS12");
        caKeyStore.load(null, null);
        caKeyStore.setKeyEntry("logbook-ca", caKeyPair.getPrivate(), 
            caPassword.toCharArray(), new Certificate[]{caCert});
        
        String caCertP12Path = Paths.get(outputDir, "logbook-ca.p12").toString();
        try (FileOutputStream fos = new FileOutputStream(caCertP12Path)) {
            caKeyStore.store(fos, caPassword.toCharArray());
        }
        
        // 4. CA証明書をPEM形式でエクスポート
        String caCertPemPath = Paths.get(outputDir, "logbook-ca.crt").toString();
        exportCertificateToPEM(caCert, caCertPemPath);
        
        // 5. サーバー証明書用のキーペアを生成
        KeyPair serverKeyPair = keyGen.generateKeyPair();
        
        // 6. サーバー証明書を作成（CAで署名）
        X509Certificate serverCert = createServerCertificate(
            serverKeyPair,
            caKeyPair.getPrivate(),
            caCert,
            cn, org
        );
        
        // 7. サーバー証明書をP12ファイルに保存
        KeyStore serverKeyStore = KeyStore.getInstance("PKCS12");
        serverKeyStore.load(null, null);
        
        // 証明書チェーンの検証ログ
        log.info("CA証明書 Subject: {}", caCert.getSubjectX500Principal());
        log.info("CA証明書 Issuer: {}", caCert.getIssuerX500Principal());
        log.info("サーバー証明書 Subject: {}", serverCert.getSubjectX500Principal());
        log.info("サーバー証明書 Issuer: {}", serverCert.getIssuerX500Principal());
        
        // 証明書チェーンの有効性を手動で検証
        try {
            serverCert.verify(caCert.getPublicKey());
            log.info("サーバー証明書の署名検証: 成功");
        } catch (Exception e) {
            log.error("サーバー証明書の署名検証: 失敗", e);
            throw new Exception("サーバー証明書の署名が不正です", e);
        }
        
        // batスクリプトと同じ順序で処理：
        // 1. サーバー証明書の鍵ペアと証明書チェーンを追加（Step 3 + Step 6-2相当）
        Certificate[] chain = new Certificate[]{serverCert, caCert};
        serverKeyStore.setKeyEntry("kancolle-cert", serverKeyPair.getPrivate(), 
            serverPassword.toCharArray(), chain);
        
        // 2. CA証明書を別エイリアスで追加（Step 6-1相当）
        serverKeyStore.setCertificateEntry("logbook-ca", caCert);
        
        String serverCertPath = Paths.get(outputDir, "kancolle.p12").toString();
        try (FileOutputStream fos = new FileOutputStream(serverCertPath)) {
            serverKeyStore.store(fos, serverPassword.toCharArray());
        }
        
        log.info("CA証明書を作成しました: {}", caCertP12Path);
        log.info("CA証明書(PEM)を作成しました: {}", caCertPemPath);
        log.info("サーバー証明書を作成しました: {}", serverCertPath);
        
        // 検証: 作成した証明書のエイリアスを確認
        try {
            java.util.List<String> aliases = getKeystoreAliases(serverCertPath, serverPassword);
            log.info("作成したkancolle.p12のエイリアス数: {}", aliases.size());
            log.info("エイリアス一覧: {}", aliases);
        } catch (Exception e) {
            log.warn("証明書エイリアスの検証に失敗", e);
        }
    }

    /**
     * CA証明書を作成（自己署名）
     *
     * @param keyPair キーペア
     * @return CA証明書
     * @throws Exception 証明書作成に失敗した場合
     */
    private X509Certificate createCACertificate(KeyPair keyPair) throws Exception {
        // DN（Distinguished Name）を設定
        X500Name subject = new X500Name("CN=Logbook-Kai Root CA, OU=Development, O=Logbook, L=Tokyo, ST=Tokyo, C=JP");
        
        // 有効期限: 10年
        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + 3650L * 24L * 60L * 60L * 1000L);
        
        // シリアル番号
        BigInteger serialNumber = new BigInteger(64, new SecureRandom());
        
        // 証明書ビルダーを作成
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            subject,              // issuer（自己署名なのでsubjectと同じ）
            serialNumber,
            notBefore,
            notAfter,
            subject,              // subject
            keyPair.getPublic()
        );
        
        // CA制約拡張を追加（CA証明書であることを示す）
        // RFC 5280: CA証明書では BasicConstraints を Critical=true にすることが推奨
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        
        // KeyUsage拡張を追加（CA証明書の用途を示す）
        // RFC 5280: CA証明書では KeyUsage を Critical=true にすることが推奨
        // keyCertSign: 証明書に署名する権限、cRLSign: CRLに署名する権限
        certBuilder.addExtension(Extension.keyUsage, true, 
            new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        
        // SubjectKeyIdentifier拡張を追加（CA証明書の識別子）
        // RFC 5280: SubjectKeyIdentifier は通常 Critical=false（非クリティカル）
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        SubjectKeyIdentifier subjectKeyId = extUtils.createSubjectKeyIdentifier(keyPair.getPublic());
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, subjectKeyId);
        
        // 署名器を作成
        ContentSigner signer = new JcaContentSignerBuilder("SHA384withRSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keyPair.getPrivate());
        
        // 証明書を生成
        X509CertificateHolder certHolder = certBuilder.build(signer);
        
        // X509Certificateに変換
        return new JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(certHolder);
    }

    /**
     * サーバー証明書を作成（CAで署名）
     *
     * @param serverKeyPair サーバーのキーペア
     * @param caPrivateKey CA秘密鍵
     * @param caCert CA証明書
     * @param cn Common Name
     * @param org Organization
     * @return サーバー証明書
     * @throws Exception 証明書作成に失敗した場合
     */
    private X509Certificate createServerCertificate(KeyPair serverKeyPair, PrivateKey caPrivateKey,
            X509Certificate caCert, String cn, String org) throws Exception {
        
        // DN（Distinguished Name）を設定
        X500Name subject = new X500Name(
            String.format("CN=%s, OU=Development, O=%s, L=Tokyo, ST=Tokyo, C=JP", cn, org));
        
        // CA証明書からissuerを取得（JcaX509CertificateHolderを使用）
        X500Name issuer = new JcaX509CertificateHolder(caCert).getSubject();
        
        log.info("サーバー証明書作成: Subject={}, Issuer={}", subject, issuer);
        
        // 有効期限: 10年
        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + 3650L * 24L * 60L * 60L * 1000L);
        
        // シリアル番号
        BigInteger serialNumber = new BigInteger(64, new SecureRandom());
        
        // 証明書ビルダーを作成
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            issuer,               // issuer（CA）
            serialNumber,
            notBefore,
            notAfter,
            subject,              // subject（サーバー）
            serverKeyPair.getPublic()
        );
        
        // 拡張ユーティリティ
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        
        // AuthorityKeyIdentifier拡張を追加（CA証明書のSubjectKeyIdentifierを参照）
        AuthorityKeyIdentifier authorityKeyId = extUtils.createAuthorityKeyIdentifier(caCert);
        certBuilder.addExtension(Extension.authorityKeyIdentifier, false, authorityKeyId);
        
        // ExtendedKeyUsage拡張を追加（serverAuth, clientAuth）
        ExtendedKeyUsage extendedKeyUsage = new ExtendedKeyUsage(new KeyPurposeId[]{
            KeyPurposeId.id_kp_serverAuth,
            KeyPurposeId.id_kp_clientAuth
        });
        certBuilder.addExtension(Extension.extendedKeyUsage, false, extendedKeyUsage);
        
        // KeyUsage拡張を追加（DigitalSignature, KeyEncipherment）
        certBuilder.addExtension(Extension.keyUsage, false, 
            new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        
        // SAN（Subject Alternative Name）拡張を追加
        GeneralName[] generalNames = new GeneralName[]{
            new GeneralName(GeneralName.dNSName, cn),
            new GeneralName(GeneralName.dNSName, cn.replace("*.", ""))
        };
        certBuilder.addExtension(Extension.subjectAlternativeName, false, 
            new GeneralNames(generalNames));
        
        // SubjectKeyIdentifier拡張を追加（サーバー証明書の識別子）
        SubjectKeyIdentifier subjectKeyId = extUtils.createSubjectKeyIdentifier(serverKeyPair.getPublic());
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, subjectKeyId);
        
        // 署名器を作成（CA秘密鍵で署名）
        ContentSigner signer = new JcaContentSignerBuilder("SHA384withRSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(caPrivateKey);
        
        // 証明書を生成
        X509CertificateHolder certHolder = certBuilder.build(signer);
        
        // X509Certificateに変換
        return new JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(certHolder);
    }

    /**
     * 証明書をPEM形式でエクスポート
     *
     * @param cert 証明書
     * @param filePath 出力ファイルパス
     * @throws Exception エクスポートに失敗した場合
     */
    private void exportCertificateToPEM(X509Certificate cert, String filePath) throws Exception {
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(filePath))) {
            pemWriter.writeObject(cert);
        }
    }

    /**
     * PEM形式のCA証明書を読み込む
     *
     * @param filePath ファイルパス
     * @return X509証明書
     * @throws Exception 読み込みに失敗した場合
     */
    public X509Certificate loadCertificateFromPEM(String filePath) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(fis);
        }
    }

    /**
     * 証明書情報を取得
     *
     * @param certPath 証明書ファイルパス
     * @return 証明書情報
     * @throws Exception 読み込みに失敗した場合
     */
    public CertificateInfo getCertificateInfo(String certPath) throws Exception {
        X509Certificate cert = loadCertificateFromPEM(certPath);
        
        return new CertificateInfo(
            certPath,
            cert.getSubjectX500Principal().getName(),
            cert.getIssuerX500Principal().getName(),
            cert.getSerialNumber().toString(16).toUpperCase(),
            cert.getNotBefore(),
            cert.getNotAfter(),
            cert.getSigAlgName()
        );
    }

    /**
     * PKCS12ファイルの全エイリアスを取得（検証用）
     *
     * @param filePath PKCS12ファイルパス
     * @param password パスワード
     * @return エイリアスのリスト
     * @throws Exception 読み込みに失敗した場合
     */
    public java.util.List<String> getKeystoreAliases(String filePath, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(filePath)) {
            keyStore.load(fis, password.toCharArray());
        }
        
        java.util.List<String> aliases = new java.util.ArrayList<>();
        java.util.Enumeration<String> aliasEnum = keyStore.aliases();
        while (aliasEnum.hasMoreElements()) {
            aliases.add(aliasEnum.nextElement());
        }
        
        log.info("PKCS12ファイル {} のエイリアス: {}", filePath, aliases);
        return aliases;
    }

    /**
     * PEM形式のRSA秘密鍵を読み込む
     *
     * @param filePath ファイルパス
     * @param password パスワード
     * @return 秘密鍵
     * @throws Exception 読み込みに失敗した場合
     */
    public PrivateKey loadPrivateKeyFromPEM(String filePath, String password) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath);
             java.io.InputStreamReader isr = new java.io.InputStreamReader(fis);
             PEMParser pemParser = new PEMParser(isr)) {
            
            Object object = pemParser.readObject();
            
            initializeBouncyCastle();
            
            if (object instanceof PEMEncryptedKeyPair) {
                // 暗号化された秘密鍵の場合
                PEMEncryptedKeyPair encryptedKeyPair = (PEMEncryptedKeyPair) object;
                JcePEMDecryptorProviderBuilder decryptorBuilder = 
                    new JcePEMDecryptorProviderBuilder()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME);
                PEMKeyPair keyPair = encryptedKeyPair.decryptKeyPair(
                    decryptorBuilder.build(password != null ? password.toCharArray() : new char[0]));
                JcaPEMKeyConverter converter = 
                    new JcaPEMKeyConverter()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME);
                return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            } else if (object instanceof PEMKeyPair) {
                // 暗号化されていない秘密鍵の場合
                PEMKeyPair keyPair = (PEMKeyPair) object;
                JcaPEMKeyConverter converter = 
                    new JcaPEMKeyConverter()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME);
                return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            } else if (object instanceof PrivateKeyInfo) {
                // PKCS#8形式の秘密鍵
                JcaPEMKeyConverter converter = 
                    new JcaPEMKeyConverter()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME);
                return converter.getPrivateKey((PrivateKeyInfo) object);
            } else {
                throw new IllegalArgumentException("Unknown object type: " + object.getClass().getName());
            }
        }
    }
}

