package com.shuati.ai;

import com.shuati.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 与旧 Next.js lib/crypto.ts 完全兼容：
 * AES-256-GCM，key = Base64.decode(APP_AES_SECRET)，
 * 数据布局 = base64(iv[12] + ciphertext + tag[16])。
 */
@Component
public class AesCrypto {

  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH = 16;

  private final SecretKeySpec key;

  public AesCrypto(@Value("${APP_AES_SECRET:}") String secret) {
    if (secret == null || secret.isBlank()) {
      this.key = null;
      return;
    }
    byte[] raw = Base64.getDecoder().decode(secret.trim());
    this.key = new SecretKeySpec(raw, "AES");
  }

  public String decrypt(String encoded) {
    if (key == null) {
      throw new ApiException(500, "缺少环境变量 APP_AES_SECRET");
    }
    try {
      byte[] all = Base64.getDecoder().decode(encoded);
      byte[] iv = Arrays.copyOfRange(all, 0, IV_LENGTH);
      byte[] cipherText = Arrays.copyOfRange(all, IV_LENGTH, all.length - TAG_LENGTH);
      byte[] tag = Arrays.copyOfRange(all, all.length - TAG_LENGTH, all.length);
      byte[] payload = new byte[cipherText.length + tag.length];
      System.arraycopy(cipherText, 0, payload, 0, cipherText.length);
      System.arraycopy(tag, 0, payload, cipherText.length, tag.length);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
      return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new ApiException(500, "AI Key 解密失败：" + e.getMessage());
    }
  }

  public String encrypt(String plain) {
    if (key == null) {
      throw new ApiException(500, "缺少环境变量 APP_AES_SECRET");
    }
    try {
      byte[] iv = new byte[IV_LENGTH];
      new java.security.SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] tag = cipher.getIV() == null ? new byte[0] : new byte[0];
      byte[] payload = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, payload, 0, iv.length);
      System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
      return Base64.getEncoder().encodeToString(payload);
    } catch (Exception e) {
      throw new ApiException(500, "AI Key 加密失败：" + e.getMessage());
    }
  }

  public String mask(String plain) {
    if (plain == null || plain.length() < 8) {
      return "****";
    }
    return plain.substring(0, 4) + "****" + plain.substring(plain.length() - 4);
  }
}
