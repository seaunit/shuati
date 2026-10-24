package com.shuati.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuestionContentTest {

  @Test
  void normalizesWhitespaceBeforeHashing() {
    assertThat(QuestionContent.normalize("  题目 A \n\t 第二行  "))
        .isEqualTo("题目 A 第二行");
    assertThat(QuestionContent.hash(" 题目   A "))
        .isEqualTo(QuestionContent.hash("题目 A"));
  }

  @Test
  void differentContentProducesDifferentHash() {
    assertThat(QuestionContent.hash("题目 A")).isNotEqualTo(QuestionContent.hash("题目 B"));
  }
}
