package com.shuati.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptTemplatesTest {

  @Test
  void explainPromptOnlyDescribesTheQuestionSoItCanBeShared() {
    String prompt = PromptTemplates.explainUser(
        "线程共享哪些资源？", "[{\"key\":\"A\",\"text\":\"栈指针\"}]", "C");

    assertThat(prompt).contains("【题目】").contains("【正确答案】C");
    // 解析会按题目缓存并共享给所有用户，绝不能带上某个考生的作答结论
    assertThat(prompt).doesNotContain("考生");
    assertThat(prompt).doesNotContain("回答正确");
    assertThat(prompt).doesNotContain("回答错误");
    assertThat(PromptTemplates.EXPLAIN_SYSTEM).doesNotContain("考生对错");
  }
}
