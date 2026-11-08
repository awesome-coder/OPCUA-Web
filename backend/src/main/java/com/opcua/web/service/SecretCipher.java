package com.opcua.web.service;

import com.opcua.web.config.AppProperties;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AES-GCM encryption for credentials at rest. The local key is generated on first run
 * and stored next to the configuration file.
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);
    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final AppProperties properties;
    private final SecureRandom random = new SecureRandom();
    private SecretKey key;

    public SecretCipher(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        try {
            Path dataDir = Path.of(properties.getDataDir()).toAbsolutePath().normalize();
            Files.createDirectories(dataDir);
            Path keyFile = dataDir.resolve("secret.key");
            if (Files.exists(keyFile)) {
                String encoded = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
                byte[] raw = Base64.getDecoder().decode(encoded);
                if (raw.length != 32) {
                    throw new IllegalStateException("secret.key 长度无效");
                }
                key = new SecretKeySpec(raw, "AES");
            } else {
                byte[] raw = new byte[32];
                random.nextBytes(raw);
                key = new SecretKeySpec(raw, "AES");
                Files.writeString(keyFile, Base64.getEncoder().encodeToString(raw), StandardCharsets.UTF_8);
                log.info("已生成本地凭据加密密钥: {}", keyFile);
            }
        } catch (Exception e) {
            throw new IllegalStateException("初始化凭据加密失败: " + e.getMessage(), e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        if (!ciphertext.startsWith(PREFIX)) {
            return ciphertext;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (combined.length <= IV_LENGTH) {
                return null;
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("凭据解密失败，可能是 secret.key 已更换: {}", e.getMessage());
            return null;
        }
    }

    public String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest).substring(0, 12);
        } catch (Exception e) {
            return "n/a";
        }
    }
}
