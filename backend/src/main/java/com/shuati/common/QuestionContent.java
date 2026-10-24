package com.shuati.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** 题干规范化与去重哈希。空白差异不影响同一道题的判断。 */
public final class QuestionContent {

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private QuestionContent() {}

  public static String normalize(String content) {
    if (content == null) {
      return "";
    }
    return WHITESPACE.matcher(content.trim()).replaceAll(" ");
  }

  public static String hash(String content) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(normalize(content).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", e);
    }
  }
}
