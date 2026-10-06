package com.shuati.ai;

public final class PromptTemplates {

  private PromptTemplates() {
  }

  public static final String ESSAY_SYSTEM = """
      你是资深的面试官，正在批改面试简答题（考生答案可能是文字论述、解题思路或伪代码；若是伪代码/思路，重点评判逻辑正确性、边界条件处理与思路完整性，不要求语法精确）。
      请根据【题目】【评分要点】【参考答案】评判【考生答案】。
      要求：
      1. 逐条核对评分要点是否被覆盖；
      2. verdict 只能是 CORRECT（要点基本全覆盖）、PARTIAL（覆盖部分要点）、WRONG（离题或要点基本未覆盖）之一；
      3. score 为 0 到 10 的整数；
      4. hit_points 列出命中的要点（从评分要点中原样摘录），missed_points 列出遗漏的要点；
      5. feedback 用中文给出 100 到 300 字的评语与讲解，指出错误并补充正确知识；
      6. 只输出 JSON 对象，禁止输出任何其他内容。
      JSON 格式：{"verdict":"...","score":0,"hit_points":[],"missed_points":[],"feedback":"..."}""";

  public static String essayUser(
      String content, String keyPoints, String referenceAnswer, String userAnswer) {
    return """
        【题目】
        %s

        【评分要点】
        %s

        【参考答案】
        %s

        【考生答案】
        %s""".formatted(content, keyPoints, referenceAnswer, userAnswer);
  }

  public static final String EXPLAIN_SYSTEM = """
      你是 Java 面试讲师。根据题目、选项、正确答案和考生作答，输出一段 Markdown 解析。
      要求：
      1. 先说明正确答案与考生对错；
      2. 逐个选项简述为什么对/为什么错；
      3. 适当补充面试考点延伸；
      4. 300 字以内，直接输出 Markdown，不要输出 JSON。""";

  public static String explainUser(
      String content, String options, String answer, String selected, boolean correct) {
    return """
        【题目】
        %s

        【选项】
        %s

        【正确答案】%s
        【考生选择】%s（%s）""".formatted(content, options, answer, selected,
        correct ? "回答正确" : "回答错误");
  }

  public static final String EXTRACT_SYSTEM = """
      你是题库构建助手。请从给定【原文】中抽取全部题目，只输出一个 JSON 对象，格式：
      {"questions":[{"unit":"单元名","type":"SINGLE|MULTI|SHORT","content":"题干 Markdown","options":[{"key":"A","text":"选项内容"}],"answer":"正确答案","key_points":["判分要点"],"difficulty":"EASY|MEDIUM|HARD","explanation":"解析"}]}

      硬性规则：
      1. SINGLE（单选）：恰好 4 个选项，answer 为 1 个字母；
      2. MULTI（多选）：4~6 个选项，answer 为至少 2 个字母；
      3. SHORT（简答）：必须有 key_points；
      4. 只输出 JSON，不要解释。""";
}
