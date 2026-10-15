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

  @Test
  void essayPromptAsksModelToDeriveKeyPointsWhenQuestionHasNone() {
    String withPoints = PromptTemplates.essayUser(
        "说说索引的作用", "[\"加速检索\"]", "B+ 树索引", "我的回答");
    assertThat(withPoints).contains("【评分要点】").contains("加速检索");

    // 没给要点时不能把「评分要点」区块塞进去，更不能把 JDBC 读出来的 "null" 当内容发给模型
    for (String absent : new String[]{null, "", "null", "[]"}) {
      String prompt = PromptTemplates.essayUser("说说索引的作用", absent, "B+ 树索引", "我的回答");
      assertThat(prompt).doesNotContain("【评分要点】");
      assertThat(prompt).doesNotContain("null");
      assertThat(prompt).contains("【参考答案】").contains("【考生答案】");
    }

    // 提示词里要明确交代「自己提炼要点」这条规则
    assertThat(PromptTemplates.ESSAY_SYSTEM).contains("自行提炼");
  }
}
