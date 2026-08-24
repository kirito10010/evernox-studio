package com.evernox.util;

import com.evernox.config.CodecConfig;
import com.evernox.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 图片落盘编解码器
 *
 * 编码后的字节流结构:
 * <pre>
 *   [0..3]  魔数 'E' 'V' 'N' 'X'
 *   [4]     版本号 0x01
 *   [5..]   AES-256-GCM 密文（尾部含 16 字节 GCM Tag）
 * </pre>
 * 因此落盘文件不是任何合法图片格式，即使把后缀改成 .jpg 也无法被图片查看器打开。
 * IV 每个文件随机生成，Hex 编码后存入 image.iv 列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageCodec {

    private static final byte[] MAGIC = {'E', 'V', 'N', 'X'};
    private static final byte VERSION = 0x01;
    private static final int HEADER_LENGTH = MAGIC.length + 1;

    private static final int AES_KEY_LENGTH = 256;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final CodecConfig codecConfig;

    /** 启动时派生一次并缓存，避免每次上传都做十万次 PBKDF2 迭代 */
    private SecretKey secretKey;

    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    void initKey() {
        try {
            byte[] salt = Base64.getDecoder().decode(codecConfig.getSalt());
            PBEKeySpec spec = new PBEKeySpec(
                    codecConfig.getSecret().toCharArray(),
                    salt,
                    codecConfig.getIterations(),
                    AES_KEY_LENGTH
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("图片编解码密钥初始化完成 (iterations={})", codecConfig.getIterations());
        } catch (Exception e) {
            throw new IllegalStateException("初始化图片编解码密钥失败，请检查 evernox.codec 配置", e);
        }
    }

    /**
     * 编码结果
     *
     * @param payload 可直接落盘的字节流（含文件头）
     * @param ivHex   Hex 编码的 IV，需要与图片记录一起保存
     */
    public record Encoded(byte[] payload, String ivHex) {
    }

    /**
     * 编码原始图片字节
     */
    public Encoded encode(byte[] raw) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(raw);

            byte[] payload = new byte[HEADER_LENGTH + cipherText.length];
            System.arraycopy(MAGIC, 0, payload, 0, MAGIC.length);
            payload[MAGIC.length] = VERSION;
            System.arraycopy(cipherText, 0, payload, HEADER_LENGTH, cipherText.length);

            return new Encoded(payload, bytesToHex(iv));
        } catch (Exception e) {
            log.error("图片编码失败: {}", e.getMessage(), e);
            throw new BusinessException("图片编码失败");
        }
    }

    /**
     * 解码落盘字节，返回原始图片字节
     */
    public byte[] decode(byte[] stored, String ivHex) {
        if (stored == null || stored.length <= HEADER_LENGTH) {
            throw new BusinessException("图片文件已损坏或格式不正确");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (stored[i] != MAGIC[i]) {
                throw new BusinessException("图片文件已损坏或格式不正确");
            }
        }
        if (stored[MAGIC.length] != VERSION) {
            throw new BusinessException("不支持的图片文件版本");
        }
        if (ivHex == null || ivHex.length() != IV_LENGTH * 2) {
            throw new BusinessException("图片文件已损坏或格式不正确");
        }

        try {
            byte[] cipherText = new byte[stored.length - HEADER_LENGTH];
            System.arraycopy(stored, HEADER_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, hexToBytes(ivHex)));
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            // 不在异常信息中暴露密钥或算法细节
            log.error("图片解码失败: {}", e.getMessage(), e);
            throw new BusinessException("图片文件已损坏或格式不正确");
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
