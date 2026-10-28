package com.shuati.question;

import com.shuati.common.ApiException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class QuestionTypeSupport {

  public static final String SINGLE = "SINGLE";
  public static final String MULTI = "MULTI";
  public static final String TRUE_FALSE = "TRUE_FALSE";
  public static final String SHORT = "SHORT";

  private static final Set<String> VALID_TYPES =
      Set.of(SINGLE, MULTI, TRUE_FALSE, SHORT);
  private static final Set<String> TRUE_VALUES =
      Set.of("T", "TRUE", "正确", "对", "是", "√", "✓", "✔", "☑");
  private static final Set<String> FALSE_VALUES =
      Set.of("F", "FALSE", "错误", "错", "否", "×", "✕", "✗", "☒");

  private QuestionTypeSupport() {
  }

  public static boolean isValidType(String type) {
    return VALID_TYPES.contains(type);
  }

  public static String normalizeType(Object raw) {
    String value = string(raw).trim().toUpperCase(Locale.ROOT);
    if (VALID_TYPES.contains(value)) {
      return value;
    }
    if (Set.of("TRUE/FALSE", "TRUEFALSE", "TF", "BOOLEAN", "判断", "判断题", "正误判断")
        .contains(value)) {
      return TRUE_FALSE;
    }
    return "";
  }

  public static boolean isTrueFalse(String type) {
    return TRUE_FALSE.equals(type);
  }

  public static boolean isChoiceType(String type) {
    return SINGLE.equals(type) || MULTI.equals(type) || TRUE_FALSE.equals(type);
  }

  public static boolean allowsCustomOptions(String type) {
    return SINGLE.equals(type) || MULTI.equals(type);
  }

  public static List<Map<String, String>> trueFalseOptions() {
    return List.of(
        Map.of("key", "T", "text", "正确"),
        Map.of("key", "F", "text", "错误"));
  }

  public static String normalizeTrueFalseAnswer(Object raw) {
    String value = raw == null ? "" : String.valueOf(raw)
        .replace("\uFEFF", "")
        .trim()
        .replaceAll("[。.!！\\s]+$", "")
        .toUpperCase(Locale.ROOT);
    if (TRUE_VALUES.contains(value)) {
      return "T";
    }
    if (FALSE_VALUES.contains(value)) {
      return "F";
    }
    throw new ApiException(400, "判断题答案只能是正确或错误");
  }

  public static String normalizeAnswer(String type, Object raw) {
    return isTrueFalse(type) ? normalizeTrueFalseAnswer(raw) : string(raw).trim();
  }

  public static Object normalizeOptions(String type, Object raw) {
    return isTrueFalse(type) ? trueFalseOptions() : raw;
  }

  public static String displayAnswer(String type, Object raw) {
    if (!isTrueFalse(type)) {
      return string(raw);
    }
    try {
      return "T".equals(normalizeTrueFalseAnswer(raw)) ? "正确" : "错误";
    } catch (ApiException ignored) {
      return string(raw);
    }
  }

  private static String string(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
