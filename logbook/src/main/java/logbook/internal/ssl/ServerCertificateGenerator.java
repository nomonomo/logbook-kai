package logbook.internal.ssl;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * ルート CA によるサーバー証明書の生成と PKCS12 KeyStore の組立を行う。
 */
public final class ServerCertificateGenerator {

    /** 本番利用時のサーバー証明書有効日数（デフォルト） */
    public static final int DEFAULT_SERVER_VALIDITY_DAYS = 365;

    private static final int RSA_KEY_SIZE = 2048;

    /**
     * ルート CA の証明書と秘密鍵。
     */
    public record CaIdentity(X509Certificate certificate, PrivateKey privateKey) {}

    /**
     * 署名済みサーバー証明書とその鍵ペア。
     */
    public record SignedServerCertificate(KeyPair keyPair, X509Certificate certificate) {}

    private ServerCertificateGenerator() {
    }

    /**
     * BouncyCastle プロバイダーを登録する。
     */
    public static void ensureBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * RSA 鍵ペアを生成する。
     */
    public static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(RSA_KEY_SIZE, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    /**
     * PKCS12 からルート CA（秘密鍵付き）を読み込む。
     */
    public static CaIdentity loadCaIdentityFromPkcs12(String rootCertificatePath, String rootPassword)
            throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(rootCertificatePath)) {
            keyStore.load(fis, rootPassword.toCharArray());
        }

        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!keyStore.isKeyEntry(alias)) {
                continue;
            }
            Key key = keyStore.getKey(alias, rootPassword.toCharArray());
            if (!(key instanceof PrivateKey privateKey)) {
                continue;
            }
            Certificate certificate = keyStore.getCertificate(alias);
            if (certificate instanceof X509Certificate caCertificate) {
                return new CaIdentity(caCertificate, privateKey);
            }
        }
        throw new IllegalStateException("ルート証明書ファイルに秘密鍵付き証明書が含まれていません");
    }

    /**
     * ルート CA でサーバー証明書を署名する。
     */
    public static SignedServerCertificate createSignedServerCertificate(
            CaIdentity caIdentity, String cn, String org, int validityDays) throws Exception {
        ensureBouncyCastleProvider();
        KeyPair serverKeyPair = generateRsaKeyPair();
        X509Certificate serverCert = signServerCertificate(
                serverKeyPair,
                caIdentity.privateKey(),
                caIdentity.certificate(),
                cn,
                org,
                validityDays);
        return new SignedServerCertificate(serverKeyPair, serverCert);
    }

    /**
     * サーバー証明書用 PKCS12 KeyStore を組み立てる。
     */
    public static KeyStore buildServerKeyStore(
            SignedServerCertificate signedServer,
            X509Certificate caCertificate,
            char[] keyStorePassword) throws Exception {
        KeyStore serverKeyStore = KeyStore.getInstance("PKCS12");
        serverKeyStore.load(null, null);
        Certificate[] chain = new Certificate[] { signedServer.certificate(), caCertificate };
        serverKeyStore.setKeyEntry(
                SslCertificateUtil.SERVER_CERT_ALIAS,
                signedServer.keyPair().getPrivate(),
                keyStorePassword,
                chain);
        serverKeyStore.setCertificateEntry(SslCertificateUtil.ROOT_CERT_ALIAS, caCertificate);
        return serverKeyStore;
    }

    /**
     * KeyStore を PKCS12 ファイルに書き出す。
     */
    public static void writeKeyStoreToFile(KeyStore keyStore, Path outputPath, char[] password)
            throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            keyStore.store(fos, password);
        }
    }

    /**
     * CA 秘密鍵でサーバー証明書に署名する。
     */
    public static X509Certificate signServerCertificate(
            KeyPair serverKeyPair,
            PrivateKey caPrivateKey,
            X509Certificate caCert,
            String cn,
            String org,
            int validityDays) throws Exception {
        // サーバー証明書の DN（Distinguished Name）を設定
        X500Name subject = new X500Name(
                String.format("CN=%s, OU=Development, O=%s, L=Tokyo, ST=Tokyo, C=JP", cn, org));
        // CA証明書からissuerを取得（JcaX509CertificateHolderを使用）
        X500Name issuer = new JcaX509CertificateHolder(caCert).getSubject();

        // 有効期限を設定
        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + (long) validityDays * 24L * 60L * 60L * 1000L);

        // シリアル番号を生成
        BigInteger serialNumber = new BigInteger(64, new SecureRandom());

        // 証明書ビルダーを作成
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serialNumber,
                notBefore,
                notAfter,
                subject,
                serverKeyPair.getPublic());

        // 拡張ユーティリティを作成
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

        // AuthorityKeyIdentifier拡張を追加（CA証明書のSubjectKeyIdentifierを参照）
        AuthorityKeyIdentifier authorityKeyId = extUtils.createAuthorityKeyIdentifier(caCert);
        certBuilder.addExtension(Extension.authorityKeyIdentifier, false, authorityKeyId);

        // ExtendedKeyUsage拡張を追加（serverAuth, clientAuth）
        ExtendedKeyUsage extendedKeyUsage = new ExtendedKeyUsage(new KeyPurposeId[] {
                KeyPurposeId.id_kp_serverAuth,
                KeyPurposeId.id_kp_clientAuth
        });
        certBuilder.addExtension(Extension.extendedKeyUsage, false, extendedKeyUsage);

        // KeyUsage拡張を追加（DigitalSignature, KeyEncipherment）
        certBuilder.addExtension(Extension.keyUsage, false,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

        // SAN（Subject Alternative Name）拡張を追加
        GeneralName[] generalNames = new GeneralName[] {
                new GeneralName(GeneralName.dNSName, cn),
                new GeneralName(GeneralName.dNSName, cn.replace("*.", ""))
        };
        certBuilder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(generalNames));

        // SubjectKeyIdentifier拡張を追加（サーバー証明書の識別子）
        SubjectKeyIdentifier subjectKeyId = extUtils.createSubjectKeyIdentifier(serverKeyPair.getPublic());
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, subjectKeyId);

        // 署名器を作成（CA秘密鍵で署名）
        ContentSigner signer = new JcaContentSignerBuilder("SHA384withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(caPrivateKey);

        // 証明書ホルダーを作成
        X509CertificateHolder certHolder = certBuilder.build(signer);
        
        // 証明書を返す
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(certHolder);
    }
}
