-- 商业模式改为纯点数制：套餐不再在线售卖（表结构保留，管理员仍可手动调整套餐）
UPDATE plan SET show_on_pricing = 0;
