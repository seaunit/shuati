-- AI 配置改为单条维护后不再支持禁用，历史数据统一置为启用
UPDATE ai_config
   SET status = 'ENABLED'
 WHERE status <> 'ENABLED';
