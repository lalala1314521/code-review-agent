package io.github.lalala1314521.codereviewagent.common.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API Key 对称加密工具（AES-GCM）。
 *
 * <p>选 AES-GCM：AEAD 模式加密自带完整性校验，密文被篡改解密即抛异常，
 * 防"DB 里改一个字符导致 key 静默失效"。
 * 密钥来自环境变量 {@code LLM_CONFIG_SECRET}（生产必设；未设用内置开发值并 WARN），
 * 经 SHA-256 派生为 256bit AES 密钥，明文不入库不入日志。
 * 密文格式：Base64( IV(12B) ‖ cipher+tag )，每次随机 IV 防重放。
 */
@Component
public class CryptoUtil {

    private static final Logger log = LoggerFactory.getLogger(CryptoUtil.class);
    private static final String AES = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String DEV_SECRET = "dev-only-secret-change-me-in-prod";

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public CryptoUtil(@Value("${LLM_CONFIG_SECRET:}") String secret) {
        String effective = (secret == null || secret.isBlank()) ? DEV_SECRET : secret;
        if (DEV_SECRET.equals(effective)) {
            log.warn("LLM_CONFIG_SECRET 未设置，使用内置开发密钥——生产环境必须通过环境变量配置！");
        }
        this.keySpec = new SecretKeySpec(sha256(effective), AES);
    }

    /**
     * 加密：返回 Base64(IV ‖ 密文)。
     */
    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    /**
     * 解密：密文被篡改时抛异常（GCM 完整性校验）。
     */
    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt failed（密文损坏或密钥不匹配）", e);
        }
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
