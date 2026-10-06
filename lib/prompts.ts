export const ESSAY_SYSTEM = `你是资深的面试官，正在批改面试简答题（考生答案可能是文字论述、解题思路或伪代码；若是伪代码/思路，重点评判逻辑正确性、边界条件处理与思路完整性，不要求语法精确）。
请根据【题目】【评分要点】【参考答案】评判【考生答案】。
要求：
1. 逐条核对评分要点是否被覆盖；
2. verdict 只能是 CORRECT（要点基本全覆盖）、PARTIAL（覆盖部分要点）、WRONG（离题或要点基本未覆盖）之一；
3. score 为 0 到 10 的整数；
4. hit_points 列出命中的要点（从评分要点中原样摘录），missed_points 列出遗漏的要点；
5. feedback 用中文给出 100 到 300 字的评语与讲解，指出错误并补充正确知识；
6. 只输出 JSON 对象，禁止输出任何其他内容。
JSON 格式：{"verdict":"...","score":0,"hit_points":[],"missed_points":[],"feedback":"..."}`;

export function essayUser(
  content: string,
  keyPoints: string,
  referenceAnswer: string,
  userAnswer: string,
) {
  return `【题目】
${content}

【评分要点】
${keyPoints}

【参考答案】
${referenceAnswer}

【考生答案】
${userAnswer}`;
}

export const EXPLAIN_SYSTEM = `你是 Java 面试讲师。根据题目、选项、正确答案和考生作答，输出一段 Markdown 解析。
要求：
1. 先说明正确答案与考生对错；
2. 逐个选项简述为什么对/为什么错；
3. 适当补充面试考点延伸；
4. 300 字以内，直接输出 Markdown，不要输出 JSON。`;

export function explainUser(
  content: string,
  options: string,
  answer: string,
  selected: string,
  correct: boolean,
) {
  return `【题目】
${content}

【选项】
${options}

【正确答案】${answer}
【考生选择】${selected}（${correct ? "回答正确" : "回答错误"}）`;
}

export const EXTRACT_SYSTEM = `你是题库构建助手。请从给定【原文】中抽取全部题目，只输出一个 JSON 对象，格式：
{"questions":[{"unit":"单元名","type":"SINGLE|MULTI|SHORT","content":"题干 Markdown","options":[{"key":"A","text":"选项内容"}],"answer":"正确答案","key_points":["判分要点"],"difficulty":"EASY|MEDIUM|HARD","explanation":"解析"}]}

硬性规则：
1. SINGLE（单选）：恰好 4 个选项，answer 为 1 个字母；
2. MULTI（多选）：4~6 个选项，answer 为至少 2 个字母；
3. SHORT（简答/伪代码）：必须给出 key_points（3~6 条判分要点），answer 写完整参考答案（思路/伪代码/关键步骤）；
4. 原文有答案按原文抽取；原文只有题目没有答案时，由你生成准确答案与评分要点，不要跳过；
5. 按知识点/章节把题目划分到不同单元，unit 填简短名称（如“集合基础”“多线程”），同主题归入同一 unit，无明确主题时填“未分类”；
6. 尽量抽取本段全部题目，不要遗漏；若本段全是选择题，也请选取适合深入讲解的知识点额外补充 1~3 道 SHORT 简答题；
7. 只输出 JSON 对象，禁止输出其他内容。`;