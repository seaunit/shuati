-- 旧库里的模型名是 deepseek-chat，DeepSeek 已改用 deepseek-flash
UPDATE ai_config
   SET model = 'deepseek-flash'
 WHERE model = 'deepseek-chat';
