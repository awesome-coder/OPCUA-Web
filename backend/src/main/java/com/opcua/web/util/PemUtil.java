package com.opcua.web.util;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;

public final class PemUtil {

    private PemUtil() {
    }

    public static String toPem(X509Certificate certificate) {
        try {
            String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(certificate.getEncoded());
            return "-----BEGIN CERTIFICATE-----\n" + base64 + "\n-----END CERTIFICATE-----\n";
        } catch (Exception e) {
            throw new IllegalStateException("无法编码证书", e);
        }
    }

    public static X509Certificate parseCertificate(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8))
            );
            return certificate;
        } catch (Exception e) {
            throw new IllegalArgumentException("证书 PEM 格式无效: " + e.getMessage(), e);
        }
    }

    public static String toPrivateKeyPem(byte[] pkcs8) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pkcs8);
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    public static byte[] parsePrivateKeyPem(String pem) {
        String normalized = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    public static byte[] decodePemCertificate(String pem) {
        try {
            return CertificateUtil.decodeCertificate(pem.getBytes(StandardCharsets.UTF_8)).getEncoded();
        } catch (Exception e) {
            throw new IllegalArgumentException("证书 PEM 格式无效", e);
        }
    }
}
