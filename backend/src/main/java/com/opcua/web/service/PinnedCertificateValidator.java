package com.opcua.web.service;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.milo.opcua.stack.client.security.ClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Certificate validation for OPC UA secure channels.
 * <p>
 * In "trust all" mode this intentionally accepts untrusted certificates and is
 * suitable for lab commissioning only. In pinned mode the SHA-256 fingerprint of the
 * leaf server certificate must match the certificate configured on the device.
 */
public class PinnedCertificateValidator implements ClientCertificateValidator {

    private static final Logger log = LoggerFactory.getLogger(PinnedCertificateValidator.class);

    private final boolean trustAll;
    private final byte[] pinnedSha256;
    private final AtomicBoolean warned = new AtomicBoolean(false);

    public PinnedCertificateValidator(boolean trustAll, X509Certificate pinnedCertificate) {
        this.trustAll = trustAll;
        this.pinnedSha256 = pinnedCertificate == null ? null : sha256(pinnedCertificate);
    }

    @Override
    public void validateCertificateChain(List<X509Certificate> certificateChain) throws UaException {
        validate(certificateChain);
    }

    @Override
    public void validateCertificateChain(
        List<X509Certificate> certificateChain,
        String applicationUri,
        String... validHostNames
    ) throws UaException {
        validate(certificateChain);
    }

    private void validate(List<X509Certificate> chain) throws UaException {
        if (chain == null || chain.isEmpty()) {
            throw new UaException(StatusCodes.Bad_CertificateInvalid, "服务器没有提供证书");
        }
        X509Certificate leaf = chain.get(0);
        try {
            leaf.checkValidity();
        } catch (Exception e) {
            throw new UaException(StatusCodes.Bad_CertificateTimeInvalid, "服务器证书不在有效期内");
        }

        if (trustAll) {
            if (warned.compareAndSet(false, true)) {
                log.warn("安全连接正在使用“信任所有服务器证书”模式，仅建议用于调试: {}",
                    leaf.getSubjectX500Principal().getName());
            }
            return;
        }

        if (pinnedSha256 == null) {
            throw new UaException(
                StatusCodes.Bad_CertificateUntrusted,
                "未配置固定的服务器证书，且未启用“信任所有服务器证书”"
            );
        }

        if (!Arrays.equals(pinnedSha256, sha256(leaf))) {
            throw new UaException(
                StatusCodes.Bad_CertificateUntrusted,
                "服务器证书与设备配置中的固定证书不匹配（SHA-256 不一致）"
            );
        }
    }

    private static byte[] sha256(X509Certificate certificate) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("无法计算证书指纹", e);
        }
    }
}
