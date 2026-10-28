package com.shuati.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shuati.common.ApiException;
import org.junit.jupiter.api.Test;

class QuestionTypeSupportTest {

  @Test
  void normalizesCommonTrueFalseAnswers() {
    assertThat(QuestionTypeSupport.normalizeTrueFalseAnswer("T")).isEqualTo("T");
    assertThat(QuestionTypeSupport.normalizeTrueFalseAnswer("true")).isEqualTo("T");
    assertThat(QuestionTypeSupport.normalizeTrueFalseAnswer("正确")).isEqualTo("T");
    assertThat(QuestionTypeSupport.normalizeTrueFalseAnswer("对")).isEqualTo("T");
    assertThat(QuestionTypeSupport.normalizeTrueFalseAnswer("√")).isEqualTo("T");
    assertThat(QuestionTypeSupport.normalizeTrueFalseAnswer("✓")).isEqualTo("T");

    assertThat(QuestionTypeSupport.normalizeTrueFalseAnswer("F")).isEqualTo("F");
    assertThat(QuestionTypeSupport.normalizeTrueFalseAnswer("false")).isEqualTo("F");
    assertThat(QuestionTypeSupport.normalizeTrueFalseAnswer("错误")).isEqualTo("F");
    assertThat(QuestionTypeSupport.normalizeTrueFalseAnswer("错")).isEqualTo("F");
    assertThat(QuestionTypeSupport.normalizeTrueFalseAnswer("×")).isEqualTo("F");
    assertThat(QuestionTypeSupport.normalizeTrueFalseAnswer("✗")).isEqualTo("F");
  }

  @Test
  void rejectsEmptyUnknownAndDoubleAnswers() {
    assertThrows(ApiException.class, () -> QuestionTypeSupport.normalizeTrueFalseAnswer(""));
    assertThrows(ApiException.class, () -> QuestionTypeSupport.normalizeTrueFalseAnswer("也许"));
    assertThrows(ApiException.class, () -> QuestionTypeSupport.normalizeTrueFalseAnswer("TF"));
    assertThrows(ApiException.class, () -> QuestionTypeSupport.normalizeTrueFalseAnswer("正确错误"));
  }

  @Test
  void alwaysReturnsCanonicalOptions() {
    assertThat(QuestionTypeSupport.trueFalseOptions())
        .containsExactly(
            java.util.Map.of("key", "T", "text", "正确"),
            java.util.Map.of("key", "F", "text", "错误"));
  }

  @Test
  void normalizesImportedTrueFalseTypeNames() {
    assertThat(QuestionTypeSupport.normalizeType("判断")).isEqualTo("TRUE_FALSE");
    assertThat(QuestionTypeSupport.normalizeType("判断题")).isEqualTo("TRUE_FALSE");
    assertThat(QuestionTypeSupport.normalizeType("true_false")).isEqualTo("TRUE_FALSE");
    assertThat(QuestionTypeSupport.normalizeType("SINGLE")).isEqualTo("SINGLE");
  }
}
