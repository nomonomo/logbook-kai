package logbook.internal.ssl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

/**
 * SslCertificateUtil の有効期限テスト
 * このテストは以前（25.10.1～26.5.1）はサーバー証明書ファイルを作成し、サーバ証明書を読み込んでいた
 * 今後はルート証明書ファイルを読み込み、サーバ証明書を生成する方式を推奨するので、
 * このテストはサーバ証明書読み込みを削除した場合に不要になる
 */
class SslCertificateUtilExpiryTest {

    @TempDir
    Path tempDir;

    private Path testP12;

    @BeforeEach
    void setUp() throws Exception {
        Date notBefore = new Date(System.currentTimeMillis() - 60_000L);
        Date notAfter = Date.from(Instant.now().plus(29, ChronoUnit.DAYS));

        this.testP12 = tempDir.resolve("kancolle-expiring.p12");
        exportSelfSignedServerCertificateP12(testP12, notBefore, notAfter);
    }

    @Test
    void isExpiringWithinThirtyDays_trueFor29DaysAhead() {
        Date notAfter = Date.from(Instant.now().plus(29, ChronoUnit.DAYS));
        assertTrue(SslCertificateUtil.isServerCertificateExpiringWithinDays(
                notAfter, 30));
    }

    @Test
    void isExpiringWithinThirtyDays_falseFor90DaysAhead() {
        Date notAfter = Date.from(Instant.now().plus(90, ChronoUnit.DAYS));
        assertFalse(SslCertificateUtil.isServerCertificateExpiringWithinDays(
                notAfter, 30));
    }

    @Test
    void getServerCertificateNotAfter_readsNotAfterWithinThirtyDays() throws Exception {
        SslContextFactory.Server factory = SslCertificateUtil.tryLoadServerFactory(testP12.toString());
        try {
            Optional<Date> notAfter = SslCertificateUtil.getServerCertificateNotAfter(factory);
            assertTrue(notAfter.isPresent(), "notAfter should be present");
            assertTrue(
                    SslCertificateUtil.isServerCertificateExpiringWithinDays(
                            notAfter.get(), 30),
                    "cert should be within 30 days: " + notAfter.get());
        } finally {
            if (factory != null) {
                factory.stop();
            }
        }
    }

    private static void exportSelfSignedServerCertificateP12(Path outputP12Path, Date notBefore, Date notAfter)
            throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();

        X500Name subject = new X500Name(String.format(
                "CN=%s, OU=Development, O=%s, L=Tokyo, ST=Tokyo, C=JP",
                SslCertificateUtil.DEFAULT_CERT_CN,
                SslCertificateUtil.DEFAULT_CERT_ORG));

        BigInteger serialNumber = new BigInteger(64, new SecureRandom());

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                serialNumber,
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic());

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

        ExtendedKeyUsage extendedKeyUsage = new ExtendedKeyUsage(new KeyPurposeId[] {
                KeyPurposeId.id_kp_serverAuth,
                KeyPurposeId.id_kp_clientAuth
        });
        certBuilder.addExtension(Extension.extendedKeyUsage, false, extendedKeyUsage);

        certBuilder.addExtension(Extension.keyUsage, false,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

        GeneralName[] generalNames = new GeneralName[] {
                new GeneralName(GeneralName.dNSName, SslCertificateUtil.DEFAULT_CERT_CN),
                new GeneralName(GeneralName.dNSName,
                        SslCertificateUtil.DEFAULT_CERT_CN.replace("*.", ""))
        };
        certBuilder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(generalNames));

        SubjectKeyIdentifier subjectKeyId = extUtils.createSubjectKeyIdentifier(keyPair.getPublic());
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, subjectKeyId);

        ContentSigner signer = new JcaContentSignerBuilder("SHA384withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());

        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(certHolder);

        KeyStore serverKeyStore = KeyStore.getInstance("PKCS12");
        serverKeyStore.load(null, null);

        Certificate[] chain = new Certificate[] { cert };
        serverKeyStore.setKeyEntry(
                SslCertificateUtil.SERVER_CERT_ALIAS,
                keyPair.getPrivate(),
                SslCertificateUtil.SERVER_CERTIFICATE_PASSWORD.toCharArray(),
                chain);

        try (FileOutputStream fos = new FileOutputStream(outputP12Path.toFile())) {
            serverKeyStore.store(fos, SslCertificateUtil.SERVER_CERTIFICATE_PASSWORD.toCharArray());
        }

        // Jetty 側の読み取りに関係ないが、最低限の検証のために空でないことを確認
        if (outputP12Path.toFile().length() <= 0) {
            throw new IllegalStateException("P12ファイルが空でした: " + outputP12Path);
        }
    }
}
