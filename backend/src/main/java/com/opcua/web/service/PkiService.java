package com.opcua.web.service;

import com.opcua.web.config.AppProperties;
import com.opcua.web.dto.PkiInfoView;
import com.opcua.web.util.PemUtil;
import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PkiService {

    public static final String APPLICATION_URI = "urn:opcua-web:console:client";
    public static final String APPLICATION_NAME = "OPC UA Web Console";

    private static final Logger log = LoggerFactory.getLogger(PkiService.class);

    private final AppProperties properties;
    private X509Certificate certificate;
    private PrivateKey privateKey;
    private KeyPair keyPair;
    private String certificatePem;
    private Path pkiDir;

    public PkiService(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        try {
            pkiDir = Path.of(properties.getDataDir()).toAbsolutePath().normalize().resolve("pki");
            Files.createDirectories(pkiDir);
            Path certFile = pkiDir.resolve("client-cert.pem");
            Path keyFile = pkiDir.resolve("client-key.pem");
            if (Files.exists(certFile) && Files.exists(keyFile)) {
                load(certFile, keyFile);
            } else {
                generate(certFile, keyFile);
            }
            log.info("OPC UA 客户端证书指纹 SHA-256: {}", sha256Fingerprint());
        } catch (Exception e) {
            throw new IllegalStateException("初始化 OPC UA 客户端证书失败: " + e.getMessage(), e);
        }
    }

    private void load(Path certFile, Path keyFile) throws Exception {
        String pem = Files.readString(certFile);
        certificatePem = pem;
        certificate = PemUtil.parseCertificate(pem);
        byte[] pkcs8 = PemUtil.parsePrivateKeyPem(Files.readString(keyFile));
        privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        keyPair = new KeyPair(certificate.getPublicKey(), privateKey);
        certificate.checkValidity();
    }

    private void generate(Path certFile, Path keyFile) throws Exception {
        KeyPair generated = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
        String hostName;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostName = "localhost";
        }
        SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(generated)
            .setCommonName("OPC UA Web Console")
            .setOrganization("OPC UA Web")
            .setOrganizationalUnit("Integration")
            .setLocalityName("Local")
            .setStateName("Local")
            .setCountryCode("CN")
            .setApplicationUri(APPLICATION_URI)
            .addDnsName("localhost")
            .addDnsName(hostName)
            .addIpAddress("127.0.0.1");
        X509Certificate generatedCertificate = builder.build();
        String pem = PemUtil.toPem(generatedCertificate);
        Files.writeString(certFile, pem);
        Files.writeString(keyFile, PemUtil.toPrivateKeyPem(generated.getPrivate().getEncoded()));
        certificate = generatedCertificate;
        certificatePem = pem;
        privateKey = generated.getPrivate();
        keyPair = generated;
        log.info("已生成 OPC UA 客户端证书: {}", certFile);
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public String getCertificatePem() {
        return certificatePem;
    }

    public PkiInfoView info() {
        return new PkiInfoView(
            certificate.getSubjectX500Principal().getName(),
            certificate.getIssuerX500Principal().getName(),
            certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT),
            DateTimeFormatter.ISO_INSTANT.format(certificate.getNotBefore().toInstant()),
            DateTimeFormatter.ISO_INSTANT.format(certificate.getNotAfter().toInstant()),
            APPLICATION_URI,
            sha256Fingerprint(),
            sha1Fingerprint(),
            privateKey.getAlgorithm(),
            ((java.security.interfaces.RSAPrivateKey) privateKey).getModulus().bitLength(),
            certificatePem
        );
    }

    public String sha256Fingerprint() {
        return fingerprint("SHA-256");
    }

    public String sha1Fingerprint() {
        return fingerprint("SHA-1");
    }

    private String fingerprint(String algorithm) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance(algorithm).digest(certificate.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                if (!sb.isEmpty()) sb.append(':');
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "n/a";
        }
    }

    public Path getPkiDir() {
        return pkiDir;
    }

    public String getKeyFileBase64() {
        try {
            return Base64.getEncoder().encodeToString(privateKey.getEncoded());
        } catch (Exception e) {
            return "";
        }
    }
}
