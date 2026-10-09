-- 旧版「AI 解析」把考生自己的选择一起喂给模型（例如「考生选 A，判定错误」），
-- 生成结果却按题目缓存进 question.explanation，导致其他用户看到别人的作答结论。
-- 这里清空这批失效缓存，让它们按新的「只针对题目」提示词重新生成。
UPDATE question
   SET explanation = NULL
 WHERE explanation LIKE '%考生%';
