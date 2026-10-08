-- 软考真题题库导入（仅题目数据，不含用户、练习记录、订单或点数数据）
-- 来源：本地 shuati 数据库 bank.id=5
-- 幂等策略：生产库已存在公共题库「软考真题」时整批跳过，避免重复导入。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
DROP TEMPORARY TABLE IF EXISTS soft_exam_import_guard;
CREATE TEMPORARY TABLE soft_exam_import_guard (id tinyint NOT NULL PRIMARY KEY);
SET @soft_exam_exists := (SELECT COUNT(*) FROM bank WHERE owner_id IS NULL AND name = '软考真题');
INSERT INTO bank (name, description, is_default, owner_id)
SELECT '软考真题', NULL, 0, NULL
WHERE @soft_exam_exists = 0;
INSERT INTO soft_exam_import_guard (id) SELECT 1 WHERE @soft_exam_exists = 0;
SET @soft_bank_id := (SELECT id FROM bank WHERE owner_id IS NULL AND name = '软考真题' ORDER BY id LIMIT 1);
START TRANSACTION;

INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '操作系统基础', 0
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据库设计', 1
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据库理论', 2
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '操作系统架构', 3
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '内存管理', 4
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件架构', 5
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '区块链', 6
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'Linux系统', 7
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '网络基础', 8
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '系统监视', 9
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '电子政务', 10
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件文档', 11
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '需求工程', 12
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件过程', 13
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件开发工具', 14
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件设计', 15
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件设计原则', 16
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件构件', 17
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '中间件', 18
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '开发模型', 19
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '敏捷开发', 20
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件测试', 21
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件架构评估', 22
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '三层C/S架构', 23
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '设计模式基础', 24
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '创建型模式', 25
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '质量属性', 26
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '网络安全', 27
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'Kerberos认证', 28
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '知识产权', 29
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '应用数学', 30
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '系统设计', 31
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '架构风格', 32
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '仓库架构风格', 33
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '需求到架构的映射', 34
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'FACE架构', 35
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '需求与架构差异', 36
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'Redis数据类型', 37
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'Redis持久化', 38
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'Redis内存淘汰', 39
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '非功能性需求', 40
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'SSM框架', 41
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据访问机制', 42
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'OPC标准', 43
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '企业集成架构', 44
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件缺陷管理', 45
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '云原生架构', 46
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据分片技术', 47
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '操作系统进程管理', 48
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '操作系统存储管理', 49
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '文件系统', 50
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '操作系统死锁', 51
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据库关系运算', 52
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '嵌入式系统', 53
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据库系统', 54
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '人工智能', 55
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '计算机网络', 56
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '网络架构', 57
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'Web服务器性能', 58
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '企业数字化转型', 59
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '信息化建设', 60
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '组织信息化需求', 61
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件产品管理', 62
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'CMMI', 63
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '配置管理', 64
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '需求管理', 65
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '需求跟踪', 66
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件生命周期', 67
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '敏捷方法', 68
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'RUP', 69
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '版本控制', 70
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '结构化设计', 71
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '耦合', 72
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '内聚', 73
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'UML', 74
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'McCabe复杂度', 75
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '构件交互', 76
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'COM重用', 77
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '网络安全威胁', 78
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'ABSD', 79
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件架构风格', 80
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '特定领域软件架构', 81
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '安全性', 82
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '架构评估', 83
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数学规划', 84
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '项目管理', 85
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '分布式计算', 86
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '面向对象用例建模', 87
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '顺序图与协作图', 88
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '对象模型、动态模型和功能模型', 89
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据架构', 90
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '反规范化设计', 91
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据一致性', 92
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'Redis应用', 93
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据库性能优化', 94
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '缓存同步', 95
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '系统架构设计', 96
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '网络协议', 97
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '面向方面编程', 98
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '系统安全架构', 99
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '企业集成平台', 100
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '微服务架构', 101
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '云计算基础', 102
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '进程管理', 103
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '磁盘调度', 104
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据库备份', 105
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '关系数据库理论', 106
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '关系代数', 107
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '操作系统', 108
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'GPU与AI芯片', 109
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '系统可靠性', 110
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据资产', 111
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据管理', 112
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '信息安全', 113
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件开发模型', 114
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件过程改进', 115
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '信息建模', 116
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '模型驱动开发', 117
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '工作流', 118
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '领域驱动设计', 119
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'UML顺序图', 120
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '构件特性', 121
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '构件定义', 122
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '服务端构件模型', 123
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '构件演化', 124
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件复杂性度量', 125
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '白盒测试', 126
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '黑盒测试', 127
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件测试类型', 128
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '遗留系统演化策略', 129
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件体系结构多视图', 130
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'ABSD方法', 131
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件体系结构风格', 132
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件架构复用', 133
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件复用过程', 134
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'DSSA', 135
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '质量属性场景', 136
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'ATAM', 137
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '可靠性分析', 138
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '安全性分析', 139
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '网络技术', 140
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数学应用', 141
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '专业英语', 142
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件架构质量属性', 143
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据流图', 144
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据字典', 145
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '嵌入式系统故障检测', 146
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '故障诊断方法', 147
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '数据库缓存', 148
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '缓存分片', 149
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '布隆过滤器', 150
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, 'MQTT协议', 151
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '边缘计算架构', 152
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '边缘计算优势', 153
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '基于构件的软件开发', 154
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '软件维护', 155
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '区块链技术', 156
FROM soft_exam_import_guard g
WHERE g.id = 1;
INSERT INTO unit (bank_id, name, sort)
SELECT @soft_bank_id, '湖仓一体架构', 157
FROM soft_exam_import_guard g
WHERE g.id = 1;

INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '前趋图是一个有向无环图，记为 DAG，用于描述进程之间执行的先后关系。假设系统中进程 P={P1，P2, P3, P4, P5, P6, P7}，且进程的前趋图如下：

那么，该前驱图可记为(1)。', CAST('[{"key":"A","text":"→={(P1,P2),(P1,P3),(P2,P4),(P3,P4),(P4,P5),(P4,P6),(P5,P7),(P6,P7)}"},{"key":"B","text":"→={(P1,P2),(P1,P3),(P2,P4),(P3,P4),(P4,P5),(P4,P6),(P5,P7),(P6,P7)}"},{"key":"C","text":"→={(P1,P2),(P1,P3),(P2,P4),(P3,P4),(P4,P5),(P4,P6),(P5,P7),(P6,P7)}"},{"key":"D","text":"→={(P1,P2),(P1,P3),(P2,P4),(P3,P4),(P4,P5),(P4,P6),(P5,P7),(P6,P7)}"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', '**答案：B；考生选 A，判定错误。**

注意：本题 A/B/C/D 四个选项文本完全相同，均显示为同一前趋关系集合，选项疑似抄录错误，无法仅凭文本区分。

- **A**：与 B 内容一致，按题面不应判错；若判错，说明原选项应有缺失边或标识错误。
- **B**：给定正确答案，表示 P1→P2/P3，P2/P3→P4，P4→P5/P6，P5/P6→P7。
- **C/D**：同样与 B 文本重复，无法解释为错，属题目选项重复问题。

**考点延伸**：前趋图是 DAG，边表示进程执行先后约束，常用于进程同步、信号量 PV 操作、死锁分析、拓扑排序。Java 面试可延伸到 JMM happens-before、线程有序性与可见性。建议核对原图，不能只背选项。',
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '操作系统基础';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在支持多线程的操作系统中，假设进程 P 创建了线程 T1、T2 和 T3，那么下列说法正确的是(2)。', CAST('[{"key":"A","text":"该进程中已打开的文件是不能被 T1、T2 和 T3 共享的"},{"key":"B","text":"该进程中 T1 的栈指针是不能被 T2 共享的，但可被 T3 共享"},{"key":"C","text":"该进程中 T1 的栈指针是不能被 T2 和 T3 共享的"},{"key":"D","text":"该进程中某线程的栈指针是可以被 T1、T2 和 T3 共享的"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', '**正确答案：C**，考生选择 **A**，回答**错误**。

- **A 错**：同一进程内线程共享进程资源，已打开文件、地址空间、全局变量等均可被 T1、T2、T3 共享。
- **B 错**：栈指针属于线程私有上下文，T1 的栈指针不能被 T2 共享，也不能被 T3 共享。
- **C 对**：每个线程有独立栈和寄存器，栈指针不与其他线程共享。
- **D 错**：线程私有寄存器/栈指针不能被同进程其他线程共享。

**考点延伸**：线程是 CPU 调度的基本单位，进程是资源分配的基本单位。线程共享：代码段、数据段、堆、文件描述符等；线程私有：程序计数器、寄存器、栈、线程 ID、状态等。面试常考“进程 vs 线程”“线程共享哪些资源”。',
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '操作系统基础';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '假设某计算机的字长为 32 位，该计算机文件管理系统磁盘空间管理采用位示图(bitmap)记录磁盘的使用情况。若磁盘的容量为 300GB，物理块的大小为 4MB，那么位示图的大小为(3)个字。', CAST('[{"key":"A","text":"2400"},{"key":"B","text":"3200"},{"key":"C","text":"6400"},{"key":"D","text":"9600"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'MEDIUM', '**答案：A（2400），考生选 B，错误。**

计算：  
300GB = 300×1024MB = 307200MB  
物理块数 = 307200 ÷ 4 = 76800 块  
位示图需 76800 位，字长 32 位：  
76800 ÷ 32 = **2400 字**

**选项分析：**
- A 2400：正确，符合上述计算。
- B 3200：错误，单位换算或除法有误，数值偏大。
- C 6400：错误，不是正确块数与字长换算结果。
- D 9600：错误，约为正确值 4 倍，明显偏大。

**考点延伸：**  
位示图用 1 位表示 1 个磁盘块，常用于空闲空间管理。注意公式：  
**位示图字数 = 磁盘容量 ÷ 块大小 ÷ 字长**  
并区分位、字节、字，且 1GB=1024MB。',
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '操作系统基础';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '实时操作系统主要用于有实时要求的过程控制等领域。因此，在实时操作系统中，对于来自外部的事件必须在(4)。', CAST('[{"key":"A","text":"一个时间片内进行处理"},{"key":"B","text":"一个周转时间内进行处理"},{"key":"C","text":"一个机器周期内进行处理"},{"key":"D","text":"被控对象允许的时间范围内进行处理"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '操作系统基础';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述前趋图（Precedence Graph）的定义及其在操作系统中的作用。', CAST('[]' AS JSON), '前趋图是一个有向无环图（DAG），用于描述进程之间执行的先后顺序。图中的每个结点表示一个进程或程序段，有向边表示两个结点之间的前趋关系，即一个结点必须在另一个结点之前完成。前趋图在操作系统中用于表示进程之间的同步和互斥关系，是分析进程并发执行的基础。',
       CAST('["前趋图是有向无环图","结点表示进程或程序段","有向边表示前趋关系","用于描述进程执行的先后顺序","在操作系统中用于进程同步和互斥分析"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '操作系统基础';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '通常在设计关系模式时，派生属性不会作为关系中的属性来存储。按照这个原则，原设计的学生关系模式为 Students（学号，姓名，性别，出生日期，年龄，家庭地址），那么该关系模式正确的设计应为(5)。', CAST('[{"key":"A","text":"Students（学号，姓名，性别，出生日期，年龄，家庭地址）"},{"key":"B","text":"Students（学号，姓名，性别，出生日期，家庭地址）"},{"key":"C","text":"Students（学号，姓名，性别，出生日期，家庭地址）"},{"key":"D","text":"Students（学号，姓名，性别，年龄，家庭地址）"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '什么是派生属性？在数据库设计中，为什么通常不将派生属性作为关系中的属性存储？请举例说明。', CAST('[]' AS JSON), '派生属性是指可以由其他属性通过计算得到的属性，例如年龄可以由出生日期和当前日期计算得出。在数据库设计中，不存储派生属性是为了避免数据冗余和不一致性，因为派生属性的值会随着基础属性的变化而变化，如果存储则需同步更新，增加维护成本。例如，学生表中的年龄就是派生属性，应通过出生日期计算得到，而不是直接存储。',
       CAST('["派生属性由其他属性计算得到","避免数据冗余","避免数据不一致","减少维护成本","举例：年龄由出生日期计算"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请说明关系型数据库开发中，逻辑数据模型设计过程包含哪些任务？根据图2-1包裹详情单，应该涉及出哪些关系模式的名称，并指出每个关系模式的主键属性。', CAST('[]' AS JSON), '逻辑数据模型设计过程包含的任务：
（1）构建系统上下文数据模型，包含实体及实体之间的联系；
（2）绘制基于主键的数据模型，为每个实体添加主键属性；
（3）构建全属性数据模型，为每个实体添加非主键属性；
（4）利用规范化技术建立系统规范化数据模型。
包裹单的逻辑数据模型中包含的实体：
（1）收件人（主键：电话）；
（2）寄件人（主键：电话）；
（3）包裹单（主键：编号）。',
       CAST('["逻辑数据模型设计任务包括构建上下文数据模型、基于主键的数据模型、全属性数据模型、规范化数据模型。","实体包括收件人、寄件人、包裹单。","收件人主键为电话，寄件人主键为电话，包裹单主键为编号。"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请说明什么是超类实体？结合图中包裹单信息，试设计一种超类实体，给出完整的属性列表。', CAST('[]' AS JSON), '超类实体是将多个实体中相同的属性组合起来构造出的新实体。
用户（姓名、电话、单位名称、详细地址）',
       CAST('["超类实体是多个实体相同属性的抽象。","超类实体为“用户”。","属性包括姓名、电话、单位名称、详细地址。"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请说明什么是派生属性？结合图中包裹单信息说明哪个属性是派生属性。', CAST('[]' AS JSON), '派生属性是指某个实体的非主键属性由该实体其他非主键属性决定。
包裹单中的总计是由资费、挂号费、保价费、回执费计算得出，所以是派生属性。',
       CAST('["派生属性定义：非主键属性由其他非主键属性决定。","包裹单中的“总计”是派生属性。","总计由资费、挂号费、保价费、回执费计算得出。"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '某企业开发信息管理系统平台进行 E-R 图设计，人力部门定义的是员工实体具有属性：员工号、姓名、性别、出生日期、联系方式和部门，培训部门定义的培训师实体具有属性：培训师号，姓名和职称，其中职称={初级培训师，中级培训师，高级培训师}，这种情况属于（ ）。', CAST('[{"key":"A","text":"属性冲突"},{"key":"B","text":"结构冲突"},{"key":"C","text":"命名冲突"},{"key":"D","text":"实体冲突"}]' AS JSON), 'B',
       CAST('["同一实体在不同局部 E-R 图中属性不同","员工实体和培训师实体本质是同一实体，但属性不同","属于结构冲突"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在合并 E-R 图时，解决上述冲突的方法是（ ）。', CAST('[{"key":"A","text":"员工实体和培训师实体均保持不变"},{"key":"B","text":"保留员工实体、删除培训师实体"},{"key":"C","text":"员工实体中加入职称属性，删除培训师实体"},{"key":"D","text":"将培训师实体所有属性并入员工实体，删除培训师实体"}]' AS JSON), 'C',
       CAST('["合并时应统一实体，保留员工实体","将培训师特有的职称属性并入员工实体","删除培训师实体"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在分布式数据库中有分片透明、复制透明、位置透明和逻辑透明等基本概念。其中，(8)是指用户无需知道数据存放的物理位置。', CAST('[{"key":"A","text":"分片透明"},{"key":"B","text":"逻辑透明"},{"key":"C","text":"位置透明"},{"key":"D","text":"复制透明"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库理论';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '以下关于操作系统微内核架构特征的说法，不正确的是(9)。', CAST('[{"key":"A","text":"微内核的系统结构清晰，利于协作开发"},{"key":"B","text":"微内核代码量少，系统具有良好的可移植性"},{"key":"C","text":"微内核有良好的伸缩性、扩展性"},{"key":"D","text":"微内核的功能代码可以互相调用，性能很高"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '操作系统架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '分页内存管理的核心是将虚拟内存空间和物理内存空间皆划分成大小相同的页面，并以页面作为内存空间的最小分配单位。下图给出了内存管理单元的虚拟地址到物理地址的翻译过程，假设页面大小为 4KB，那么 CPU 发出虚拟地址 0010000000000100 后，其访问的物理地址是(10)。', CAST('[{"key":"A","text":"1100000000000100"},{"key":"B","text":"0100000000000100"},{"key":"C","text":"1100000000000000"},{"key":"D","text":"1100000000000010"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '内存管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '以下关于计算机内存管理的描述中，(11)属于段页式内存管理的描述。', CAST('[{"key":"A","text":"一个程序就是一段，使用基址极限对来进行管理"},{"key":"B","text":"一个程序分为许多固定大小的页面，使用页表进行管理"},{"key":"C","text":"程序按逻辑分为多段，每一段内又进行分页，使用段页表来进行管理"},{"key":"D","text":"程序按逻辑分成多段，用一组基址极限对来进行管理。基址极限对存放在段表里"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '内存管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请解释分页内存管理中虚拟地址到物理地址的转换过程，并说明页表的作用。', CAST('[]' AS JSON), '在分页内存管理中，虚拟地址由页号和页内偏移组成。CPU发出虚拟地址后，内存管理单元（MMU）根据页号查找页表，得到对应的物理页框号，然后将物理页框号与页内偏移拼接，形成物理地址。页表是操作系统为每个进程维护的数据结构，记录了虚拟页号到物理页框号的映射关系，是实现地址转换的关键。',
       CAST('["虚拟地址由页号和页内偏移组成","MMU根据页号查找页表","页表记录虚拟页号到物理页框号的映射","物理地址由物理页框号和页内偏移拼接而成","页表是地址转换的关键"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '内存管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件脆弱性是软件中存在的弱点(或缺陷)，利用它可以危害系统安全策略，导致信息丢失、系统价值和可用性降低。嵌入式系统软件架构通常采用分层架构，它可以将问题分解为一系列相对独立的子问题，局部化在每一层中，从而有效地降低单个问题的规模和复杂性，实现复杂系统的分解。但是，分层架构仍然存在脆弱性。常见的分层架构的脆弱性包括(12)等两个方面。', CAST('[{"key":"A","text":"底层发生错误会导致整个系统无法正常运行、层与层之间功能引用可能导致功能失效"},{"key":"B","text":"底层发生错误会导致整个系统无法正常运行、层与层之间引入通信机制势必造成性能下降"},{"key":"C","text":"上层发生错误会导致整个系统无法正常运行、层与层之间引入通信机制势必造成性能下降"},{"key":"D","text":"上层发生错误会导致整个系统无法正常运行、层与层之间功能引用可能导致功能失效"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件脆弱性是指软件中存在的弱点（或缺陷），利用它可以危害系统安全策略，导致信息丢失、系统价值和可用性降低等。分层架构存在众多脆弱性问题，以下不属于分层架构脆弱性表现的是（ ）。', CAST('[{"key":"A","text":"一旦某个底层发生错误，整个程序将无法正常运行"},{"key":"B","text":"层与层之间引入通信机制，导致性能下降"},{"key":"C","text":"分层架构具有良好的可扩展性和可维护性"},{"key":"D","text":"层间传递可能产生数据溢出、空指针等安全问题"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述分层架构的脆弱性表现，并说明其产生的原因。', CAST('[]' AS JSON), '分层架构的脆弱性主要表现在两个方面：一是底层错误会导致整个程序无法正常运行，可能产生数据溢出、空指针、空对象等安全问题，或得出错误结果；二是层与层之间引入通信机制，原本直接的操作需要层层传递，导致性能下降。产生原因：底层错误影响上层，层间通信增加开销。',
       CAST('["底层错误导致整个程序无法正常运行","可能产生数据溢出、空指针、空对象等安全问题","层间通信机制导致性能下降","原本直接的操作需要层层传递"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '考虑软件架构时，重要的是从不同的视角(perspective)来检查，这促使软件设计师考虑架构的不同属性。例如，展示功能组织的(44)能判断质量特性,展示并发行为的(45)能判断系统行为特性。选择的特定视角或视图也就是逻辑视图、进程视图、实现视图和(46)。使用(47)来记录设计元素的功能和概念接口, 它本身在系统中的角色，这些角色包括功能、性能等。', CAST('[{"key":"A","text":"静态视角"},{"key":"B","text":"动态视角"},{"key":"C","text":"多维视角"},{"key":"D","text":"部署视角"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '考虑软件架构时，重要的是从不同的视角(perspective)来检查，这促使软件设计师考虑架构的不同属性。例如，展示功能组织的(44)能判断质量特性,展示并发行为的(45)能判断系统行为特性。选择的特定视角或视图也就是逻辑视图、进程视图、实现视图和(46)。使用(47)来记录设计元素的功能和概念接口, 它本身在系统中的角色，这些角色包括功能、性能等。', CAST('[{"key":"A","text":"开发视角"},{"key":"B","text":"动态视角"},{"key":"C","text":"部署视角"},{"key":"D","text":"静态视角"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '考虑软件架构时，重要的是从不同的视角(perspective)来检查，这促使软件设计师考虑架构的不同属性。例如，展示功能组织的(44)能判断质量特性,展示并发行为的(45)能判断系统行为特性。选择的特定视角或视图也就是逻辑视图、进程视图、实现视图和(46)。使用(47)来记录设计元素的功能和概念接口, 它本身在系统中的角色，这些角色包括功能、性能等。', CAST('[{"key":"A","text":"开发视图"},{"key":"B","text":"配置视图"},{"key":"C","text":"部署视图"},{"key":"D","text":"物理视图"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '考虑软件架构时，重要的是从不同的视角(perspective)来检查，这促使软件设计师考虑架构的不同属性。例如，展示功能组织的(44)能判断质量特性,展示并发行为的(45)能判断系统行为特性。选择的特定视角或视图也就是逻辑视图、进程视图、实现视图和(46)。使用(47)来记录设计元素的功能和概念接口, 它本身在系统中的角色，这些角色包括功能、性能等。', CAST('[{"key":"A","text":"逻辑视图"},{"key":"B","text":"物理视图"},{"key":"C","text":"部署视图"},{"key":"D","text":"用例视图"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '以下关于微服务架构与面向服务架构的描述中，正确的是（   ）。', CAST('[{"key":"A","text":"两者均采用去中心化管理"},{"key":"B","text":"两者均采用集中式管理"},{"key":"C","text":"微服务架构采用去中心化管理，面向服务架构采用集中式管理"},{"key":"D","text":"微服务架构采用集中式管理，面向服务架构采用去中心化管理"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '以下关于区块链应用系统中“挖矿”行为的描述中，错误的是（ ）。', CAST('[{"key":"A","text":"矿工“挖矿”取得区块链的记账权，同时获得代币奖励"},{"key":"B","text":"“挖矿”本质上是在尝试计算一个Hash碰撞"},{"key":"C","text":"“挖矿”是一种工作量证明机制"},{"key":"D","text":"可以防止比特币的双花攻击"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '区块链';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述区块链中“挖矿”的作用及其技术本质，并说明它是否能防止双花攻击。', CAST('[]' AS JSON), '“挖矿”的作用是获得记账权和代币奖励，技术本质是尝试计算一个Hash碰撞，完成工作量证明。挖矿行为本身不能防止双花攻击，双花攻击的防止依赖于区块链的共识机制和交易确认规则。',
       CAST('["挖矿获得记账权和代币奖励","技术本质是计算Hash碰撞","完成工作量证明","挖矿本身不能防止双花攻击"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '区块链';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在Linux系统中，DNS的配置文件是（ ）。', CAST('[{"key":"A","text":"/etc/hostname"},{"key":"B","text":"/dev/host.conf"},{"key":"C","text":"/etc/resolv.conf"},{"key":"D","text":"/dev/name.conf"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'Linux系统';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '下面关于网络延迟的说法中，正确的是（ ）。', CAST('[{"key":"A","text":"在对等网络中，网络的延迟大小与网络中的终端数量无关"},{"key":"B","text":"使用路由器进行数据转发所带来的延迟小于交换机"},{"key":"C","text":"使用Internet服务能够最大限度地减小网络延迟"},{"key":"D","text":"服务器延迟的主要影响因素是队列延迟和磁盘IO延迟"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络基础';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述网络延迟的主要来源，并说明为什么路由器转发延迟大于交换机。', CAST('[]' AS JSON), '网络延迟主要来源于运算、读取和写入、数据传输以及拥塞。路由器采用存储转发方式，需要分析数据包的三层地址并进行路由决策，而交换机采用直接转发方式，不分析三层地址，因此路由器转发延迟大于交换机。',
       CAST('["延迟来源包括运算、读写、传输和拥塞","路由器存储转发，分析三层地址","交换机直接转发，不分析三层地址","路由器转发延迟大于交换机"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络基础';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '进行系统监视通常有三种方式：一是通过（ ），如UNIX/Linux系统中的ps、last等；二是通过系统记录文件查阅系统在特定时间内的运行状态；三是集成命令、文件记录和可视化技术的监控工具。', CAST('[{"key":"A","text":"系统命令"},{"key":"B","text":"系统调用"},{"key":"C","text":"系统接口"},{"key":"D","text":"系统功能"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '系统监视';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '集成命令、文件记录和可视化技术的监控工具是（ ）。', CAST('[{"key":"A","text":"Windows的netstat"},{"key":"B","text":"Linux的iptables"},{"key":"C","text":"Windows的Perfmon"},{"key":"D","text":"Linux的top"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '系统监视';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '人口信息采集、处理和利用业务属于（ ）领域。', CAST('[{"key":"A","text":"政府对企（事）业单位（G2B）"},{"key":"B","text":"政府与政府（G2G）"},{"key":"C","text":"企业对政府（B2G）"},{"key":"D","text":"政府对居民（G2C）"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '电子政务';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '营业执照的颁发业务属于（ ）领域。', CAST('[{"key":"A","text":"政府对企（事）业单位（G2B）"},{"key":"B","text":"政府与政府（G2G）"},{"key":"C","text":"企业对政府（B2G）"},{"key":"D","text":"政府对居民（G2C）"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '电子政务';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '户籍管理业务属于（ ）领域。', CAST('[{"key":"A","text":"政府对企（事）业单位（G2B）"},{"key":"B","text":"政府与政府（G2G）"},{"key":"C","text":"企业对政府（B2G）"},{"key":"D","text":"政府对居民（G2C）"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '电子政务';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '参加政府工程投标活动属于（ ）领域。', CAST('[{"key":"A","text":"政府对企（事）业单位（G2B）"},{"key":"B","text":"政府与政府（G2G）"},{"key":"C","text":"企业对政府（B2G）"},{"key":"D","text":"政府对居民（G2C）"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '电子政务';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述电子政务中G2G、G2B、G2C、B2G、C2G五种互动领域的主要业务内容。', CAST('[]' AS JSON), 'G2G：政府内部政务活动，如基础信息采集、计划管理、通信系统、管理信息系统等；G2B：政府向企业发布政策法规、颁发执照许可证等；G2C：政府向居民提供服务，如信息服务、户口证件管理、公共部门服务等；B2G：企业向政府缴税、填报统计信息、参加投标、供应商品服务等；C2G：居民向政府缴税、填报信息、参政议政、报警服务等。',
       CAST('["G2G涉及政府内部政务活动","G2B是政府对企业的管理服务","G2C是政府对居民的服务","B2G是企业对政府的活动","C2G是居民对政府的活动"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '电子政务';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件文档是影响软件可维护性的决定因素。软件的文档可以分为用户文档和（ ）两类。', CAST('[{"key":"A","text":"系统文档"},{"key":"B","text":"需求文档"},{"key":"C","text":"标准文档"},{"key":"D","text":"实现文档"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件文档';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '用户文档主要描述（ ）和使用方法，并不关心这些功能是怎样实现的。', CAST('[{"key":"A","text":"系统实现"},{"key":"B","text":"系统设计"},{"key":"C","text":"系统功能"},{"key":"D","text":"系统测试"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件文档';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件需求开发的最终文档经过评审批准后，就定义了开发工作的（ ）。', CAST('[{"key":"A","text":"需求基线"},{"key":"B","text":"需求标准"},{"key":"C","text":"需求用例"},{"key":"D","text":"需求分析"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '需求工程';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '需求基线在客户和开发者之间构筑了产品功能需求和非功能需求的一个（ ），是需求开发和需求管理之间的桥梁。', CAST('[{"key":"A","text":"需求用例"},{"key":"B","text":"需求管理标准"},{"key":"C","text":"需求约定"},{"key":"D","text":"需求变更"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '需求工程';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '（   ）通常为一个迭代过程，其中的活动包括需求发现、需求分类和组织、需求协商、需求文档化。', CAST('[{"key":"A","text":"需求确认"},{"key":"B","text":"需求管理"},{"key":"C","text":"需求抽取"},{"key":"D","text":"需求规格说明"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '需求工程';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件过程是制作软件产品的一组活动及其结果。这些活动主要由软件人员来完成，软件活动主要包括软件描述、 (26)、软件有效性验证和(27)。其中， (28)定义了软件功能以及使用的限制。', CAST('[{"key":"A","text":"软件模型"},{"key":"B","text":"软件需求"},{"key":"C","text":"软件分析"},{"key":"D","text":"软件开发"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件过程';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件过程是制作软件产品的一组活动及其结果。这些活动主要由软件人员来完成，软件活动主要包括软件描述、 (26)、软件有效性验证和(27)。其中， (28)定义了软件功能以及使用的限制。', CAST('[{"key":"A","text":"软件分析"},{"key":"B","text":"软件测试"},{"key":"C","text":"软件演化"},{"key":"D","text":"软件开发"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件过程';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件过程是制作软件产品的一组活动及其结果。这些活动主要由软件人员来完成，软件活动主要包括软件描述、 (26)、软件有效性验证和(27)。其中， (28)定义了软件功能以及使用的限制。', CAST('[{"key":"A","text":"软件分析"},{"key":"B","text":"软件测试"},{"key":"C","text":"软件描述"},{"key":"D","text":"软件开发"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件过程';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述软件过程的定义及其包含的主要活动。', CAST('[]' AS JSON), '软件过程是指软件生存周期所涉及的一系列相关过程，是制作软件产品的一组活动及其结果。主要活动包括软件描述、软件开发、软件有效性验证和软件演化。',
       CAST('["软件过程定义：软件生存周期所涉及的一系列相关过程，是制作软件产品的一组活动及其结果。","主要活动包括软件描述、软件开发、软件有效性验证和软件演化。","软件描述定义了软件功能以及使用的限制。"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件过程';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '对应软件开发过程的各种活动，软件开发工具有需求分析工具、 (29)、编码与排错工具、测试工具等。按描述需求定义的方法可将需求分析工具分为基于自然语言或图形描述的工具和基于(30)的工具。', CAST('[{"key":"A","text":"设计工具"},{"key":"B","text":"分析工具"},{"key":"C","text":"耦合工具"},{"key":"D","text":"监控工具"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件开发工具';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '对应软件开发过程的各种活动，软件开发工具有需求分析工具、 (29)、编码与排错工具、测试工具等。按描述需求定义的方法可将需求分析工具分为基于自然语言或图形描述的工具和基于(30)的工具。', CAST('[{"key":"A","text":"用例"},{"key":"B","text":"形式化需求定义语言"},{"key":"C","text":"UML"},{"key":"D","text":"需求描述"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件开发工具';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述软件开发工具的分类，并说明需求分析工具的两大类。', CAST('[]' AS JSON), '软件开发工具按软件过程活动可分为软件开发工具、软件维护工具、软件管理和软件支持工具。其中，对应软件开发过程的各种活动，软件开发工具有需求分析工具、设计工具、编码与排错工具、测试工具等。需求分析工具按描述需求定义的方法可分为基于自然语言或图形描述的工具和基于形式化需求定义语言的工具。',
       CAST('["软件开发工具按软件过程活动分类：软件开发工具、软件维护工具、软件管理和软件支持工具。","软件开发工具包括需求分析工具、设计工具、编码与排错工具、测试工具等。","需求分析工具分类：基于自然语言或图形描述的工具和基于形式化需求定义语言的工具。"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件开发工具';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件设计包括四个既独立又相互联系的活动：（31）、软件结构设计、人机界面设计和(32)。', CAST('[{"key":"A","text":"用例设计"},{"key":"B","text":"数据设计"},{"key":"C","text":"程序设计"},{"key":"D","text":"模块设计"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件设计包括四个既独立又相互联系的活动：（31）、软件结构设计、人机界面设计和(32)。', CAST('[{"key":"A","text":"接口设计"},{"key":"B","text":"操作设计"},{"key":"C","text":"输入输出设计"},{"key":"D","text":"过程设计"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述软件设计包括的四个活动，并说明信息隐蔽原则的作用。', CAST('[]' AS JSON), '软件设计包括数据设计、软件结构设计、人机界面设计和过程设计四个活动。信息隐蔽原则通过将每个程序的成分隐蔽或封装在一个单一的设计模块中，并尽可能少地暴露其内部处理过程，可以提高软件的可修改性、可测试性和可移植性。',
       CAST('["软件设计四个活动：数据设计、软件结构设计、人机界面设计和过程设计。","信息隐蔽原则：将每个程序的成分隐蔽或封装在一个单一的设计模块中，尽可能少地暴露内部处理过程。","信息隐蔽的作用：提高软件的可修改性、可测试性和可移植性。"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '信息隐蔽是开发整体程序结构时使用的法则，通过信息隐蔽可以提高软件的(33)、可测试性和(34)。', CAST('[{"key":"A","text":"可修改性"},{"key":"B","text":"可扩充性"},{"key":"C","text":"可靠性"},{"key":"D","text":"耦合性"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件设计原则';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '信息隐蔽是开发整体程序结构时使用的法则，通过信息隐蔽可以提高软件的(33)、可测试性和(34)。', CAST('[{"key":"A","text":"封装性"},{"key":"B","text":"安全性"},{"key":"C","text":"可移植性"},{"key":"D","text":"可交互性"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件设计原则';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '按照外部形态，构成一个软件系统的构件可以分为五类，其中， (35)是指可以进行版本替换并增加构件新功能。', CAST('[{"key":"A","text":"装配的构件"},{"key":"B","text":"可修改的构件"},{"key":"C","text":"有限制的构件"},{"key":"D","text":"适应性构件"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件构件';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '以下关于软件构件的叙述中，错误的是(35)', CAST('[{"key":"A","text":"构件的部署必须能跟它所在的环境及其他构件完全分离"},{"key":"B","text":"构件作为一个部署单元是不可拆分的"},{"key":"C","text":"在一个特定进程中可能会存在多个特定构件的拷贝"},{"key":"D","text":"对于不影响构件功能的某些属性可以对外部可见"}]' AS JSON), 'D',
       CAST('["构件没有外部可见状态","构件是独立部署单元，不可拆分"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件构件';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述软件构件的特点。', CAST('[]' AS JSON), '软件构件具有以下特点：独立部署单元、可作为第三方组装单元、没有外部可见状态。构件可以独立开发、发布，并与其他构件组装成系统。',
       CAST('["独立部署单元","第三方组装单元","无外部可见状态"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件构件';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '中间件是提供平台和应用之间的通用服务，这些服务具有标准的程序接口和协议。中间件的基本功能包括：为客户端和服务器之间提供(36)；提供(37)保证交易的一致性；提供应用的(38)。', CAST('[{"key":"A","text":"连接和通信"},{"key":"B","text":"应用程序接口"},{"key":"C","text":"通信协议支持"},{"key":"D","text":"数据交换标准"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '中间件';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '中间件是提供平台和应用之间的通用服务，这些服务具有标准的程序接口和协议。中间件的基本功能包括：为客户端和服务器之间提供(36)；提供(37)保证交易的一致性；提供应用的(38)。', CAST('[{"key":"A","text":"安全控制机制"},{"key":"B","text":"交易管理机制"},{"key":"C","text":"标准消息格式"},{"key":"D","text":"数据映射机制"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '中间件';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '中间件是提供平台和应用之间的通用服务，这些服务具有标准的程序接口和协议。中间件的基本功能包括：为客户端和服务器之间提供(36)；提供(37)保证交易的一致性；提供应用的(38)。', CAST('[{"key":"A","text":"基础硬件平台"},{"key":"B","text":"操作系统服务"},{"key":"C","text":"网络和数据库"},{"key":"D","text":"负载均衡和高可用性"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '中间件';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '通常，嵌入式中间件没有统一的架构风格，根据应用对象的不同可存在多种类型，比较常见的是消息中间件和分布式对象中间件，以下有关消息中间件的描述中，不正确的是（   ）。', CAST('[{"key":"A","text":"消息中间件是消息传输过程中保存消息的一种容器"},{"key":"B","text":"消息中间件具有两个基本特点：采用异步处理模式、应用程序和应用程序调用关系为松耦合关系"},{"key":"C","text":"消息中间件主要由一组对象来提供系统服务，对象间能够跨平台通信"},{"key":"D","text":"消息中间件的消息传递服务模型有点对点模型和发布-订阅模型之分"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '中间件';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在分布式系统中，中间件通常提供两种不同类型的支持，即（   ）。', CAST('[{"key":"A","text":"数据支持和交互支持"},{"key":"B","text":"交互支持和提供公共服务"},{"key":"C","text":"安全支持和提供公共服务"},{"key":"D","text":"数据支持和提供公共服务"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '中间件';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '应用系统开发中可以采用不同的开发模型，其中， (39)将整个开发流程分为目标设定、风险分析、开发和有效性验证、评审四个部分； (40)则通过重用来提高软件的可靠性和易维护性，程序在进行修改时产生较少的副作用。', CAST('[{"key":"A","text":"瀑布模型"},{"key":"B","text":"螺旋模型"},{"key":"C","text":"构件模型"},{"key":"D","text":"对象模型"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '开发模型';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '应用系统开发中可以采用不同的开发模型，其中， (39)将整个开发流程分为目标设定、风险分析、开发和有效性验证、评审四个部分； (40)则通过重用来提高软件的可靠性和易维护性，程序在进行修改时产生较少的副作用。', CAST('[{"key":"A","text":"瀑布模型"},{"key":"B","text":"螺旋模型"},{"key":"C","text":"构件模型"},{"key":"D","text":"对象模型"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '开发模型';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '关于敏捷开发方法的特点，不正确的是(41)。', CAST('[{"key":"A","text":"敏捷开发方法是适应性而非预设性"},{"key":"B","text":"敏捷开发方法是面向过程的而非面向人的"},{"key":"C","text":"采用迭代增量式的开发过程，发行版本小型化"},{"key":"D","text":"敏捷开发中强调开发过程中相关人员之间的信息交流"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '敏捷开发';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '自动化测试工具主要使用脚本技术来生成测试用例，其中， (42)是录制手工测试的测试用例时得到的脚本； (43)是将测试输入存储在独立的数据文件中,而不是在脚本中。', CAST('[{"key":"A","text":"线性脚本"},{"key":"B","text":"结构化脚本"},{"key":"C","text":"数据驱动脚本"},{"key":"D","text":"共享脚本"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件测试';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '自动化测试工具主要使用脚本技术来生成测试用例，其中， (42)是录制手工测试的测试用例时得到的脚本； (43)是将测试输入存储在独立的数据文件中,而不是在脚本中。', CAST('[{"key":"A","text":"线性脚本"},{"key":"B","text":"结构化脚本"},{"key":"C","text":"数据驱动脚本"},{"key":"D","text":"共享脚本"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件测试';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件测试是保障软件质量的重要手段。 (42) 是指被测试程序不在机器上运行，而采用人工监测和计算机辅助分析的手段对程序进行监测。', CAST('[{"key":"A","text":"静态测试"},{"key":"B","text":"动态测试"},{"key":"C","text":"黑盒测试"},{"key":"D","text":"白盒测试"}]' AS JSON), 'A',
       CAST('["静态测试不运行程序，通过人工和工具分析"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件测试';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '(43) 也称为功能测试，不考虑程序的内部结构和处理算法，只检查软件功能是否能按照要求正常使用。', CAST('[{"key":"A","text":"系统测试"},{"key":"B","text":"集成测试"},{"key":"C","text":"黑盒测试"},{"key":"D","text":"白盒测试"}]' AS JSON), 'C',
       CAST('["黑盒测试关注功能，不考虑内部结构"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件测试';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述黑盒测试和白盒测试的区别。', CAST('[]' AS JSON), '黑盒测试又称功能测试，不考虑程序内部结构和实现细节，只依据需求规格说明检查功能是否符合要求；白盒测试又称结构测试，需要了解程序内部逻辑结构，覆盖代码路径和分支。',
       CAST('["黑盒测试关注功能，白盒测试关注结构","黑盒测试不关心内部实现，白盒测试需要内部逻辑","黑盒测试基于需求，白盒测试基于代码"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件测试';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在软件架构评估中，影响多个质量属性的特性是多个质量属性的（ ）。', CAST('[{"key":"A","text":"敏感点"},{"key":"B","text":"权衡点"},{"key":"C","text":"风险决策"},{"key":"D","text":"无风险决策"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构评估';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在软件架构评估中，敏感点是（ ）。', CAST('[{"key":"A","text":"影响多个质量属性的特性"},{"key":"B","text":"一个或多个构件（或构件之间关系）的特性"},{"key":"C","text":"风险决策"},{"key":"D","text":"无风险决策"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构评估';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '提高加密级别可以提高安全性，但可能耗费更多处理时间，影响系统性能。如果某个机密消息的处理有严格的时间延迟要求，则加密级别可能成为一个（ ）。', CAST('[{"key":"A","text":"敏感点"},{"key":"B","text":"权衡点"},{"key":"C","text":"风险决策"},{"key":"D","text":"无风险决策"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构评估';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述软件架构评估中敏感点与权衡点的区别，并举例说明。', CAST('[]' AS JSON), '敏感点是一个或多个构件（或构件之间关系）的特性，研究敏感点可使设计人员或分析员明确在搞清楚如何实现质量目标时应注意什么。权衡点是影响多个质量属性的特性，是多个质量属性的敏感点。例如，提高加密级别可以提高安全性，但可能耗费更多处理时间，影响性能，若系统对性能有严格需求，则加密级别成为权衡点。',
       CAST('["敏感点定义：一个或多个构件（或关系）的特性","权衡点定义：影响多个质量属性的特性，是多个质量属性的敏感点","举例说明：加密级别影响安全性和性能，成为权衡点"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构评估';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在三层C/S架构中，增加了一个（ ）。', CAST('[{"key":"A","text":"应用服务器"},{"key":"B","text":"分布式数据库"},{"key":"C","text":"内容分发"},{"key":"D","text":"镜像"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '三层C/S架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '三层C/S架构是将应用功能分成表示层、功能层和（ ）三部分。', CAST('[{"key":"A","text":"硬件层"},{"key":"B","text":"数据层"},{"key":"C","text":"设备层"},{"key":"D","text":"通信层"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '三层C/S架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在三层C/S架构中，应用的用户接口部分是（ ）。', CAST('[{"key":"A","text":"表示层"},{"key":"B","text":"数据层"},{"key":"C","text":"应用层"},{"key":"D","text":"功能层"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '三层C/S架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述三层C/S架构与二层C/S架构相比的主要优点。', CAST('[]' AS JSON), '三层C/S架构将应用功能分成表示层、功能层和数据层，各层逻辑独立，具有以下优点：1）可扩展性好，易于支持大型企业广域网或Internet；2）灵活性高，可独立修改各层；3）可重用性好，功能层可复用；4）安全性好，可集中管理数据访问；5）易于维护，客户端较薄。',
       CAST('["可扩展性好","灵活性高","可重用性好","安全性好","易于维护"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '三层C/S架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '经典的设计模式共有23个，按目的划分可分为（ ）型、结构型和行为型三种模式。', CAST('[{"key":"A","text":"创建"},{"key":"B","text":"实例"},{"key":"C","text":"代理"},{"key":"D","text":"协同"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '设计模式基础';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '按设计模式的范围划分，可以把设计模式分为类设计模式和（ ）设计模式。', CAST('[{"key":"A","text":"包"},{"key":"B","text":"模板"},{"key":"C","text":"对象"},{"key":"D","text":"架构"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '设计模式基础';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请说明设计模式按目的分类的三种类型，并各举一个例子。', CAST('[]' AS JSON), '设计模式按目的分为创建型、结构型和行为型。创建型模式关注对象的创建，如Abstract Factory；结构型模式关注类和对象的组合，如Adapter；行为型模式关注对象之间的职责分配和通信，如Observer。',
       CAST('["创建型：关注对象创建，如Abstract Factory","结构型：关注类和对象组合，如Adapter","行为型：关注职责分配和通信，如Observer"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '设计模式基础';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在不指定具体类的情况下，为创建一系列相关或相互依赖的对象提供一个接口的模式是（ ）。', CAST('[{"key":"A","text":"Prototype"},{"key":"B","text":"Abstract Factory"},{"key":"C","text":"Builder"},{"key":"D","text":"Singleton"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '创建型模式';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '将复杂对象的构建与其表示相分离，使得相同的构造过程可以创建不同的对象的模式是（ ）。', CAST('[{"key":"A","text":"Prototype"},{"key":"B","text":"Abstract Factory"},{"key":"C","text":"Builder"},{"key":"D","text":"Singleton"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '创建型模式';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '允许对象在不了解要创建对象的确切类以及如何创建等细节的情况下创建自定义对象的模式是（ ）。', CAST('[{"key":"A","text":"Prototype"},{"key":"B","text":"Abstract Factory"},{"key":"C","text":"Builder"},{"key":"D","text":"Singleton"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '创建型模式';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '“网站在并发用户数量10万的负载情况下，用户请求的平均响应时间应小于3秒”这一场景主要与（ ）质量属性相关。', CAST('[{"key":"A","text":"性能"},{"key":"B","text":"可用性"},{"key":"C","text":"易用性"},{"key":"D","text":"可修改性"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '质量属性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '通常可采用（ ）架构策略实现性能属性。', CAST('[{"key":"A","text":"抽象接口"},{"key":"B","text":"信息隐藏"},{"key":"C","text":"主动冗余"},{"key":"D","text":"资源调度"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '质量属性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '“主站宕机后，系统能够在10秒内自动切换至备用站点并恢复正常运行”主要与（ ）质量属性相关。', CAST('[{"key":"A","text":"性能"},{"key":"B","text":"可用性"},{"key":"C","text":"易用性"},{"key":"D","text":"可修改性"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '质量属性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '通常可采用（ ）架构策略实现可用性属性。', CAST('[{"key":"A","text":"记录/回放"},{"key":"B","text":"操作串行化"},{"key":"C","text":"心跳"},{"key":"D","text":"增加计算资源"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '质量属性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '“系统完成上线后，少量的外围业务功能和界面的调整与修改不超过10人·月”主要与（ ）质量属性相关。', CAST('[{"key":"A","text":"性能"},{"key":"B","text":"可用性"},{"key":"C","text":"易用性"},{"key":"D","text":"可修改性"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '质量属性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请解释质量属性场景的六个组成部分，并针对“网站在并发用户数量10万的负载情况下，用户请求的平均响应时间应小于3秒”这一场景，写出其质量属性场景描述。', CAST('[]' AS JSON), '质量属性场景由刺激源、刺激、环境、制品、响应、响应度量六部分组成。针对该性能场景：刺激源为大量并发用户，刺激为发起用户请求，环境为系统正常运行状态，制品为在线教育平台，响应为系统处理请求并返回结果，响应度量为平均响应时间小于3秒。',
       CAST('["刺激源：大量并发用户","刺激：发起用户请求","环境：系统正常运行","制品：在线教育平台","响应：处理请求并返回结果","响应度量：平均响应时间小于3秒"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '质量属性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '在架构评估中，质量属性效用树主要关注哪些质量属性？请将题干中的需求描述（a）~（k）归类到相应的质量属性。', CAST('[]' AS JSON), '质量属性效用树主要关注性能、可用性、安全性和可修改性。性能：b、g；可用性：d、f；安全性：c、i；可修改性：h、j。功能需求：a、e；可测试性：k。',
       CAST('["列出四个质量属性：性能、可用性、安全性、可修改性","正确归类b、g为性能","正确归类d、f为可用性","正确归类c、i为安全性","正确归类h、j为可修改性"]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '质量属性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在架构评估过程中，评估人员所关注的是系统的质量属性。其中，是指系统的响应能力，即要经过多长时间才能对某个事件做出响应，或者在某段时间内系统所能处理的事件的数量，这个质量属性是？', CAST('[{"key":"A","text":"性能"},{"key":"B","text":"可用性"},{"key":"C","text":"安全性"},{"key":"D","text":"可修改性"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '质量属性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在一个分布式软件系统中，一个构件失去了与另一个远程构件的连接。在系统修复后，连接于30秒之内恢复，系统可以重新正常工作。这一描述体现了软件系统的哪个质量属性？', CAST('[{"key":"A","text":"安全性"},{"key":"B","text":"可用性"},{"key":"C","text":"兼容性"},{"key":"D","text":"可移植性"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '质量属性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件系统质量属性（Quality Attribute）是一个系统的可测量或者可测试的属性，它被用来描述系统满足利益相关者需求的程度，其中，（  ）关注的是当需要修改缺陷、增加功能、提高质量属性时，定位修改点并实施修改的难易程度。', CAST('[{"key":"A","text":"可靠性"},{"key":"B","text":"可测试性"},{"key":"C","text":"可维护性"},{"key":"D","text":"可重用性"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '质量属性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件系统质量属性中，（  ）关注的是当用户数和数据量增加时，软件系统维持高服务质量的能力。', CAST('[{"key":"A","text":"可用性"},{"key":"B","text":"可扩展性"},{"key":"C","text":"可伸缩性"},{"key":"D","text":"可移植性"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '质量属性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述软件质量属性中的性能与可用性的区别，并各举一个例子。', CAST('[]' AS JSON), '性能关注系统响应时间、吞吐量等，在给定负载下系统能多快响应；可用性关注系统正常运行时间比例，系统在故障后恢复的能力。例如：性能要求是“在1000个并发用户下响应时间小于2秒”；可用性要求是“系统年可用性达到99.9%”。',
       CAST('["性能关注响应时间和吞吐量","可用性关注正常运行时间和恢复能力","性能例子","可用性例子"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '质量属性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'SYN Flooding攻击的原理是（ ）。', CAST('[{"key":"A","text":"利用TCP三次握手，恶意造成大量TCP半连接，耗尽服务器资源，导致系统拒绝服务"},{"key":"B","text":"操作系统在实现TCP/IP协议栈时，不能很好地处理TCP报文的序列号紊乱问题，导致系统崩溃"},{"key":"C","text":"操作系统在实现TCP/IP协议栈时，不能很好地处理IP分片包的重叠情况，导致系统崩溃"},{"key":"D","text":"操作系统协议栈在处理IP分片时，对于重组后超大的IP数据包不能很好地处理，导致缓存溢出而系统崩溃"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络安全';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '下面关于Kerberos认证的说法中，错误的是（ ）。', CAST('[{"key":"A","text":"Kerberos是基于对称密钥的认证协议"},{"key":"B","text":"Kerberos使用时间戳防止重放攻击"},{"key":"C","text":"Kerberos认证中心（KDC）保存所有用户的密钥"},{"key":"D","text":"Kerberos认证过程中，客户端首先向认证服务器（AS）申请票据授予票据（TGT）"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络安全';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述SYN Flooding攻击的原理及防御措施。', CAST('[]' AS JSON), 'SYN Flooding攻击利用TCP三次握手协议，攻击者发送大量伪造源IP的SYN请求，服务器响应SYN-ACK后等待ACK，导致大量半连接，耗尽服务器资源，无法提供正常服务。防御措施包括：限制SYN请求速率、使用SYN Cookie、增大半连接队列、过滤可疑IP等。',
       CAST('["原理：利用TCP三次握手，发送大量伪造SYN，造成半连接，耗尽资源","防御：限制SYN速率","防御：使用SYN Cookie","防御：增大半连接队列","防御：过滤可疑IP"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络安全';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '某Web网站向CA申请了数字证书。用户登录过程中可通过验证什么确认该数字证书的有效性？', CAST('[{"key":"A","text":"CA的签名"},{"key":"B","text":"网站的签名"},{"key":"C","text":"会话密钥"},{"key":"D","text":"DES密码"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络安全';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '某Web网站向CA申请了数字证书。用户登录过程中可通过验证CA的签名确认该数字证书的有效性，以做什么？', CAST('[{"key":"A","text":"向网站确认自己的身份"},{"key":"B","text":"获取访问网站的权限"},{"key":"C","text":"和网站进行双向认证"},{"key":"D","text":"验证该网站的真伪"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络安全';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '以下 wifi 认证方式中，（ ）使用了 AES 加密算法，安全性更高。', CAST('[{"key":"A","text":"开放式"},{"key":"B","text":"WPA"},{"key":"C","text":"WPA2"},{"key":"D","text":"WEP"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络安全';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '以下关于Kerberos认证系统的叙述中，错误的是（ ）。', CAST('[{"key":"A","text":"Kerberos是在开放的网络中为用户提供身份认证的一种方式"},{"key":"B","text":"系统中的用户要相互访问必须首先向CA申请票据"},{"key":"C","text":"KDC中保存着所有用户的账号和密码"},{"key":"D","text":"Kerberos使用时间戳来防止重放攻击"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'Kerberos认证';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述Kerberos认证系统的基本角色和认证流程。', CAST('[]' AS JSON), 'Kerberos系统至少包含三个角色：认证服务器（AS）、客户端（Client）和普通服务器（Server）。客户端和服务器在AS的帮助下完成相互认证。认证流程：客户端首先向密钥分发中心（KDC）申请初始票据，获得票据后，客户端使用该票据向目标服务器请求服务，服务器验证票据后建立会话。',
       CAST('["答出三个角色：认证服务器、客户端、普通服务器","说明客户端和服务器在AS帮助下完成认证","描述客户端向KDC申请初始票据","描述服务器验证票据的过程"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'Kerberos认证';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '某软件公司研发人员将职务开发的软件中的算法和部分程序代码公开发表，该研发人员（ ），该公司丧失了该软件的（ ）。', CAST('[{"key":"A","text":"与公司共同享有该软件的著作权，是正常行使发表权"},{"key":"B","text":"与公司共同享有该软件的著作权，是正常行使信息网络传播权"},{"key":"C","text":"不享有该软件的著作权，其行为涉嫌侵犯公司的专利权"},{"key":"D","text":"不享有该软件的著作权，其行为涉嫌侵犯公司的软件著作权"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '知识产权';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '按照《中华人民共和国著作权法》的权利保护期，下列权利中受到永久保护的是（ ）。', CAST('[{"key":"A","text":"发表权"},{"key":"B","text":"修改权"},{"key":"C","text":"复制权"},{"key":"D","text":"发行权"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '知识产权';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述软件著作权中人身权和财产权的区别，并举例说明。', CAST('[]' AS JSON), '软件著作权包括人身权和财产权。人身权包括署名权、修改权、保护作品完整权等，与作者身份密切相关，不可转让；财产权包括复制权、发行权、展览权、改编权、信息网络传播权等，可以转让和许可使用。例如，修改权属于人身权，受永久保护；复制权属于财产权，保护期有限。',
       CAST('["指出人身权包括署名权、修改权、保护作品完整权","指出财产权包括复制权、发行权、信息网络传播权等","说明人身权与作者身份相关，不可转让","说明财产权可转让和许可使用","举例说明两者区别"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '知识产权';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '赵某购买了一款有注册商标的应用App，擅自复制成光盘出售，其行为是侵犯什么的行为？', CAST('[{"key":"A","text":"注册商标专用权"},{"key":"B","text":"软件著作权"},{"key":"C","text":"光盘所有权"},{"key":"D","text":"软件专利权"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '知识产权';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '下列关于著作权归属的表述，正确的是？', CAST('[{"key":"A","text":"改编作品的著作权归属于改编人"},{"key":"B","text":"职务作品的著作权都归属于企业法人"},{"key":"C","text":"委托作品的著作权都归属于委托人"},{"key":"D","text":"合作作品的著作权归属于所有参与和组织创作的人"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '知识产权';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'X公司接受Y公司的委托开发了一款应用软件，双方没有订立任何书面合同。在此情形下，谁享有该软件的著作权？', CAST('[{"key":"A","text":"X、Y公司共同"},{"key":"B","text":"X公司"},{"key":"C","text":"Y公司"},{"key":"D","text":"X、Y公司均不"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '知识产权';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述软件著作权归属的一般原则，并说明委托开发和职务开发的情况。', CAST('[]' AS JSON), '软件著作权归属的一般原则：谁创作谁享有。委托开发中，若无书面合同约定，著作权归受托人（开发者）；职务开发中，若属于职务作品，著作权一般归单位，但作者享有署名权；合作开发则归合作者共同享有。',
       CAST('["一般原则：谁创作谁享有","委托开发无约定归开发者","职务作品一般归单位","合作作品归合作者共同享有"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '知识产权';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '程序员甲将其编写完成的某软件程序发给同事乙并进行讨论，之后甲放弃该程序并决定重新开发，后来乙将该程序稍加修改并署自己名在某技术论坛发布。以下说法中，正确的是（ ）。', CAST('[{"key":"A","text":"乙的行为侵犯了甲对该程序享有的软件著作权"},{"key":"B","text":"乙的行为未侵权，因其发布的场合是以交流学习为目的的技术论坛"},{"key":"C","text":"乙的行为没有侵犯甲的软件著作权，因为甲已放弃该程序"},{"key":"D","text":"乙对该程序进行了修改，因此乙享有该程序的软件著作权"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '知识产权';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '以下关于软件著作权产生时间的叙述中，正确的是（ ）。', CAST('[{"key":"A","text":"软件著作权产生自软件首次公开发表时"},{"key":"B","text":"软件著作权产生自开发者有开发意图时"},{"key":"C","text":"软件著作权产生自软件开发完成之日起"},{"key":"D","text":"软件著作权产生自软件著作权登记时"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '知识产权';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'M 公司将其开发的某软件产品注册了商标，为确保公司可在市场竞争中占据优势地位，M 公司对员工进行了保密约束，此情形下，该公司不享有（ ）。', CAST('[{"key":"A","text":"软件著作权"},{"key":"B","text":"专利权"},{"key":"C","text":"商业秘密权"},{"key":"D","text":"商标权"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '知识产权';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '某厂生产的某种电视机，销售价为每台2500元，去年的总销售量为25000台，固定成本总额为250万元，可变成本总额为4000万元，税率为16%，则该产品年销售量的盈亏平衡点为（ ）台。', CAST('[{"key":"A","text":"5000"},{"key":"B","text":"10000"},{"key":"C","text":"15000"},{"key":"D","text":"20000"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '应用数学';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述盈亏平衡点的概念及其计算方法。', CAST('[]' AS JSON), '盈亏平衡点是指销售收入等于总成本时的销售量或销售额。计算方法：设销售量为N，固定成本为F，单位可变成本为v，销售单价为p，税率为t，则总成本=F+vN，总收益=pN(1-t)。令总成本等于总收益，解出N即为盈亏平衡点。',
       CAST('["定义盈亏平衡点","列出总成本和总收益公式","说明令两者相等求解","给出计算示例"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '应用数学';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '系统设计的目的是确定一种（ ），该架构定义了用于构建所建议信息系统的技术。', CAST('[{"key":"A","text":"数据模型"},{"key":"B","text":"过程模型"},{"key":"C","text":"物理数据流程图"},{"key":"D","text":"应用体系架构"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '系统设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述系统设计阶段的主要任务和输入。', CAST('[]' AS JSON), '系统设计阶段的主要任务是确定应用体系架构，定义构建信息系统的技术。通过分析需求分析阶段创建的数据模型和过程模型来完成。物理数据流程图用于建立物理过程和数据存储。输入包括从各种来源获取的事实、建议和意见，以及决策分析阶段获批的系统建议。',
       CAST('["确定应用体系架构","分析数据模型和过程模型","使用物理数据流程图","输入包括事实、建议、意见和系统建议"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '系统设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请从数据处理方式、系统的可扩展性和处理性能三个方面，比较管道-过滤器架构风格和仓库架构风格的优缺点，并说明在线软件开发系统更适合哪种架构风格。', CAST('[]' AS JSON), '管道-过滤器风格：数据处理方式为数据流驱动，各过滤器独立处理数据，可扩展性较好，但性能受过滤器间数据传输影响；仓库风格：数据处理方式为中心数据存储，各构件共享数据，可扩展性较好，但性能受中心存储访问瓶颈影响。在线软件开发系统需要频繁交互和共享数据，更适合仓库风格。',
       CAST('["分别描述两种风格的数据处理方式","比较可扩展性","比较处理性能","给出结论并说明理由"]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在三层C/S架构中，是应用的用户接口部分，负责与应用逻辑间的对话功能的是哪一层？', CAST('[{"key":"A","text":"表示层"},{"key":"B","text":"感知层"},{"key":"C","text":"设备层"},{"key":"D","text":"业务逻辑层"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在三层C/S架构中，是应用的本体，负责具体的业务处理逻辑的是哪一层？', CAST('[{"key":"A","text":"数据层"},{"key":"B","text":"分发层"},{"key":"C","text":"功能层"},{"key":"D","text":"算法层"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '针对该系统的功能，赵工建议采用解释器架构风格，李工建议采用管道-过滤器架构风格，王工则建议采用隐式调用架构风格。请针对平台的核心应用场景，从机器学习流程定义的灵活性和学习算法的可扩展性两个方面对三种架构风格进行对比与分析，并指出该平台更适合采用哪种架构风格。', CAST('[]' AS JSON), '该平台更适合采用解释器架构风格。解释器风格自定义规则，灵活性和可扩展性高；管道-过滤器风格数据流固定，灵活性不足；隐式调用风格事件驱动，但流程定义不直观。',
       CAST('["解释器风格：自定义规则，灵活性和可扩展性高","管道-过滤器风格：数据流固定，灵活性不足","隐式调用风格：事件驱动，但流程定义不直观","结论：解释器风格最适合"]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '针对该系统的功能，李工建议采用面向对象的架构风格，将折扣力度计算和用户筛选分别封装为独立对象，通过对象调用实现对应的功能；王工则建议采用解释器（interpreters）架构风格，将折扣力度计算和用户筛选条件封装为独立的规则，通过解释规则实现对应的功能。请针对系统的主要功能，从折扣规则的可修改性、个性化折扣定义灵活性和系统性能三个方面对这两种架构风格进行比较与分析，并指出该系统更适合采用哪种架构风格。', CAST('[]' AS JSON), '面向对象风格：将折扣规则和筛选逻辑封装为对象，修改规则需修改代码并重新编译，可修改性较差；灵活性较低，但性能较好。解释器风格：规则以数据形式存在，修改规则无需修改代码，可修改性好；灵活性高，但性能较差。系统需求强调灵活设置折扣规则和促销活动，且用户规模不大，性能要求不高，因此更适合采用解释器风格。',
       CAST('["比较可修改性：解释器优于面向对象","比较灵活性：解释器优于面向对象","比较性能：面向对象优于解释器","结合需求选择解释器风格"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'MULTI', '关于仓库架构风格，以下说法正确的是？', CAST('[{"key":"A","text":"数据存储在中心仓库，处理流程独立，支持交互式处理。"},{"key":"B","text":"数据与处理紧密关联，调整处理流程需要系统重新启动。"},{"key":"C","text":"数据与处理分离，需要加载数据，性能降低。"},{"key":"D","text":"数据处理组件之间一般无依赖关系，可并发调用，提高性能。"}]' AS JSON), 'ACD',
       CAST('["仓库架构风格中数据与处理分离，处理流程独立，支持交互式处理。","调整处理流程不需要重启系统，因此B错误。","数据与处理分离导致加载数据时性能降低。","组件之间无依赖关系，可并发调用，提高性能。"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '仓库架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述仓库架构风格的优缺点。', CAST('[]' AS JSON), '优点：数据与处理分离，各处理组件独立，可并发执行，提高性能；支持交互式处理，易于扩展新组件。缺点：数据与处理分离导致数据加载开销，性能可能降低；数据一致性维护复杂；组件间通信可能依赖中心仓库，存在性能瓶颈。',
       CAST('["优点：数据与处理分离，组件独立，并发执行，性能高。","优点：支持交互式处理，易于扩展。","缺点：数据加载开销，性能降低。","缺点：数据一致性维护复杂。","缺点：中心仓库可能成为性能瓶颈。"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '仓库架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请从描述语言、非功能性需求描述、需求和架构的一致性等三个方面，用300字以内的文字说明软件需求到架构的映射存在哪些难点。', CAST('[]' AS JSON), '难点包括：
（1）需求和架构描述语言存在差异：软件需求是频繁获取的非正规的自然语言，而软件架构常用某种正式语言。
（2）非功能属性难以在架构中描述：系统属性中描述的非功能性需求通常很难在架构模型中形成规约。
（3）需求和架构的一致性难以保障：从软件需求映射到软件架构的过程中，保持一致性和可追溯性很难，且复杂程度很高，因为单一的软件需求可能定位到多个软件架构的关注点。反之，架构元素也可能有多个软件需求。',
       CAST('["描述语言差异：需求是非正式自然语言，架构是正式语言。","非功能性需求难以在架构中描述。","一致性和可追溯性难以保障。","需求与架构可能多对多映射。"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '需求到架构的映射';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请分析图3-1给出的FACE架构的相关信息，用300字以内的文字简要说明FACE 5个段的含义。', CAST('[]' AS JSON), '（1）操作系统服务段：为FACE架构其他段提供操作系统、运行时和操作系统级健康监控等服务。通过开放式OSGi框架为上层功能提供OS标准接口，并可实现上层组件的即插即用能力。
（2）I/O服务段：主要针对专用I/O设备进行抽象，屏蔽平台服务段软件与硬件设备的关系，形成一种虚拟设备，这里隐含着对系统中的所有硬件I/O的虚拟化。
（3）平台服务段：主要是指平台/用户需要的共性服务软件，主要涵盖跨平台的系统管理、共享设备服务，以及健康管理等。
（4）传输服务段：通过使用传统跨平台中间件软件（如CORBA、DDA等），为平台上层可移植组件段提供平台性的数据交换服务。
（5）可移植组件段：为用户软件段，提供了多组件使用能力和功能服务。',
       CAST('["操作系统服务段提供操作系统、运行时和健康监控。","I/O服务段抽象专用I/O设备，虚拟化硬件。","平台服务段提供跨平台系统管理、共享设备服务。","传输服务段提供数据交换服务。","可移植组件段提供用户软件组件。"]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'FACE架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请用200字以内的文字简要说明在可移植性上，应用程序的紧耦合和封装问题的主要表现分别是什么，并给出解决方案。', CAST('[]' AS JSON), '紧耦合问题主要表现在I/O问题、业务逻辑问题和表现问题。解决方案：采用分离原则，通过隔离实现硬件特定信息和少数模块的代码，来减少耦合性。
封装问题主要表现在ICD硬编码问题、组件的紧耦合问题、直接调用问题。解决方案：通过提供数据源或槽的软件服务的方法，从紧耦合组件分解出应用程序，并将平台相关部分加入计算环境中，在计算平台内提供数据源或槽的软件服务，并实现接口标准化。',
       CAST('["紧耦合表现：I/O问题、业务逻辑问题、表现问题。","紧耦合解决方案：分离原则，隔离硬件特定信息。","封装表现：ICD硬编码、组件紧耦合、直接调用。","封装解决方案：提供数据源/槽软件服务，接口标准化。"]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'FACE架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述FACE架构中操作系统服务段、I/O服务段、平台服务段、传输服务段和可移植组件段各自的主要功能。', NULL, '操作系统服务段为其他段提供操作系统、运行时和操作系统级健康监控等服务，通过OSGi框架提供标准接口和即插即用能力；I/O服务段对专用I/O设备进行抽象，屏蔽硬件关系，但不抽象GPU驱动；平台服务段提供系统级健康监控、配置、日志和流媒体等共性软件；传输服务段为上层可移植组件段提供平台性的数据交换服务，禁止组件间直接调用；可移植组件段提供多组件使用能力和功能服务，包括公共服务和可移植组件。',
       CAST('["操作系统服务段：提供OS服务、健康监控、OSGi","I/O服务段：抽象I/O设备，不抽象GPU驱动","平台服务段：提供共性软件如健康监控、配置、日志","传输服务段：提供数据交换服务，禁止直接调用","可移植组件段：提供多组件使用能力和功能服务"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'FACE架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '在FACE架构中，紧耦合问题和封装问题分别表现在哪些方面？请提出相应的解决方案。', NULL, '紧耦合问题主要表现在I/O问题、业务逻辑问题和表现问题。解决方案：采用分离原则，通过隔离硬件特定信息和少数模块的代码，减少耦合性。封装问题主要表现在ICD硬编码问题、组件的紧耦合问题、直接调用问题。解决方案：通过提供数据源或槽的软件服务，将紧耦合组件分解出应用程序，并将平台相关部分加入计算环境中，在计算平台内提供数据源或槽的软件服务，并实现接口标准化。',
       CAST('["紧耦合问题：I/O、业务逻辑、表现","紧耦合解决方案：分离原则，隔离硬件特定信息","封装问题：ICD硬编码、组件紧耦合、直接调用","封装解决方案：数据源/槽服务，分解组件，接口标准化"]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'FACE架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述软件需求与软件架构之间存在的主要差异，并说明这些差异带来的挑战。', NULL, '主要差异包括：1）需求和架构描述语言存在差异，需求是非正规的自然语言，架构是正式语言；2）非功能属性难于在架构中描述；3）需求和架构的一致性难以保障。挑战：需求到架构的映射复杂，单一需求可能对应多个架构关注点，反之亦然，导致可追溯性差。',
       CAST('["指出语言差异","指出非功能属性描述困难","指出一致性和可追溯性难以保障","说明映射复杂性和多对多关系"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '需求与架构差异';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在Redis中，用于实现用户帖子的评论计数器功能（功能a）最合适的数据类型是？', CAST('[{"key":"A","text":"STRING"},{"key":"B","text":"LIST"},{"key":"C","text":"SET"},{"key":"D","text":"ZSET"}]' AS JSON), 'A',
       CAST('["计数器是简单的增减操作","STRING类型支持incr/decr操作"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'Redis数据类型';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'MULTI', '在Redis中，支持粉丝列表功能（功能b）和好友信息的发布/订阅功能（功能g）的数据类型包括？', CAST('[{"key":"A","text":"STRING"},{"key":"B","text":"LIST"},{"key":"C","text":"SET"},{"key":"D","text":"HASH"},{"key":"E","text":"ZSET"}]' AS JSON), 'BC',
       CAST('["粉丝列表可用LIST或SET存储","发布/订阅可用LIST的阻塞操作或专门的pub/sub机制"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'Redis数据类型';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在Redis中，用于实现共同好友功能（功能d）最合适的数据类型是？', CAST('[{"key":"A","text":"STRING"},{"key":"B","text":"LIST"},{"key":"C","text":"SET"},{"key":"D","text":"ZSET"}]' AS JSON), 'C',
       CAST('["共同好友需要集合的交集操作","SET类型支持交集运算"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'Redis数据类型';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在Redis中，用于实现排名功能（功能e）最合适的数据类型是？', CAST('[{"key":"A","text":"STRING"},{"key":"B","text":"LIST"},{"key":"C","text":"SET"},{"key":"D","text":"ZSET"}]' AS JSON), 'D',
       CAST('["排名需要按分数排序","ZSET是有序集合，支持排序"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'Redis数据类型';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在Redis中，用于存储用户信息的结构化数据（功能f）最合适的数据类型是？', CAST('[{"key":"A","text":"STRING"},{"key":"B","text":"LIST"},{"key":"C","text":"HASH"},{"key":"D","text":"ZSET"}]' AS JSON), 'C',
       CAST('["用户信息是结构化数据","HASH适合存储对象"]' AS JSON), 'EASY', '### 正确答案与考生对错

**正确答案：C（HASH）**  
你的选择：**B（LIST）**，回答错误。LIST 适合存储有序列表（如消息队列），但存储用户结构化信息（如姓名、年龄、邮箱）时，HASH 才是最优解。

---

### 选项解析

- **A. STRING（错误）**：STRING 只能存储单个字符串或序列化对象（如 JSON），但无法对字段单独读写，修改一个字段需整体反序列化，效率低且不灵活。
- **B. LIST（错误）**：LIST 是双向链表，支持按顺序存取，适合消息队列、时间线等场景，但无法按字段名直接访问，不适合表示键值对结构。
- **C. HASH（正确）**：HASH 类似 Java 的 `Map<String, String>`，可存储多个字段（如 `name`、`age`），支持对单个字段的增删改查，内存紧凑且操作高效，是存储用户信息的标准选择。
- **D. ZSET（错误）**：ZSET 是有序集合，每个成员带分数，用于排行榜、排序场景，不适合存储无排序需求的用户属性。

---

### 面试考点延伸

- **HASH 优势**：相比 STRING 序列化，HASH 支持部分更新（`HINCRBY` 年龄自增），且节省内存（小 HASH 使用 ziplist 编码）。
- **实际应用**：用户信息、购物车、对象缓存等场景优先考虑 HASH；若需按时间排序（如用户动态），则用 LIST；若需按分数排名（如积分榜），则用 ZSET。
- **易错点**：不要混淆“结构化数据”与“有序数据”，HASH 无序但字段可命名，LIST 有序但字段不可命名。',
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'Redis数据类型';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请比较Redis的RDB持久化和AOF持久化在磁盘更新频率、数据安全、数据一致性、重启性能和数据文件大小五个方面的差异，并说明在系统需要快速重启恢复的场景下应选择哪种方式及原因。', NULL, '磁盘更新频率：AOF比RDB文件更新频率高。数据安全：AOF比RDB更安全。数据一致性：RDB间隔一段时间存储，可能发生数据丢失和不一致；AOF通过append模式写文件，即使发生服务器宕机，也可通过redis-check-aof工具解决数据一致性问题。重启性能：RDB性能比AOF好。数据文件大小：AOF文件比RDB文件大。在系统需要快速重启恢复的场景下，应选择RDB，因为RDB重启性能更好，能最快恢复服务。',
       CAST('["磁盘更新频率：AOF高","数据安全：AOF更安全","数据一致性：RDB可能丢失数据，AOF更一致","重启性能：RDB更好","数据文件大小：AOF更大","选择RDB原因：重启性能好"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'Redis持久化';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请描述Redis的“定期删除+惰性删除”策略失效的场景，并给出三种内存淘汰机制。', NULL, '失效场景：如果“定期删除”没删除KEY，也没即时去请求KEY，也就是说“惰性删除”也没生效，这样策略就失效。三种内存淘汰机制：1）从已设置过期时间的数据集最近最少使用的数据淘汰（volatile-lru）；2）从已设置过期时间的数据集将要过期的数据淘汰（volatile-ttl）；3）从数据集最近最少使用的数据淘汰（allkeys-lru）。',
       CAST('["失效场景：定期删除未删，惰性删除未触发","淘汰机制1：volatile-lru","淘汰机制2：volatile-ttl","淘汰机制3：allkeys-lru"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'Redis内存淘汰';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请将以下非功能性需求分类：a)系统应支持大于100个工业设备的并行监测；b)设备数据从制造现场传输到系统后台的传输时间小于1s；c)系统应7*24小时工作；d)可抵御常见XSS攻击；e)系统在故障情况下，应在0.5小时内恢复；f)支持数据审计。分别归类为性能、安全性、可用性。', NULL, '性能：a、b；安全性：d、f；可用性：c、e。',
       CAST('["性能：a（并行监测）、b（传输时间）","安全性：d（抵御XSS）、f（数据审计）","可用性：c（7*24）、e（故障恢复）"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '非功能性需求';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述SSM框架（Spring+Spring MVC+Mybatis）中各组件的职责，并说明其工作流程。', NULL, 'SSM框架是标准的MVC模式：Spring MVC负责请求的转发和视图管理，Spring实现业务对象管理，Mybatis作为数据对象的持久化引擎。工作流程：用户发送HTTP请求到前端控制器（DispatcherServlet），前端控制器根据请求信息调用处理器映射器（HandlerMapping）找到对应的Controller，Controller调用业务层（Service）处理业务逻辑，业务层通过Mybatis访问数据库，返回结果给Controller，Controller将模型数据传给视图解析器（ViewResolver），最终渲染视图返回给用户。',
       CAST('["Spring MVC：请求转发和视图管理","Spring：业务对象管理","Mybatis：持久化引擎","工作流程：请求->前端控制器->Controller->Service->Mybatis->视图"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'SSM框架';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '在工业设备监测系统中，采用标准的数据访问机制（如OPC UA）与多种不同设备进行数据交互的原因是什么？', NULL, '采用标准数据访问机制的原因：1）实现设备无关性，屏蔽不同设备的差异；2）提供统一的数据模型和接口，简化开发；3）支持互操作性，便于系统集成；4）提高可扩展性，方便添加新设备；5）增强安全性，标准通常包含安全机制。',
       CAST('["设备无关性","统一数据模型和接口","互操作性","可扩展性","安全性"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据访问机制';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述标准数据访问机制（如OPC）在工业设备检测系统中的作用。', CAST('[]' AS JSON), '标准数据访问机制在硬件供应商和软件开发商之间建立了一套完整的规则，使得数据交互对双方透明。硬件供应商只需考虑应用程序的多种需求和传输协议，软件开发商不必了解硬件的实质和操作过程，从而实现对设备数据采集的统一管理。',
       CAST('["建立硬件供应商和软件开发商之间的规则","数据交互对双方透明","硬件供应商只需考虑应用需求和传输协议","软件开发商无需了解硬件细节","实现设备数据采集的统一管理"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据访问机制';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请说明OPC（OLE for Process Control）的提出目的及其系统构成。', CAST('[]' AS JSON), 'OPC是为了不同供应厂商的设备和应用程序之间的软件接口标准化，使数据交换更加简单化而提出的。OPC系统由OPC服务器、OPC接口和OPC应用程序构成。OPC服务器按照各个供应厂商的硬件开发，吸收硬件和系统的差异，实现不依存于硬件的系统构成；同时利用Variant数据类型，不依存于硬件中固有数据类型。',
       CAST('["OPC目的是标准化不同厂商设备和应用程序的软件接口","使数据交换简单化","系统构成：OPC服务器、OPC接口、OPC应用程序","OPC服务器吸收硬件差异，实现不依存于硬件的系统","利用Variant数据类型不依存于硬件固有数据类型"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'OPC标准';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述企业集成架构设计中的三类技术（数据集成、应用集成、企业集成）分别要解决的问题及其含义。', CAST('[]' AS JSON), '数据集成解决不同应用和系统间的数据共享和交换需求，包括共享信息管理、共享模型管理和数据操作管理。应用集成解决两个或多个应用系统根据业务逻辑进行功能互相调用和互操作，在数据集成基础上实现。企业集成解决企业间应用集成和交互，通常采用多层结构提高系统柔性。',
       CAST('["数据集成解决数据共享和交换","应用集成解决应用间功能互操作","企业集成解决企业间集成和交互","数据集成包括共享信息管理、共享模型管理、数据操作管理","应用集成在数据集成基础上实现","企业集成采用多层结构提高柔性"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '企业集成架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请列举数据集成、应用集成和企业集成各自包含的集成模式。', CAST('[]' AS JSON), '数据集成的模式包括：数据联邦、数据复制模式、基于结构的数据集成模式。应用集成的模式包括：集成适配器模式、集成信使模式、集成面板模式和集成代理模式。企业集成的模式包括：前端集成模式、后端集成模式和混合集成模式。',
       CAST('["数据集成模式：数据联邦、数据复制、基于结构","应用集成模式：适配器、信使、面板、代理","企业集成模式：前端、后端、混合"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '企业集成架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述软件缺陷管理的基本流程。', CAST('[]' AS JSON), '软件缺陷管理的基本流程包括：缺陷提交、缺陷审查、修复流程、验证流程、缺陷关闭。测试人员发现缺陷后提交缺陷报告；审查确定缺陷问题、种类和级别；审查通过后进入修复流程，转发给开发人员修复；开发人员提交修复后代码，进入验证流程，通过回归测试等方法验证；确认完全解决后关闭缺陷。',
       CAST('["缺陷提交","缺陷审查","修复流程","验证流程","缺陷关闭"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件缺陷管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请列举常见的缺陷种类和级别。', CAST('[]' AS JSON), '根据IEEE标准，缺陷主要包括输入/输出错误、逻辑错误、计算错误、接口错误、数据错误等；从软件测试角度可分为功能缺陷、系统缺陷、加工缺陷、数据缺陷、代码缺陷。根据严重程度，Beizer将缺陷分为十级：轻微、中等、使人不悦、影响使用、严重、非常严重、极为严重、无法容忍、灾难性、传染性。',
       CAST('["IEEE标准缺陷种类：输入/输出、逻辑、计算、接口、数据","测试角度缺陷分类：功能、系统、加工、数据、代码","Beizer十级缺陷：轻微到传染性"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件缺陷管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请阐述云原生架构的五个设计原则（服务化、弹性、可观测、韧性、自动化）的内涵。', CAST('[]' AS JSON), '服务化原则：将代码按生命周期拆分，通过服务化架构加快迭代和稳定性，模块间通过接口编程，提高复用。弹性原则：系统部署规模随业务量变化自动伸缩，无需事先规划固定资源，提高资源利用率。可观测原则：通过日志、链路跟踪、度量等手段，使每次请求背后的调用耗时、参数等清晰可见，支持运维和业务分析。韧性原则：当软硬件组件出现异常时，软件表现出抵御能力，核心目标是提升MTBF。自动化原则：通过自动化工具标准化交付过程，实现软件交付和运维的自动化。',
       CAST('["服务化：拆分模块，面向接口，提高复用","弹性：自动伸缩，提高资源利用率","可观测：日志、链路、度量，清晰可见","韧性：抵御异常，提升MTBF","自动化：标准化和自动化交付"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '云原生架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述数据分片技术的概念及其主要目的。', CAST('[]' AS JSON), '数据分片技术是将数据库中的数据按照某种规则分散存储到多个数据库或节点上，以提升系统的扩展性和性能。主要目的是解决单库数据量过大导致的性能瓶颈，通过水平或垂直拆分，实现负载均衡，提高并发处理能力。',
       CAST('["数据分片是将数据分散存储到多个节点","提升扩展性和性能","解决单库性能瓶颈","水平或垂直拆分","实现负载均衡，提高并发"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据分片技术';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请列举常见的数据分片策略，并简要说明其优缺点。', CAST('[]' AS JSON), '常见的数据分片策略包括：范围分片、哈希分片、列表分片、复合分片等。范围分片按数据范围划分，实现简单，但可能数据倾斜；哈希分片通过哈希函数均匀分布，避免倾斜，但范围查询困难；列表分片按预定义列表划分，灵活但扩展性差；复合分片结合多种策略，更灵活但实现复杂。',
       CAST('["范围分片：简单，可能倾斜","哈希分片：均匀，范围查询困难","列表分片：灵活，扩展性差","复合分片：灵活，实现复杂"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据分片技术';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简要阐述 Hash 分片、一致性 Hash 分片和按照数据范围分片三种方式的原理。', CAST('[]' AS JSON), 'Hash 分片：基于哈希表思想，按照数据的某一特征（key）计算哈希值，并将哈希值与系统中的节点建立映射关系，从而将数据分布到不同节点。优点：映射关系简单，元数据少；缺点：节点增减时大量数据迁移，且可能数据不均衡。一致性 Hash 分片：将数据和节点映射到一个首尾相接的 Hash 环上，数据从其在环上的位置顺时针找到第一个节点作为存储节点。优点：节点增减时仅影响环上相邻节点，数据迁移量小；缺点：需要维护节点在环上的位置。按照数据范围分片：将关键值划分成不同区间，每个物理节点负责一个或多个区间。区间大小不固定，可根据数据量动态调整，节点可负责多个块，块达到阈值可分裂。优点：数据均衡性好，节点增减时调整灵活；缺点：需要维护区间映射，元数据较多。',
       CAST('["Hash 分片基于哈希函数映射数据到节点，简单但节点变化影响大","一致性 Hash 使用环状结构，节点增减影响局部","范围分片按关键值区间划分，可动态调整","三种方式各有优缺点，适用于不同场景"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据分片技术';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '具体阐述你参与管理和开发的项目采用了哪些分片方式，并说明其实现过程和应用效果。', CAST('[]' AS JSON), '在项目中，我们采用了基于数据范围的分片方式。具体实现过程：首先，根据业务数据的特点，选择订单 ID 作为分片键，将订单数据按照 ID 范围划分为多个区间，每个区间对应一个数据库分片。然后，通过配置中心维护分片映射表，记录每个区间的起始值和结束值以及对应的数据库节点。当写入数据时，根据订单 ID 计算所属区间，路由到相应的数据库；读取时同样根据 ID 定位。应用效果：数据分布均匀，避免了热点问题；扩展性好，当数据量增加时，可以动态添加分片，只需调整映射表；查询效率高，因为查询条件通常包含分片键，可以直接定位到具体分片。',
       CAST('["采用范围分片，选择合适的分片键","维护分片映射表，实现路由","数据分布均匀，扩展性好","查询效率高，支持动态调整"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据分片技术';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '前趋图是一个有向无环图，记为：→={(Pi,Pj) | Pi must complete before Pj may start}。假设系统中进程 P={P1,P2,P3,P4,P5,P6,P7,P8}，且进程的前驱图如下：

（此处应有前驱图，但原文未提供，根据选项推断）

下列哪一项表示正确的前驱关系？', CAST('[{"key":"A","text":"→={（P1，P2），（P3，P1），（P4，P1），（P5，P2），（P5，P3），（P6，P4），（P7，P5），（P7，P6），（P5，P6），（P4，P5），（P6，P7），（P7，P6）}"},{"key":"B","text":"→={（P1，P2），（P1，P3），（P2，P5），（P2，P3），（P3，P4），（P3，P5），（P4，P5），（P5，P6），（P5，P7），（P8，P5），（P6，P7），（P7，P8）}"},{"key":"C","text":"→={（P1，P2），（P1，P3），（P2，P3），（P2，P5），（P3，P4），（P3，P5），（P4，P6），（P5，P6），（P5，P7），（P5，P8），（P6，P8），（P7，P8）}"},{"key":"D","text":"→={（P1，P2），（P1，P3），（P2，P3），（P2，P5），（P3，P6），（P3，P4），（P4，P7），（P5，P6），（P6，P7），（P6，P5），（P7，P5），（P7，P8）}"}]' AS JSON), 'C',
       CAST('["前趋图必须是有向无环图","每条边表示前驱关系，必须满足偏序关系","根据选项和常识判断，C 符合典型前驱图"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '操作系统进程管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '某计算机系统页面大小为 4K，进程 P1 的页面变换表如下图示，看 P1 要访问数据的逻辑地址为十六进制 1B1AH，那么该逻辑地址经过变换后，其对应的物理地址应为十六进制（ ）', CAST('[{"key":"A","text":"1B1AH"},{"key":"B","text":"3B1AH"},{"key":"C","text":"6B1AH"},{"key":"D","text":"8B1AH"}]' AS JSON), 'C',
       CAST('["页面大小 4K，低 12 位为页内偏移","逻辑地址 1B1AH 中，高 4 位为页号 1","页号 1 对应物理块号 6，拼接得到物理地址 6B1AH"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '操作系统存储管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '某文件系统文件存储采用文件索引节点法。假设文件索引节点中有 8 个地址项 iaddr[0]~iaddr[7]，每个地址项大小为 4 字节，其中地址项 iaddr[0]~iaddr[4]为直接地址索引，iaddr[5]、iaddr[6]是一级间接地址索引，iaddr[7]是二级间接地址索引，磁盘索引块和磁盘数据块大小均为 1KB，若要访问 iclsClient.dll 文件的逻辑块号分别为 1、518，则系统应分别采用（ ）。', CAST('[{"key":"A","text":"直接地址索引、直接地址索引"},{"key":"B","text":"直接地址索引、一级间接地址索引"},{"key":"C","text":"直接地址索引、二级间接地址索引"},{"key":"D","text":"一级间接地址索引、二级间接地址索引"}]' AS JSON), 'C',
       CAST('["直接地址索引覆盖逻辑块号 0-4","一级间接索引每个可索引 256 块，两个覆盖 5-516","逻辑块号 518 超出，需二级间接索引"]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '文件系统';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '若系统正在将（ ）文件修改的结果写回磁盘时系统发生掉电，则对系统的影响相对较大。', CAST('[{"key":"A","text":"目录"},{"key":"B","text":"空闲块"},{"key":"C","text":"用户程序"},{"key":"D","text":"用户数据"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '文件系统';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '假设系统中互斥资源 R 的可用数为 25。T0 时刻进程 P1、P2、P3、P4 对资源 R 的最大需求数、已分配资源数和尚需资源数的情况如表 a 所示，若 P1 和 P3 分别申请资源 R 数为 1 和 2，则系统（ ）。', CAST('[{"key":"A","text":"只能先给 P1 进行分配，因为分配后系统状态是安全的"},{"key":"B","text":"只能先给 P3 进行分配，因为分配后系统状态是安全的"},{"key":"C","text":"可以时后 P1、P3.进行分配，因为分配后系统状态是安全的"},{"key":"D","text":"不能给 P3 进行分配，因为分配后系统状态是不安全的"}]' AS JSON), 'B',
       CAST('["计算当前可用资源数：25 - (6+4+7+6) = 2","只有 P3 的尚需资源数小于等于可用数，可满足","分配后系统安全，因此只能先给 P3 分配"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '操作系统死锁';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '看关系 R、S 如下表所示，则关系 R 与 S 进行自然连接运算后的属性列数和元组个数分别为（ ）。', CAST('[{"key":"A","text":"6 和 7"},{"key":"B","text":"4 和 4"},{"key":"C","text":"4 和 3"},{"key":"D","text":"3 和 4"}]' AS JSON), 'C',
       CAST('["自然连接会去掉重复属性列","R 和 S 共有属性列合并后为 4 列","只有相同属性值相等的元组才会连接，得到 3 个元组"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库关系运算';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '一般说来，SoC 称为系统级芯片，也称片上系统，它是一个有专用目标的集成电路产品，以下关于 SoC 不正确的说法是（ ）。', CAST('[{"key":"A","text":"SoC 是一种技术，是以实际的、确定的系统功能开始，到软/硬件划分，并完成设计的整个过程"},{"key":"B","text":"SoC 是一款具有运算能力的处理器芯片，可面向特定用途进行定制的标准产品"},{"key":"C","text":"SoC 是信息系统核心的芯片集成，是将系统关键部件集成在一块芯片上，完成信息系统的核心功能"},{"key":"D","text":"SoC 是将微处理器、模拟 IP 核、数字 IP 核和存储器（或片外存储控制接口）集成在单一芯片上，是面向特定用途的标准产品"}]' AS JSON), 'B',
       CAST('["SoC 是系统级芯片，包含完整系统","B 选项片面，SoC 不仅是处理器芯片，还包含其他部件","ACD 描述正确"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '嵌入式系统';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '嵌入式实时操作系统与一般操作系统相比，具备许多特点。以下不属于嵌入式实时操作系统特点的是（ ）。', CAST('[{"key":"A","text":"可剪裁性"},{"key":"B","text":"实时性"},{"key":"C","text":"通用性"},{"key":"D","text":"可固化性"}]' AS JSON), 'C',
       CAST('["嵌入式实时操作系统面向特定应用，非通用","可剪裁、实时、可固化是其特点","通用性不是其特点"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '嵌入式系统';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '基于网络的数据库系统（NDB）是基于 4G/5G 的移动通信之上，在逻辑上可以把嵌入式设备看作远程服务器的一个客户端。以下有关 NDB 的叙述中，不正确的是（ ）。', CAST('[{"key":"A","text":"NDB 主要由客户端、通信协议和远程服务器等三部分组成"},{"key":"B","text":"NDB 的客户端主要负责提供接口给嵌入式程序，通信协议负责规范客户端与远程服务器之间的通信，远程服务器负责维护服务器上的数据库数据"},{"key":"C","text":"NDB 具有客户端小、无需支持可剪裁性、代码可重用等特点"},{"key":"D","text":"NDB 是以文件方式存储数据库数据。即数据按照一定格式储存在磁盘中，使用时由应用程序通过相应的驱动程序甚至直接对数据文件进行读写"}]' AS JSON), 'C',
       CAST('["NDB 客户端应支持可剪裁性，以适应嵌入式环境","C 选项说无需支持可剪裁性，错误","ABD 描述正确"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库系统';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '采用三级模式结构的数据库系统中，如果对一个表创建聚簇索引，那么改变的是数据库的（ ）。', CAST('[{"key":"A","text":"外模式"},{"key":"B","text":"模式"},{"key":"C","text":"内模式"},{"key":"D","text":"用户模式"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库系统';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '人工智能技术已成为当前国际科技竞争的核心技术之一，AI 芯片是占据人工智能市场的法宝。AI 芯片有别于通常处理器芯片，它应具备四种关键特征。（ ）是 AI 芯片的关键特点。', CAST('[{"key":"A","text":"新型的计算范式、信号处理能力、低精度设计、专用开发工具"},{"key":"B","text":"新型的计算范式、训练和推断、大数据处理能力、可重构的能力"},{"key":"C","text":"训练和推断、大数据处理能力、可定制性，专用开发工具"},{"key":"D","text":"训练和推断、低精度设计、新型的计算范式、图像处理能力"}]' AS JSON), 'B',
       CAST('["AI 芯片关键特征包括新型计算范式、训练和推断、大数据处理能力、可重构能力","其他选项不全面或包含非关键特征"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '人工智能';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '以下关于以太网交换机转发表的叙述中，正确的是（ ）。', CAST('[{"key":"A","text":"交换机的初始 MAC 地址表为空"},{"key":"B","text":"交换机接收到数据帧后，如果没有相应的表项，则不转发该帧"},{"key":"C","text":"交换机通过读取输入帧中的目的地址添加相应的 MAC 地址表项"},{"key":"D","text":"交换机的 MAC 地址表项是静态增长的，重启时地址表清空"}]' AS JSON), 'A',
       CAST('["交换机初始 MAC 表为空，通过自学习建立","没有表项时通常洪泛转发，而非不转发","通过源地址学习添加表项","MAC 表是动态的，重启后清空"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '计算机网络';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'Internet 网络核心采取的交换方式为（ ）。', CAST('[{"key":"A","text":"分组交换"},{"key":"B","text":"电路交换"},{"key":"C","text":"虚电路交换"},{"key":"D","text":"消息交换"}]' AS JSON), 'A',
       CAST('["Internet 使用 IP 协议，基于分组交换","分组交换是网络核心方式"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '计算机网络';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'SDN（Software Defined Network）的网络架构中不包含（ ）。', CAST('[{"key":"A","text":"逻辑层"},{"key":"B","text":"控制层"},{"key":"C","text":"转发层"},{"key":"D","text":"应用层"}]' AS JSON), 'A',
       CAST('["SDN 架构包括应用层、控制层、转发层","逻辑层不是标准组成部分"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在Web服务器的测试中，反映其性能的指标不包括:', CAST('[{"key":"A","text":"链接正确跳转"},{"key":"B","text":"最大并发连接数"},{"key":"C","text":"响应延迟"},{"key":"D","text":"吞吐量"}]' AS JSON), 'A',
       CAST('["Web服务器性能指标包括最大并发连接数、响应延迟、吞吐量","链接正确跳转属于功能测试"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'Web服务器性能';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '常见的Web服务器性能评测方法有基准性能测试、压力测试和 (17)。', CAST('[{"key":"A","text":"功能测试"},{"key":"B","text":"黑盒测试"},{"key":"C","text":"白盒测试"},{"key":"D","text":"可靠性测试"}]' AS JSON), 'D',
       CAST('["常见的Web服务器性能评测方法包括基准性能测试、压力测试和可靠性测试"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'Web服务器性能';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '企业数字化转型的五个发展阶段依次是(18)', CAST('[{"key":"A","text":"初始级发展阶段、单元级发展阶段、流程级发展阶段、网络级发展阶段、生态级发展阶段"},{"key":"B","text":"初始级发展阶段、单元级发展阶段、系统级发展阶段、网络级发展阶段、生态级发展阶段"},{"key":"C","text":"初始级发展阶段、单元级发展阶段、流程级发展阶段、网络级发展阶段、优化级发展阶段"},{"key":"D","text":"初始级发展阶段、流程级发展阶段、系统级发展阶段、网络级发展阶段、生态级发展阶段"}]' AS JSON), 'A',
       CAST('["数字化转型五个阶段：初始级、单元级、流程级、网络级、生态级"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '企业数字化转型';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '从信息化建设的角度出发，以下说法错误的是(19)', CAST('[{"key":"A","text":"有效开发利用信息资源"},{"key":"B","text":"大力发展信息产业"},{"key":"C","text":"充分建设信息化政策法规和标准规范"},{"key":"D","text":"信息化的主体是程序员和项目经理"}]' AS JSON), 'D',
       CAST('["信息化主体是全体社会成员","信息化建设包括信息资源开发、信息产业发展、政策法规建设"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '信息化建设';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '组织信息化需求通常包含三个层次，即_ (20)', CAST('[{"key":"A","text":"战略需求，运作需求，功能需求"},{"key":"B","text":"战略需求，运作需求，技术需求"},{"key":"C","text":"市场需求，技术需求，用户需求"},{"key":"D","text":"市场需求，技术需求，领域需求"}]' AS JSON), 'B',
       CAST('["组织信息化需求三个层次：战略需求、运作需求、技术需求"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '组织信息化需求';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'MULTI', '根据《软件产品管理办法》，任何单位和个人不得开发、生产、销售、进出口含有以下哪些内容的软件产品？', CAST('[{"key":"A","text":"侵犯他人的知识产权"},{"key":"B","text":"含有计算机病毒"},{"key":"C","text":"可能危害计算机系统安全"},{"key":"D","text":"含有国家规定禁止传播的内容"},{"key":"E","text":"不符合我国软件标准规范"},{"key":"F","text":"未经国家正式批准"}]' AS JSON), 'ABCDE',
       CAST('["《软件产品管理办法》规定禁止含有侵犯知识产权、计算机病毒、危害系统安全、禁止传播内容、不符合标准规范等"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件产品管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '某软件企业在项目开发过程中目标明确，实施过程遵守既定的计划与流程，资源准备充分，权责到人，对整个流程进行严格的监测、控制与审查，符合企业管理体系与流程制度。因此，该企业达到了CMMI评估的（22）', CAST('[{"key":"A","text":"可重复级"},{"key":"B","text":"已定义级"},{"key":"C","text":"量化级"},{"key":"D","text":"优化级"}]' AS JSON), 'B',
       CAST('["CMMI已定义级要求有标准流程和制度","量化级需要量化管理，优化级强调持续改进"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'CMMI';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '产品配置是指一个产品在其生命周期各个阶段所产生的各种形式（机器可读或人工可读）和各种版本的(23)的集合', CAST('[{"key":"A","text":"需求规格说明、设计说明、测试报告"},{"key":"B","text":"需求规格说明、设计说明、计算机程序"},{"key":"C","text":"设计说明、用户手册、计算机程序"},{"key":"D","text":"文档、计算机程序、部件及数据"}]' AS JSON), 'D',
       CAST('["配置项包括文档、程序、部件和数据等"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '配置管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '（   ）的常见功能包括版本控制、变更管理、配置状态管理、访问控制和安全控制等。', CAST('[{"key":"A","text":"软件测试工具"},{"key":"B","text":"版本控制工具"},{"key":"C","text":"软件维护工具"},{"key":"D","text":"配置管理工具"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '配置管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '需求管理的主要活动包括(24)', CAST('[{"key":"A","text":"变更控制、版本控制、需求跟踪、需求状态跟踪"},{"key":"B","text":"需求获取、变更控制、版本控制、需求跟踪"},{"key":"C","text":"需求获取、需求建模、变更控制、版本控制"},{"key":"D","text":"需求获取、需求建模、需求评审、需求跟踪"}]' AS JSON), 'A',
       CAST('["需求管理活动包括变更控制、版本控制、需求跟踪、需求状态跟踪"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '需求管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '（）包括编制每个需求与系统元素之间的联系文档，这些元素包括其它需求、体系结构、设计部件、源代码模块、测试、帮助文件和文档等。', CAST('[{"key":"A","text":"需求描述"},{"key":"B","text":"需求分析"},{"key":"C","text":"需求获取"},{"key":"D","text":"需求跟踪"}]' AS JSON), 'D',
       CAST('["需求跟踪建立需求与系统元素之间的联系"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '需求跟踪';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '根据传统的软件生命周期方法学，可以把软件生命周期划分为（26）', CAST('[{"key":"A","text":"软件定义、软件开发、软件测试、软件维护"},{"key":"B","text":"软件定义、软件开发、软件运行、软件维护"},{"key":"C","text":"软件分析、软件设计、软件开发、软件维护"},{"key":"D","text":"需求获取、软件设计、软件开发、软件测试"}]' AS JSON), 'B',
       CAST('["传统软件生命周期分为定义、开发、运行、维护"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件生命周期';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述软件生命周期中各个阶段的主要任务。', CAST('[]' AS JSON), '软件生命周期包括软件定义、软件开发、软件运行和维护三个阶段。定义阶段确定要做什么，包括问题定义、可行性研究、需求分析；开发阶段具体实现，包括概要设计、详细设计、编码和测试；运行维护阶段修改完善软件。',
       CAST('["定义阶段：问题定义、可行性研究、需求分析","开发阶段：设计、编码、测试","运行维护阶段：修改完善"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件生命周期';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '以下关于敏捷方法的描述中，不属于敏捷方法核心思想的是（27）', CAST('[{"key":"A","text":"敏捷方法是适应型，而非可预测型"},{"key":"B","text":"敏捷方法以过程为本"},{"key":"C","text":"敏捷方法是以人为本，而非以过程为本"},{"key":"D","text":"敏捷方法是迭代增量式的开发过程"}]' AS JSON), 'B',
       CAST('["敏捷方法以人为本，适应变化，迭代增量"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '敏捷方法';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'RUP软件开发生命周期是一个二维的软件开发模型，其中，RUP的9个核心工作流中不包括（28）', CAST('[{"key":"A","text":"业务建模"},{"key":"B","text":"配置与变更管理"},{"key":"C","text":"成本"},{"key":"D","text":"环境"}]' AS JSON), 'C',
       CAST('["RUP九个核心工作流：业务建模、需求、分析与设计、实现、测试、部署、配置与变更管理、项目管理、环境"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'RUP';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在软件开发和维护过程中，一个软件会有多个版本，(29)工具用来存储、更新、恢复和管理一个软件的多个版本', CAST('[{"key":"A","text":"软件测试"},{"key":"B","text":"版本控制"},{"key":"C","text":"UML建模"},{"key":"D","text":"逆向工程"}]' AS JSON), 'B',
       CAST('["版本控制用于管理软件多个版本"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '版本控制';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '结构化设计是一种面向数据流的设计方法，以下不属于结构化设计工具的是（30）', CAST('[{"key":"A","text":"盒图"},{"key":"B","text":"HIPO图"},{"key":"C","text":"顺序图"},{"key":"D","text":"程序流程图"}]' AS JSON), 'C',
       CAST('["顺序图是UML中的交互图，不属于结构化设计工具"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '结构化设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件设计过程中，可以用耦合和内聚两个定性标准来衡量模块的独立程度，耦合衡量不同模块彼此间互相依赖的紧密程度，应采用以下设计原则(31)', CAST('[{"key":"A","text":"尽量使用内容耦合、少用控制耦合和特征耦合、限制公共环境耦合的范围、完全不用数据耦合"},{"key":"B","text":"尽量使用数据耦合、少用控制耦合和特征耦合、限制公共环境耦合的范围、完全不用内容耦合"},{"key":"C","text":"尽量使用控制耦合、少用数据耦合和特征耦合、限制公共环境耦合的范围、完全不用内容耦合"},{"key":"D","text":"尽量使用特征耦合、少用数据耦合和控制耦合、限制公共环境耦合的范围、完全不用内容耦合"}]' AS JSON), 'B',
       CAST('["高内聚低耦合，尽量使用数据耦合，避免内容耦合"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '耦合';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '内聚衡量一个模块内部各个元素彼此结合的紧密程度，以下属于高内聚的是(32)', CAST('[{"key":"A","text":"偶然内聚"},{"key":"B","text":"时间内聚"},{"key":"C","text":"功能内聚"},{"key":"D","text":"逻辑内聚"}]' AS JSON), 'C',
       CAST('["功能内聚是最高程度的内聚"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '内聚';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'UML是面向对象设计的建模工具，独立于任何具体程序设计语言，以下(33)不属于UML中的模型', CAST('[{"key":"A","text":"用例图"},{"key":"B","text":"协作图"},{"key":"C","text":"活动图"},{"key":"D","text":"PAD图"}]' AS JSON), 'D',
       CAST('["PAD图是程序流程图，不属于UML"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'UML';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '与UML1.x不同，为了更清楚地表达UML的结构，从UML2开始，整个UML规范被划分为基础结构和上层结构两个相对独立的部分，基础结构是UML的（   ），它定义了构造UML模型的各种基本元素；而上层结构则定义了面向建模用户的各种UML模型的语法、语义和表示。', CAST('[{"key":"A","text":"元元素"},{"key":"B","text":"模型"},{"key":"C","text":"元模型"},{"key":"D","text":"元元模型"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'UML';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '使用McCabe方法可以计算程序流程图的环形复杂度，下图的环形复杂度为(34)

[图片描述：一个程序流程图，有3个判定节点]', CAST('[{"key":"A","text":"3"},{"key":"B","text":"4"},{"key":"C","text":"5"},{"key":"D","text":"6"}]' AS JSON), 'B',
       CAST('["环形复杂度 = 判定节点数 + 1","图中判定节点数为3，所以复杂度为4"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'McCabe复杂度';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '面向构件的编程目前缺乏完善的方法学支持，构件交互的复杂性带来了很多问题，其中（36）问题会产生数据竞争和死锁现象', CAST('[{"key":"A","text":"多线程"},{"key":"B","text":"异步"},{"key":"C","text":"封装"},{"key":"D","text":"多语言支持"}]' AS JSON), 'A',
       CAST('["多线程并发可能导致数据竞争和死锁"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '构件交互';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '为实现对象重用，COM支持两种形式的对象组装。在(37)重用形式下，一个外部对象拥有指向一个内部对象的唯一引用，外部对象只是把请求转发给内部对象', CAST('[{"key":"A","text":"聚集"},{"key":"B","text":"包含"},{"key":"C","text":"链接"},{"key":"D","text":"多态"}]' AS JSON), 'B',
       CAST('["包含是外部对象拥有内部对象引用，转发请求"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'COM重用';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在(38)重用形式下，直接把内部对象的接口引用传给外部对象的客户，而不再转发请求。', CAST('[{"key":"A","text":"引用"},{"key":"B","text":"转发"},{"key":"C","text":"包含"},{"key":"D","text":"聚集"}]' AS JSON), 'D',
       CAST('["聚集是直接暴露内部对象接口给客户"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'COM重用';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '信息系统面临多种类型的网络安全威胁。其中，信息泄露是指信息被泄露或透露给某个非授权的实体； (39) 是指数据被非授权地进行增删、修改或破坏而受到损失', CAST('[{"key":"A","text":"非法使用"},{"key":"B","text":"破坏信息的完整性"},{"key":"C","text":"授权侵犯"},{"key":"D","text":"计算机病毒"}]' AS JSON), 'B',
       CAST('["破坏信息完整性指非授权修改数据"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络安全威胁';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '(40) 是指对信息或其他资源的合法访问被无条件地阻止', CAST('[{"key":"A","text":"拒绝服务"},{"key":"B","text":"陷阱门"},{"key":"C","text":"旁路控制"},{"key":"D","text":"业务欺骗"}]' AS JSON), 'A',
       CAST('["拒绝服务阻止合法访问"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络安全威胁';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '(41) 是指通过对系统进行长期监听，利用统计分析方法对诸如通信频度、通信的信息流向、通信总量的变化等参数进行研究，从而发现有价值的信息和规律。', CAST('[{"key":"A","text":"特洛伊木马"},{"key":"B","text":"业务欺骗"},{"key":"C","text":"物理侵入"},{"key":"D","text":"业务流分析"}]' AS JSON), 'D',
       CAST('["业务流分析通过统计通信参数获取信息"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络安全威胁';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '基于架构的软件设计(ABSD)方法是架构驱动的方法，该方法是一个 (44) 的方法，软件系统的架构通过该方法得到细化，直到能产生(45)', CAST('[{"key":"A","text":"自顶向下"},{"key":"B","text":"自底向上"},{"key":"C","text":"原型"},{"key":"D","text":"自顶向下和自底向上结合"}]' AS JSON), 'A',
       CAST('["ABSD是自顶向下、递归细化的方法"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'ABSD';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '基于架构的软件设计(ABSD)方法，软件系统的架构通过该方法得到细化，直到能产生(45)', CAST('[{"key":"A","text":"软件质量属性"},{"key":"B","text":"软件连接性"},{"key":"C","text":"软件构件或模块"},{"key":"D","text":"软件接口"}]' AS JSON), 'C',
       CAST('["ABSD细化架构直到产生软件构件和类"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'ABSD';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '4+1视图模型可以从多个视图或视角来描述软件架构。其中，用于捕捉设计的并发和同步特征的是哪个视图？', CAST('[{"key":"A","text":"逻辑视图"},{"key":"B","text":"开发视图"},{"key":"C","text":"过程视图"},{"key":"D","text":"物理视图"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '4+1视图模型可以从多个视图或视角来描述软件架构。其中，描述了在开发环境中软件的静态组织结构的是哪个视图？', CAST('[{"key":"A","text":"逻辑视图"},{"key":"B","text":"开发视图"},{"key":"C","text":"过程视图"},{"key":"D","text":"用例视图"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件架构风格是描述某一特定应用领域中系统组织方式的惯用模式，按照软件架构风格，物联网系统属于哪种软件架构风格？', CAST('[{"key":"A","text":"层次型"},{"key":"B","text":"事件系统"},{"key":"C","text":"数据线"},{"key":"D","text":"C2"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '某公司拟开发一个个人社保管理系统，该系统的主要功能需求是根据个人收入、家庭负担、身体状态等情况，预估计算个人每年应支付的社保金，该社保金的计算方式可能随着国家经济的变化而动态改变。针对上述需求描述，该软件系统适宜采用哪种架构风格设计？', CAST('[{"key":"A","text":"Layered system"},{"key":"B","text":"Data flow"},{"key":"C","text":"Event system"},{"key":"D","text":"Rule-based system"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '某公司拟开发一个个人社保管理系统，该系统的主要功能需求是根据个人收入、家庭负担、身体状态等情况，预估计算个人每年应支付的社保金，该社保金的计算方式可能随着国家经济的变化而动态改变。该风格的主要特点是？', CAST('[{"key":"A","text":"将业务逻辑中频繁变化的部分定义为规则"},{"key":"B","text":"各构件间相互独立"},{"key":"C","text":"支持并发"},{"key":"D","text":"无数据不工作"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述4+1视图模型中的四个主要视图及其作用。', CAST('[]' AS JSON), '4+1视图模型包括逻辑视图、过程视图、开发视图和物理视图。逻辑视图描述系统的功能需求，即系统提供给用户的服务；过程视图描述系统的并发和同步特性；开发视图描述系统在开发环境中的静态组织结构；物理视图描述系统硬件和软件之间的映射关系。',
       CAST('["逻辑视图描述功能需求","过程视图描述并发和同步","开发视图描述静态组织结构","物理视图描述硬件映射"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述层次型软件架构风格的特点，并举例说明其应用场景。', CAST('[]' AS JSON), '层次型架构将系统划分为若干层，每层为上层提供服务，并调用下层功能。特点包括：关注点分离、可替换性、易于扩展和维护。应用场景如OSI网络模型、物联网系统（感知层、网络层、应用层）等。',
       CAST('["分层结构，每层职责明确","上层依赖下层，下层独立","易于维护和扩展","典型应用如OSI模型、物联网"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '在折扣规则可修改性、个性化折扣定义灵活性和系统性能三个方面，比较解释器架构风格与面向对象架构风格的优劣，并说明选择解释器风格的理由。', CAST('[]' AS JSON), '解释器风格在折扣规则可修改性和个性化折扣定义灵活性方面优于面向对象风格，因为解释器将规则作为独立语法，易于修改和灵活执行；但在系统性能方面，面向对象风格优于解释器，因为解释器需要动态解释执行。因此，若系统强调规则灵活多变，应选择解释器风格。',
       CAST('["解释器在可修改性上优于面向对象","解释器在灵活性上优于面向对象","面向对象在性能上优于解释器","给出选择解释器的理由"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '特定领域软件架构(DSSA)是指特定应用领域中为一组应用提供组织结构参考的标准软件架构。从功能覆盖的范围角度，定义了一个特定的系统族，包含整个系统族内的多个系统，可作为该领域系统的可行解决方案的一个通用软件架构的是哪个域？', CAST('[{"key":"A","text":"垂直域"},{"key":"B","text":"水平域"},{"key":"C","text":"功能域"},{"key":"D","text":"属性域"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '特定领域软件架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '特定领域软件架构(DSSA)是指特定应用领域中为一组应用提供组织结构参考的标准软件架构。从功能覆盖的范围角度，定义了在多个系统和多个系统族中功能区域的共有部分，在子系统级上涵盖多个系统族的特定部分功能的是哪个域？', CAST('[{"key":"A","text":"垂直域"},{"key":"B","text":"水平域"},{"key":"C","text":"功能域"},{"key":"D","text":"属性域"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '特定领域软件架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '安全性是根据系统可能受到的安全威胁的类型来分类的。其中，保证信息不泄露给未授权的用户、实体或过程的是哪个？', CAST('[{"key":"A","text":"可控性"},{"key":"B","text":"机密性"},{"key":"C","text":"安全审计"},{"key":"D","text":"健壮性"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '安全性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '安全性是根据系统可能受到的安全威胁的类型来分类的。其中，保证信息的完整和准确，防止信息被篡改的是哪个？', CAST('[{"key":"A","text":"可控性"},{"key":"B","text":"完整性"},{"key":"C","text":"不可否认性"},{"key":"D","text":"安全审计"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '安全性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在架构评估中，场景是从哪个角度对与系统交互的描述？', CAST('[{"key":"A","text":"系统设计者"},{"key":"B","text":"系统开发者"},{"key":"C","text":"风险承担者"},{"key":"D","text":"系统测试者"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '架构评估';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在架构评估中，一般采用哪三方面来对场景进行描述？', CAST('[{"key":"A","text":"刺激源、制品、响应"},{"key":"B","text":"刺激、制品、响应"},{"key":"C","text":"刺激、环境、响应"},{"key":"D","text":"刺激、制品、环境"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '架构评估';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在架构评估中，一个或多个构件（和/或构件之间的关系）的特性，改变加密级别的设计决策属于哪种？因为它可能会对安全性和性能产生非常重要的影响。', CAST('[{"key":"A","text":"敏感点"},{"key":"B","text":"非风险点"},{"key":"C","text":"权衡点"},{"key":"D","text":"风险点"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '架构评估';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述架构评估中敏感点、权衡点和风险点的区别。', CAST('[]' AS JSON), '敏感点是一个或多个构件（或构件之间的关系）的特性，对某个质量属性有显著影响；权衡点是影响多个质量属性的敏感点，修改时需在多个质量属性间权衡；风险点是指可能引起负面影响（如成本、进度、性能）的架构决策。',
       CAST('["敏感点影响某个质量属性","权衡点影响多个质量属性，需权衡","风险点可能带来负面影响","三者关系：权衡点也是敏感点，风险点可能是敏感点"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '架构评估';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '在架构评估过程中，质量属性效用树是对系统质量属性进行识别和优先级排序的重要工具。请将合适的质量属性名称填入图1-1中(1)、(2)空白处，并从题干中的(a)~(l)中选择合适的质量属性描述，填入(3)~(6)空白处，完成该平台的效用树。', CAST('[]' AS JSON), '(1)性能 (2)可修改性 (3)e (4)j (5)h (6)i',
       CAST('["正确识别质量属性：性能、可修改性","正确匹配质量属性描述：e对应可用性，j对应性能，h对应安全性，i对应可修改性","理解效用树的结构和用途"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '架构评估';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '改变加密级别可能会对安全性和性能产生非常重要的影响，因此，在软件架构评估中，该设计决策是一个（  ）。', CAST('[{"key":"A","text":"敏感点"},{"key":"B","text":"风险点"},{"key":"C","text":"权衡点"},{"key":"D","text":"非风险点"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '架构评估';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在进行架构评估时，首先要明确具体的质量目标，并以之作为判定该架构优劣的标准。为得出这些目标而采用的机制叫做场景，场景是从（ ）的角度对与系统的交互的简短描述。', CAST('[{"key":"A","text":"用户"},{"key":"B","text":"系统架构师"},{"key":"C","text":"项目管理者"},{"key":"D","text":"风险承担者"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '架构评估';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '在架构评估过程中，质量属性效用树（utility tree）是对系统质量属性进行识别和优先级排序的重要工具。请将合适的质量属性名称填入图 1-1 中(1)、(2)空白处，并选择题干描述的(a)～(k)填入(3)～(6)空白处，完成该系统的效用树。', CAST('[]' AS JSON), '（1）性能；（2）可修改性；（3）c；（4）e；（5）a；（6）k（注：答案可能不唯一，需结合效用树结构）',
       CAST('["识别质量属性：性能、可修改性、可用性、安全性等","将需求描述归类到对应质量属性","根据效用树层次填充"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '架构评估';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '非负变量x和y，在x≤4，y≤3和x+2y≤8的约束条件下，目标函数2x+3y的最大值为？', CAST('[{"key":"A","text":"13"},{"key":"B","text":"14"},{"key":"C","text":"15"},{"key":"D","text":"16"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数学规划';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '某项目包括A~G七个作业，各作业之间的衔接关系和所需时间如下表：其中，作业C所需的时间，乐观估计为5天，最可能为14天，保守估计为17天。假设其他作业都按计划进度实施，为使该项目按进度计划如期全部完成，作业C（ ）。', CAST('[{"key":"A","text":"必须在期望时间内完成"},{"key":"B","text":"必须在14天内完成"},{"key":"C","text":"比期望时间最多可拖延1天"},{"key":"D","text":"比期望时间最多可拖延2天"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '项目管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述三点估算法（PERT）的公式，并说明如何利用总时差判断作业的灵活性。', CAST('[]' AS JSON), '三点估算法公式：期望时间 = (最乐观 + 4×最可能 + 最悲观) / 6。总时差是指在不影响项目总工期的前提下，作业可以延迟的时间。若作业的总时差大于0，则作业有一定灵活性；若总时差为0，则为关键作业，必须按时完成。',
       CAST('["公式：期望时间 = (乐观 + 4×可能 + 悲观)/6","总时差定义","总时差>0有灵活性","总时差=0为关键作业"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '项目管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '某项目包括 A、B、C、D 四道工序，各道工序之间的衔接关系。正常进度下各工序所需的时间和直接费用、赶工进度下所需的时间和直接费用如下表所示。该项目每天需要的间接费用为4.5 万元。根据此表，以最低成本完成该项目需要（ ）天。', CAST('[{"key":"A","text":"7"},{"key":"B","text":"9"},{"key":"C","text":"10"},{"key":"D","text":"5"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '项目管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'The prevailing distributed computing model of the current era is called client-server computing. A (71) is a solution in which the presentation, presentation logic, application logic, data manipulation and data layers are distributed between client PCs and one or more servers.', CAST('[{"key":"A","text":"Client/Server system"},{"key":"B","text":"Client-side"},{"key":"C","text":"Server-side"},{"key":"D","text":"Database"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '分布式计算';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'A (72) is a personal computer that does not have to be very powerful in terms of processor speed and memory because it only presents the interface to the user.', CAST('[{"key":"A","text":"Server-side"},{"key":"B","text":"Browser"},{"key":"C","text":"Fat client"},{"key":"D","text":"Thin client"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '分布式计算';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'A (73) is a personal computer, notebook computer, or workstation that is typically more powerful in terms of processor speed, memory, and storage capacity.', CAST('[{"key":"A","text":"Cloud platform"},{"key":"B","text":"Cluster system"},{"key":"C","text":"Fat client"},{"key":"D","text":"Thin client"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '分布式计算';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'A (74) hosts one or more shared databases but also executes all database commands and services for information systems.', CAST('[{"key":"A","text":"Transaction server"},{"key":"B","text":"Database server"},{"key":"C","text":"Application server"},{"key":"D","text":"Message server"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '分布式计算';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'A(n) (75) hosts Internet or intranet Web sites, it communicates with clients by returning to them documents and data.', CAST('[{"key":"A","text":"Database server"},{"key":"B","text":"Message server"},{"key":"C","text":"Web server"},{"key":"D","text":"Application server"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '分布式计算';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '若采用面向对象方法对预约挂号管理系统进行分析，得到如图2-1所示的用例图。请将合适的参与者名称填入图2-1中的(1)和(2)处，使用题干给出的功能描述(a)~(j)，完善用例(3)~(12)的名称。', CAST('[]' AS JSON), '(1)系统管理员 (2)患者 (3)a (4)c (5)f (6)h (7)i (8)j (9)b (10)d (11)e (12)g',
       CAST('["识别参与者：系统管理员和患者","正确关联功能描述到用例","理解用例图的基本元素"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '面向对象用例建模';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '采用面向对象方法对预约挂号过程进行分析，得到如图2-2所示的顺序图，使用题干中给出的描述，完善图2-2中对象(1)，及消息(2)~(4)的名称。请简要说明在描述对象之间的动态交互关系时，协作图与顺序图存在哪些区别。', CAST('[]' AS JSON), '(1)预约人员 (2)发起预约挂号请求 (3)显示医生出诊时段 (4)显示是否预约成功。顺序图强调消息的时间顺序，协作图强调对象间的组织结构。',
       CAST('["正确填写对象和消息名称","顺序图强调时间顺序","协作图强调对象组织","两者都是交互图，但侧重点不同"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '顺序图与协作图';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '采用面向对象方法开发软件，通常需要建立对象模型、动态模型和功能模型，请分别介绍这3种模型，并详细说明它们之间的关联关系，针对上述模型，说明哪些模型可用于软件的需求分析？', CAST('[]' AS JSON), '对象模型描述系统的静态结构，用对象图表示；动态模型描述系统的交互和行为，用状态图表示；功能模型描述系统的数据变换，用DFD表示。对象模型是基础，动态模型和功能模型都依赖于对象模型。对象模型和动态模型可用于需求分析。',
       CAST('["对象模型：静态结构，对象图","动态模型：行为，状态图","功能模型：数据变换，DFD","关联关系：对象模型是基础","需求分析：对象模型和动态模型"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '对象模型、动态模型和功能模型';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请用300字以内的文字说明数据定义、数据分布与数据管理的具体内涵。', CAST('[]' AS JSON), '数据定义：明确数据的类型、格式、取值范围等，确保数据的一致性和准确性。数据分布：确定数据在系统中的存储位置和分布方式，以满足性能、可用性和安全性的要求。数据管理：包括数据的存储、备份、恢复、访问控制等，确保数据的安全可靠。',
       CAST('["数据定义：数据类型、格式、取值范围","数据分布：存储位置、分布方式","数据管理：存储、备份、恢复、访问控制"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请用200字以内的文字说明常见的反规范化设计方法，并说明用户查询商品信息应该采用哪种反规范化设计方法。', CAST('[]' AS JSON), '常见的反规范化设计方法包括：增加冗余列、增加派生列、表合并、表分割。用户查询商品信息需要同时显示药品信息、供应商信息和库存信息，可以采用增加冗余列的方法，将供应商名称和当前库存数量冗余到药品表中。',
       CAST('["反规范化方法：增加冗余列、增加派生列、表合并、表分割","用户查询商品信息适合增加冗余列","冗余列减少连接操作"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '反规范化设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请用200字以内的文字说明在反规范化设计中，解决数据不一致性问题的三种常见方法，并说明该系统应该采用哪种方法。', CAST('[]' AS JSON), '解决数据不一致性的常见方法：事务保证、触发器、批处理同步。该系统应该采用事务保证，因为反规范化后数据冗余，需要保证数据的一致性。',
       CAST('["方法：事务保证、触发器、批处理同步","系统采用事务保证","事务保证实时性强"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据一致性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '该系统采用了Redis来实现某些特定功能（如当前热销药品排名等），同时将药品关系数据放到内存以提高商品查询的性能，但必然会造成Redis和MySQL的数据实时同步问题。（1）Redis的数据类型包括String、Hash、List、Set和ZSet等，请说明实现当前热销药品排名的功能应该选择使用哪种数据类型。（2）请用200字以内的文字解释说明解决Redis和MySQL数据实时同步问题的常见方案。', CAST('[]' AS JSON), '(1)应该选择ZSet（有序集合）数据类型，因为ZSet可以按分数排序，适合排名场景。(2)常见方案：双写、消息队列、binlog订阅。',
       CAST('["ZSet适合排名","同步方案：双写、消息队列、binlog订阅","双写简单但一致性差","消息队列解耦","binlog订阅实时性好"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'Redis应用';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述数据库表分割的几种方法及其适用场景。', CAST('[]' AS JSON), '数据库表分割的方法包括：增加冗余列、增加派生列、重新组表、水平分割表、垂直分割表。增加冗余列适用于减少连接查询；增加派生列适用于减少计算和集合函数的使用；重新组表适用于频繁连接的两个表；水平分割表适用于表数据规模大、数据相对独立或需要存放到多个介质上时；垂直分割表适用于减少I/O次数。',
       CAST('["答出至少四种方法","每种方法说明适用场景","水平分割和垂直分割的区别"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库性能优化';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '在数据库设计中，维护复制列或派生列的数据一致性有哪些方法？请比较其优缺点。', CAST('[]' AS JSON), '维护复制列或派生列的数据一致性的方法有：批处理维护、应用逻辑和触发器。批处理维护是积累一定时间后批量修改，适用于实时性要求不高的场景；应用逻辑要求在同一个事务中对所有涉及的表进行增删改操作，风险较大，因为逻辑分散在多个应用中，不易维护；触发器是实时的，处理逻辑集中在一个地方，易于维护，是较好的方法。',
       CAST('["答出三种方法","说明批处理维护的特点","说明应用逻辑的缺点","说明触发器的优点"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库性能优化';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请列举几种数据库与缓存数据同步的方案，并简要说明其原理。', CAST('[]' AS JSON), '数据库与缓存同步的方案有：实时同步方案（先更新数据库，再使缓存过期）、异步队列同步（通过消息中间件如Kafka）、使用canal工具（模拟MySQL主从同步，监控binlog日志）、UDF自定义函数（利用触发器）。',
       CAST('["答出至少三种方案","说明实时同步方案的基本步骤","说明canal的原理","说明异步队列的适用场景"]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '缓存同步';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请比较基于家庭网关的传统智能家居管理系统和基于云平台的智能家居管理系统在网关管理、数据处理和系统性能方面的特点。', CAST('[]' AS JSON), '基于家庭网关的传统智能家居管理系统：网关管理分散，数据处理在本地，性能受网关限制。基于云平台的智能家居管理系统：网关管理集中，可远程高效管理；数据处理在云端，支持备份恢复，提高容灾性；性能方面，数据存储在云端，减少请求时间，提高通信效率。',
       CAST('["分别说明网关管理特点","分别说明数据处理特点","分别说明系统性能特点","突出云平台的优势"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '系统架构设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请从数据传输可靠性的角度对比分析TCP和UDP通信协议的不同，并说明在智能家居系统中应采用哪种协议。', CAST('[]' AS JSON), 'TCP提供可靠的、面向连接的、全双工的数据传输服务，通过重发技术保证数据可靠性，适用于数据量少、可靠性要求高的场合。UDP是不可靠的、无连接的协议，错误检测功能弱，但传输效率高。智能家居系统需要可靠通信，应采用TCP协议。',
       CAST('["说明TCP的特点","说明UDP的特点","对比两者可靠性","给出选择结论"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络协议';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '以下关于HTTPS和HTTP协议的描述中，不正确的是（   ）。', CAST('[{"key":"A","text":"HTTPS协议使用加密传输"},{"key":"B","text":"HTTPS协议默认服务端口号是443"},{"key":"C","text":"HTTP协议默认服务端口是80"},{"key":"D","text":"电子支付类网站应使用HTTP协议"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络协议';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '电子邮件客户端通过发起对（   ）服务器的（   ）端口的TCP连接来进行邮件发送。', CAST('[{"key":"A","text":"POP3"},{"key":"B","text":"SMTP"},{"key":"C","text":"HTTP"},{"key":"D","text":"IMAP"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络协议';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '电子邮件客户端通过发起对（   ）服务器的（   ）端口的TCP连接来进行邮件发送。', CAST('[{"key":"A","text":"23"},{"key":"B","text":"25"},{"key":"C","text":"110"},{"key":"D","text":"143"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络协议';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述HTTPS和HTTP协议的主要区别。', CAST('[]' AS JSON), 'HTTPS在HTTP基础上增加了SSL/TLS加密层，提供加密传输、身份认证和完整性校验，默认端口443；HTTP明文传输，默认端口80。HTTPS更安全，但性能略低。',
       CAST('["HTTPS有加密层","HTTPS默认端口443","HTTP默认端口80","HTTPS更安全"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络协议';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述面向方面编程（AOP）的三个开发步骤。', CAST('[]' AS JSON), 'AOP的三个开发步骤：方面分解、关注点实现和方面的重新组合。方面分解是提取横切关注点和核心关注点；关注点实现是分别实现这些关注点，核心关注点用OOP，横切关注点用AOP；方面的重新组合是通过方面集成器制定重组规则，即编织。',
       CAST('["答出三个步骤","解释方面分解","解释关注点实现","解释方面的重新组合"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '面向方面编程';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述鉴别服务的阶段有哪些。', CAST('[]' AS JSON), '鉴别服务的阶段包括：安装阶段、修改鉴别信息阶段、分发阶段、获取阶段、传送阶段、验证阶段、停活阶段、重新激活阶段、取消安装阶段。',
       CAST('["列出至少五个阶段","按顺序列出","简要说明每个阶段的作用"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '系统安全架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请说明访问控制中的基本组件及其作用。', CAST('[]' AS JSON), '访问控制的基本组件包括：发起者、AEF（访问控制实施功能）、ADF（访问控制判决功能）、目标。发起者发起访问请求；AEF确保只有允许的访问才执行；ADF根据访问控制策略和ADI做出判决；目标是访问的对象。',
       CAST('["答出四个组件","解释每个组件的作用","说明它们之间的关系"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '系统安全架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请围绕“企业集成平台的理解与应用”论题，依次从以下三个方面进行论述：1.概要叙述你参与管理和开发的、采用企业集成平台进行企业信息集成的软件项目以及你在其中所承担的主要工作。2.请给出至少4种企业集成平台应具有的基本功能，并对这4种功能的内涵进行简要阐述。3.具体阐述你参与管理和开发的项目是如何使用企业集成平台进行企业信息集成的，并围绕上述4种功能，详细论述在集成过程中遇到了哪些实际问题，是如何解决的。', CAST('[]' AS JSON), '企业集成平台是支持企业信息集成的支撑环境，其基本功能包括：通信服务、信息集成服务、应用集成服务、二次开发工具、平台运行管理工具。在项目中，通过通信服务实现分布式环境下的透明通信；通过信息集成服务实现异种数据库间的数据交换与互操作；通过应用集成服务将现有系统通过适配器连接；通过二次开发工具开发特定适配器；通过平台运行管理工具进行系统配置与维护。实际遇到的问题包括数据格式不一致、系统接口不兼容、性能瓶颈等，通过引入标准数据模型、开发适配器、优化通信机制等方式解决。',
       CAST('["明确企业集成平台的定义与作用","至少列出4种基本功能并阐述内涵","结合项目实际说明如何使用平台进行集成","针对每种功能描述遇到的问题及解决方案","论述具有逻辑性和条理性"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '企业集成平台';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述企业集成平台的基本功能，并说明每种功能的作用。', CAST('[]' AS JSON), '企业集成平台的基本功能包括：
1. 通信服务：提供分布环境下透明的同步/异步通信服务，使用户和应用程序无需关心具体操作系统和网络位置，以透明的函数调用或对象服务方式完成通信。
2. 信息集成服务：为应用提供透明的信息访问服务，实现异种数据库系统之间数据的交换、互操作、分布数据管理和共享信息模型定义，使应用能以一致的语义和接口访问数据。
3. 应用集成服务：通过高层应用编程接口（API）访问相应应用程序，这些API包含在适配器或代理中，用于连接不同应用程序，使用户无需修改原有系统即可将现有系统互联。
4. 二次开发工具：提供一组帮助用户开发特定应用程序（如适配器或应用封装服务）的支持工具，简化开发工作。
5. 平台运行管理工具：负责集成平台的运行管理和控制，包括静态和动态配置、应用运行管理、事件管理和出错管理等，维护系统稳定运行。',
       CAST('["至少列出4种功能","每种功能有简要阐述","功能描述准确"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '企业集成平台';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请围绕“微服务架构及其应用”论题，依次从以下三个方面进行论述：1.概要叙述你参与管理和开发的、采用微服务架构的软件项目以及你在其中所承担的主要工作。2.请简要描述微服务架构的优点。3.具体阐述你参与管理和开发的项目是如何基于微服务架构进行软件设计实现的。', CAST('[]' AS JSON), '微服务架构将一个复杂的应用拆分成多个独立自治的服务，服务间通过轻量级协议交互。其优点包括：通过分而治之实现持续交付和部署大型复杂应用；提高模块化，易于理解、开发和测试；降低复杂性；允许独立更新功能；高度可扩展；减少破坏系统无关部分的机会；独立交付和部署服务；支持多环境部署；持续融入新技术；改善团队协作；提高新成员生产力。在设计实现中，遵循围绕业务概念建模、自动化、隐藏内部实现细节、去中心化、独立部署、隔离失败、高度可观察等原则，采用RESTful API、API网关、服务注册、事件总线、安全保护等组件。',
       CAST('["描述微服务架构的基本概念","至少列出3个优点并解释","说明设计原则","描述具体实现组件（如API网关、服务注册等）","结合项目实际说明应用情况"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '微服务架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述微服务架构的优点。', CAST('[]' AS JSON), '微服务架构的优点包括：
1. 通过分而治之的原则，持续交付和部署大型、复杂的应用程序。
2. 通过更易于理解、开发和测试系统来提高模块化。
3. 通过每个微服务具有较小的代码库来降低复杂性。
4. 允许更新功能，而对系统的其余部分没有影响或影响极小。
5. 使架构变得高度可扩展。
6. 大大减少了破坏系统无关部分的机会。
7. 可以独立交付和部署服务，而不必等待整个系统发布。
8. 允许部署到多个云和本地基础设施环境。
9. 在持续发展现有系统的同时持续融入和利用最新的技术。
10. 使同一时间在同一系统上工作的一组开发人员间的协作更可控。
11. 允许新的团队成员更快地提高生产力，他们可以开发新功能而不必学习整个系统。',
       CAST('["至少列出3个优点","每个优点有简要解释","优点描述准确"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '微服务架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '云计算服务体系结构如下图所示，图中①、②、③分别与SaaS、PaaS、IaaS相对应，图中①、②、③应为（ ）。', CAST('[{"key":"A","text":"应用层、基础设施层、平台层"},{"key":"B","text":"应用层、平台层、基础设施层"},{"key":"C","text":"平台层、应用层、基础设施层"},{"key":"D","text":"平台层、基础设施层、应用层"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '云计算基础';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '前趋图（Precedence Graph）是一个有向无环图，记为：→={(Pi,Pj)}|Pi must complete before Pj may start}，假设系统中进程P={P1，P2，P3，P4，P5，P6，P7，P8}，且进程的前趋图如下图所示。那么，该前驱图可记为（ ）。', CAST('[{"key":"A","text":"→={P1，P2），（P1，P3），（P1，P4），（P2，P5），（P3，P5），（P4，P7），（P5，P6），（P5，P7），（P7，P6），（P4，P5），（P6，P7），（P7，P8）}"},{"key":"B","text":"→={P1，P2），（P1，P3），（P1，P4），（P2，P3），（P2，P5），（P3，P4），（P3，P6），（P4，P7），（P5，P6），（P5，P8），（P6，P7），（P7，P8）}"},{"key":"C","text":"→={P1，P2），（P1，P3），（P1，P4），（P2，P3），（P2，P5），（P3，P4），（P3，P5），（P4，P6），（P5，P7），（P5，P8），（P6，P7），（P7，P8）}"},{"key":"D","text":"→={P1，P2），（P1，P3），（P2，P3），（P2，P5），（P3，P4），（P3，P6），（P4，P7），（P5，P6），（P5，P8），（P6，P7），（P6，P8），（P7，P8）}"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '进程管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在磁盘调度管理中，应先进行移臂调度，再进行旋转调度。假设磁盘移动臂位于20号柱面上，进程的请求序列如下表所示。如果采用最短移臂调度算法，那么系统的响应序列应为（ ）。', CAST('[{"key":"A","text":"②⑧③④⑤①⑦⑥⑨"},{"key":"B","text":"②③⑧④⑥⑨①⑤⑦"},{"key":"C","text":"④⑥⑨⑤⑦①②⑧③"},{"key":"D","text":"④⑥⑨⑤⑦①②③⑧"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '磁盘调度';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '假设系统中有正在运行的事务，若要转储全部数据库，则应采用（ ）方式。', CAST('[{"key":"A","text":"静态全局转储"},{"key":"B","text":"动态增量转储"},{"key":"C","text":"静态增量转储"},{"key":"D","text":"动态全局转储"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库备份';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '给定关系模式R（U，F），其中U为属性集，F是U上的一组函数依赖，那么函数依赖的公理系统（Armstrong公理系统）中的分解规则是指（ ）为F所蕴涵。', CAST('[{"key":"A","text":"若X→Y，Y→Z，则X→Y"},{"key":"B","text":"若Y⊆X⊆U，则X→Y"},{"key":"C","text":"若X→Y，Z⊆Y，则X→Z"},{"key":"D","text":"若X→Y，Y→Z，则X→YZ"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '关系数据库理论';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '关系R和S如下表所示，则关系R与S的自然连接运算结果中的元组个数为（ ）。', CAST('[{"key":"A","text":"1"},{"key":"B","text":"2"},{"key":"C","text":"3"},{"key":"D","text":"4"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '关系代数';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '以下关于鸿蒙操作系统的叙述中，不正确的是（ ）。', CAST('[{"key":"A","text":"鸿蒙操作系统整体架构采用分层的层次化设计，从下向上依次为：内核层、系统服务层、框架层和应用层"},{"key":"B","text":"鸿蒙操作系统内核层采用宏内核设计，拥有更强的安全特性和低时延特点"},{"key":"C","text":"鸿蒙操作系统架构采用了分布式设计理念，实现了分布式软总线、分布式设备虚拟化、分布式数据管理和分布式任务调度等四种分布式能力"},{"key":"D","text":"架构的系统安全性主要体现在搭载HarmonyOS的分布式终端上，可以保证“正确的人，通过正确的设备，正确地使用数据”"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '操作系统';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'GPU目前已广泛应用于各行各业，GPU中集成了同时运行在GHz的频率上的成千上万个core，可以高速处理图像数据。最新的GPU峰值性能可高达（   ）以上。', CAST('[{"key":"A","text":"100 TFlops"},{"key":"B","text":"50 TFlops"},{"key":"C","text":"10 TFlops"},{"key":"D","text":"1TFlops"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'GPU与AI芯片';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'AI芯片是当前人工智能技术发展的核心技术，其能力要支持训练和推理，通常，AI芯片的技术架构包括（   ）等三种。', CAST('[{"key":"A","text":"GPU、FPGA、ASIC"},{"key":"B","text":"CPU、FPGA、DSP"},{"key":"C","text":"GPU、CPU、ASIC"},{"key":"D","text":"GPU、FPGA、SOC"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'GPU与AI芯片';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述GPU、FPGA和ASIC三种AI芯片技术架构的特点。', CAST('[]' AS JSON), 'GPU：通用性强，适合并行计算，但功耗和价格较高；FPGA：可编程，半定制化，功耗较低，灵活性好；ASIC：专用集成电路，针对特定算法设计，功耗低、能效比高，但灵活性差。',
       CAST('["GPU特点：通用、并行计算强、功耗高","FPGA特点：可编程、半定制、功耗较低","ASIC特点：专用、低功耗、高能效比","对比说明"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'GPU与AI芯片';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '系统（   ）是指在规定的时间内和规定条件下能有效地实现规定功能的能力。它不仅取决于规定的使用条件等因素，还与设计技术有关。常用的度量指标主要有故障率（或失效率）、平均失效等待时间、平均失效间隔时间和可靠度等。', CAST('[{"key":"A","text":"可靠性"},{"key":"B","text":"可用性"},{"key":"C","text":"可理解性"},{"key":"D","text":"可测试性"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '系统可靠性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '其中，（   ）是系统在规定工作时间内无故障的概率。', CAST('[{"key":"A","text":"失效率"},{"key":"B","text":"平均失效等待时间"},{"key":"C","text":"平均失效间隔时间"},{"key":"D","text":"可靠度"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '系统可靠性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '数据资产的特征包括（   ）。
①可增值②可测试③可共享④可维护⑤可控制⑥可量化', CAST('[{"key":"A","text":"①②③④"},{"key":"B","text":"①②③⑤"},{"key":"C","text":"①②④⑤"},{"key":"D","text":"①③⑤⑥"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据资产';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '数据管理能力成熟度评估模型(DCMM)是我国首个数据管理领域的国家标准，DCMM提出了符合我国企业的数据管理框架，该框架将组织数据管理能力划分为8个能力域，分别为：数据战略、数据治理、数据架构、数据标准、数据质量、数据安全、（   ）。', CAST('[{"key":"A","text":"数据应用和数据生存周期"},{"key":"B","text":"数据应用和数据测试"},{"key":"C","text":"数据维护和数据生存周期"},{"key":"D","text":"数据维护和数据测试"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述DCMM数据管理能力成熟度模型中的8个能力域。', CAST('[]' AS JSON), 'DCMM的8个能力域包括：数据战略、数据治理、数据架构、数据应用、数据安全、数据质量、数据标准、数据生存周期。',
       CAST('["数据战略","数据治理","数据架构","数据应用","数据安全","数据质量","数据标准","数据生存周期"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据管理';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '完整的信息安全系统至少包含三类措施，即技术方面的安全措施、管理方面的安全措施和相应的（   ）。', CAST('[{"key":"A","text":"用户需求"},{"key":"B","text":"政策法律"},{"key":"C","text":"市场需求"},{"key":"D","text":"领域需求"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '信息安全';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '其中，信息安全的技术措施主要有：信息加密、数字签名、身份鉴别、访问控制、网络控制技术、反病毒技术、（   ）。', CAST('[{"key":"A","text":"数据备份和数据测试"},{"key":"B","text":"数据迁移和数据备份"},{"key":"C","text":"数据备份和灾难恢复"},{"key":"D","text":"数据迁移和数据测试"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '信息安全';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '与瀑布模型相比，（   ）降低了实现需求变更的成本，更容易得到客户对于已完成开发工作的反馈意见，并且客户可以更早地使用软件并从中获得价值。', CAST('[{"key":"A","text":"快速原型模型"},{"key":"B","text":"敏捷开发"},{"key":"C","text":"增量式开发"},{"key":"D","text":"智能模型"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件开发模型';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'CMMI是软件企业进行多方面能力评价的、集成的成熟度模型，软件企业在实施过程中，为了达到本地化，应组织体系编写组，建立基于CMMI的软件质量管理体系文件，体系文件的层次结构一般分为四层，包括：
①顶层方针 ②模板类文件 ③过程文件 ④规程文件
按照自顶向下的塔型排列，以下顺序正确的是（   ）。', CAST('[{"key":"A","text":"①④③②"},{"key":"B","text":"①④②③"},{"key":"C","text":"①②③④"},{"key":"D","text":"①③④②"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件过程改进';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '信息建模方法是从数据的角度对现实世界建立模型，模型是现实系统的一个抽象，信息建模方法的基本工具是（   ）。', CAST('[{"key":"A","text":"流程图"},{"key":"B","text":"实体联系图"},{"key":"C","text":"数据流图"},{"key":"D","text":"数据字典"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '信息建模';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '使用模型驱动的软件开发方法，软件系统被表示为一组可以被自动转换为可执行代码的模型。其中，（   ）在不涉及实现的情况下对软件系统进行建模。', CAST('[{"key":"A","text":"平台无关模型"},{"key":"B","text":"计算无关模型"},{"key":"C","text":"平台相关模型"},{"key":"D","text":"实现相关模型"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '模型驱动开发';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '工作流表示的是业务过程模型，通常使用图形形式来描述，以下不可用来描述工作流的是(   )。', CAST('[{"key":"A","text":"活动图"},{"key":"B","text":"BPMN"},{"key":"C","text":"用例图"},{"key":"D","text":"Petri-Net"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '工作流';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '领域驱动设计提出围绕（   ）进行软件设计和开发，该模型是由开发人员与领域专家协作构建出的一个反映深层次领域知识的模型。', CAST('[{"key":"A","text":"行为模型"},{"key":"B","text":"领域模型"},{"key":"C","text":"专家模型"},{"key":"D","text":"知识库模型"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '领域驱动设计';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在UML2.0中，顺序图用来描述对象之间的消息交互，其中循环、选择等复杂交互使用（  ）表示。', CAST('[{"key":"A","text":"嵌套"},{"key":"B","text":"泳道"},{"key":"C","text":"组合"},{"key":"D","text":"序列片段"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'UML顺序图';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在UML2.0中，顺序图的对象之间的消息类型包括（  ）。', CAST('[{"key":"A","text":"同步消息、异步消息、返回消息、动态消息、静态消息"},{"key":"B","text":"同步消息、异步消息、动态消息、参与者创建消息、参与者销毁消息"},{"key":"C","text":"同步消息、异步消息、静态消息、参与者创建消息、参与者销毁消息"},{"key":"D","text":"同步消息、异步消息、返回消息、参与者创建消息、参与者销毁消息"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'UML顺序图';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述UML顺序图的主要组成部分及其作用。', CAST('[]' AS JSON), 'UML顺序图（序列图）主要组成部分包括：生命线（Lifeline）、激活（Activation）、消息（Message）和序列片段（Combined Fragment）。生命线表示参与交互的对象，激活表示对象执行操作的时间段，消息表示对象之间的通信，序列片段用于表示循环、选择等复杂交互。',
       CAST('["生命线表示对象","激活表示操作执行时间段","消息表示通信","序列片段表示复杂交互"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'UML顺序图';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '以下有关构件特性的描述中，说法不正确的是（  ）。', CAST('[{"key":"A","text":"构件是独立部署单元"},{"key":"B","text":"构件可作为第三方的组装单元"},{"key":"C","text":"构件没有外部的可见状态"},{"key":"D","text":"构件作为部署单元，是可拆分的"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '构件特性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在构件的定义中，（  ）是一个已命名的一组操作的集合。', CAST('[{"key":"A","text":"接口"},{"key":"B","text":"对象"},{"key":"C","text":"函数"},{"key":"D","text":"模块"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '构件定义';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在服务端构件模型的典型解决方案中，（  ）较为适用于应用服务器。', CAST('[{"key":"A","text":"EJB和COM+模型"},{"key":"B","text":"EJB和servlet模型"},{"key":"C","text":"COM+和ASP模型"},{"key":"D","text":"COM+和servlet模型"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '服务端构件模型';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '以下有关构件演化的叙述中，说法不正确的是（  ）。', CAST('[{"key":"A","text":"安装新版本构件可能会与现有系统发生冲突"},{"key":"B","text":"构件通常也会经历一般软件产品具有的演化过程"},{"key":"C","text":"解决“遗留系统移植”问题还需要通过使用包裹器构件来适配旧版软件"},{"key":"D","text":"为安装新版本的构件，必须终止系统中所有现有版本构件的运行"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '构件演化';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件复杂性度量中，（  ）可以反映源代码结构的复杂度。', CAST('[{"key":"A","text":"模块数"},{"key":"B","text":"环路数"},{"key":"C","text":"用户数"},{"key":"D","text":"对象数"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件复杂性度量';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在白盒测试中，测试强度最高的是（  ）。', CAST('[{"key":"A","text":"语句覆盖"},{"key":"B","text":"分支覆盖"},{"key":"C","text":"判定覆盖"},{"key":"D","text":"路径覆盖"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '白盒测试';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在黑盒测试方法中，（  ）方法最适合描述在多个逻辑条件取值组合所构成的负载情况下，分别要执行哪些不同的动作。', CAST('[{"key":"A","text":"等价类"},{"key":"B","text":"边界值"},{"key":"C","text":"判定表"},{"key":"D","text":"因果图"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '黑盒测试';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '（  ）的目的是测试软件变更之后，变更部分的正确性和对变更需求的符合性，以及软件原有的、正确的功能、性能和其它规定的要求的不损害性。', CAST('[{"key":"A","text":"验收测试"},{"key":"B","text":"Alpha测试"},{"key":"C","text":"Beta测试"},{"key":"D","text":"回归测试"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件测试类型';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在对遗留系统进行评估时，对于技术含量较高、业务价值较低且仅能完成某个部门的业务管理的遗留系统，一般采用的遗留系统演化策略是（  ）策略。', CAST('[{"key":"A","text":"淘汰"},{"key":"B","text":"继承"},{"key":"C","text":"集成"},{"key":"D","text":"改造"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '遗留系统演化策略';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在软件体系结构的建模与描述中，多视图是一种描述软件体系结构的重要途径，其体现了（  ）的思想。', CAST('[{"key":"A","text":"关注点分离"},{"key":"B","text":"面向对象"},{"key":"C","text":"模型驱动"},{"key":"D","text":"UML"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件体系结构多视图';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在4+1模型中，“1”指的是（  ）。', CAST('[{"key":"A","text":"统一场景"},{"key":"B","text":"开发视图"},{"key":"C","text":"逻辑视图"},{"key":"D","text":"物理视图"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件体系结构多视图';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '基于体系结构的软件设计（ABSD）方法是体系结构驱动，即指构成体系结构的（  ）的组合驱动的。', CAST('[{"key":"A","text":"产品、功能需求和设计活动"},{"key":"B","text":"商业、质量和功能需求"},{"key":"C","text":"商业、产品和功能需求"},{"key":"D","text":"商业、质量和设计活动"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'ABSD方法';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'ABSD方法是一个自顶向下、递归细化的方法，软件系统的体系结构通过该方法得到细化，直到能产生（  ）。', CAST('[{"key":"A","text":"软件产品和代码"},{"key":"B","text":"软件构件和类"},{"key":"C","text":"软件构件和连接件"},{"key":"D","text":"类和软件代码"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'ABSD方法';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在批处理风格软件体系结构中，每个处理步骤是一个单独的程序，每一步必须在前一步结束后才能开始，并且数据必须是完整的，以（  ）的方式传递。', CAST('[{"key":"A","text":"迭代"},{"key":"B","text":"整体"},{"key":"C","text":"统一格式"},{"key":"D","text":"递增"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件体系结构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '基于规则的系统包括规则集、规则解释器、规则/数据选择器及（  ）。', CAST('[{"key":"A","text":"解释引擎"},{"key":"B","text":"虚拟机"},{"key":"C","text":"数据"},{"key":"D","text":"工作内存"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件体系结构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '简述管道-过滤器风格的特点，并举例说明其应用场景。', CAST('[]' AS JSON), '管道-过滤器风格中，每个组件（过滤器）都有一组输入和输出，组件读取输入数据流，经过处理产生输出数据流，通过管道（连接件）连接。特点：高内聚低耦合，支持重用和并发执行，但可能引入额外开销。应用场景：编译器、图像处理、数据流处理等。',
       CAST('["过滤器独立处理数据流","管道连接过滤器","高内聚低耦合","支持重用和并发","应用场景举例"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件体系结构风格';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在软件架构复用中，（  ）是指开发过程中，只要发现有可复用的资产，就对其进行复用。', CAST('[{"key":"A","text":"发现复用"},{"key":"B","text":"机会复用"},{"key":"C","text":"资产复用"},{"key":"D","text":"过程复用"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构复用';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在软件架构复用中，（  ）是指在开发之前，就要进行规划，以决定哪些需要复用。', CAST('[{"key":"A","text":"预期复用"},{"key":"B","text":"计划复用"},{"key":"C","text":"资产复用"},{"key":"D","text":"系统复用"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构复用';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '软件复用过程的主要阶段包括（  ）。', CAST('[{"key":"A","text":"分析可复用的软件资产、管理可复用资产和使用可复用资产"},{"key":"B","text":"构造/获取可复用的软件资产、管理可复用资产和使用可复用资产"},{"key":"C","text":"构造/获取可复用的软件资产和管理可复用资产"},{"key":"D","text":"分析可复用的软件资产和使用可复用资产"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件复用过程';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'DSSA（Domain Specific Software Architecture）就是在一个特定应用领域中为一组应用提供组织结构参考的标准软件体系结构，实施DSSA的过程中包含了一些基本的活动。其中，领域模型是（  ）阶段的主要目标。', CAST('[{"key":"A","text":"领域设计"},{"key":"B","text":"领域实现"},{"key":"C","text":"领域分析"},{"key":"D","text":"领域工程"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'DSSA';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '质量属性场景是一个具体的质量属性需求，是利益相关者与系统的交互的简短陈述，它由刺激源、刺激、环境、制品、（  ）六部分组成。', CAST('[{"key":"A","text":"响应和响应度量"},{"key":"B","text":"系统和系统响应"},{"key":"C","text":"依赖和响应"},{"key":"D","text":"响应和优先级"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '质量属性场景';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '想要学习系统特性、有效使用系统、使错误的影响最低、适配系统、对系统满意属于（  ）质量属性场景的刺激。', CAST('[{"key":"A","text":"可用性"},{"key":"B","text":"性能"},{"key":"C","text":"易用性"},{"key":"D","text":"安全性"}]' AS JSON), 'C',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '质量属性场景';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '效用树是采用架构权衡分析方法（ATAM）进行架构评估的工具之一，其树形结构从根部到叶子节点依次为（  ）。', CAST('[{"key":"A","text":"树根、属性分类、优先级、质量属性场景"},{"key":"B","text":"树根、质量属性、属性分类、质量属性场景"},{"key":"C","text":"树根、优先级、质量属性、质量属性场景"},{"key":"D","text":"树根、质量属性、属性分类、优先级"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'ATAM';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '平均失效等待时间（MTTF）和平均失效间隔时间（MTBF）是进行系统可靠性分析时的重要指标，在失效率为常数和修复时间很短的情况下，（  ）。', CAST('[{"key":"A","text":"MTTF远远小于MTBF"},{"key":"B","text":"MTTF和MTBF无法计算"},{"key":"C","text":"MTTF远远大于MTBF"},{"key":"D","text":"MTTF和MTBF几乎相等"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '可靠性分析';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在进行软件系统安全性分析时，（  ）保证信息不泄露给未授权的用户、实体或过程。', CAST('[{"key":"A","text":"完整性"},{"key":"B","text":"不可否认性"},{"key":"C","text":"可控性"},{"key":"D","text":"机密性"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '安全性分析';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '在进行软件系统安全性分析时，（  ）保证对信息的传播及内容具有控制的能力，防止为非法者所用。', CAST('[{"key":"A","text":"完整性"},{"key":"B","text":"安全审计"},{"key":"C","text":"加密性"},{"key":"D","text":"可控性"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '安全性分析';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '5G 网络采用（ ）可将 5G 网络分割成多张虚拟网络，每个虚拟网络的接入、传输和核心网是逻辑独立的，任何一个虚拟网络发生故障都不会影响到其它虚拟网络。', CAST('[{"key":"A","text":"网络切片技术"},{"key":"B","text":"边缘计算技术"},{"key":"C","text":"网络隔离技术"},{"key":"D","text":"软件定义网络技术"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '网络技术';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', '计算机产生的随机数大体上能在(0，1)区间内均匀分布。假设某初等函数 f(x)在（0，1）区间内取值也在（0，1）区间内，如果由计算机产生的大量的（M 个）随机数对（r1，r2）中，符合 r2≤f（r1）条件的有 N 个，则 N/M 可作为（ ）的近似计算结果。', CAST('[{"key":"A","text":"求解方程 f(x)=x"},{"key":"B","text":"求 f(x)极大值"},{"key":"C","text":"求 f(x)的极小值"},{"key":"D","text":"求积分"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数学应用';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'Micro-service is a software development technology, which advocates dividing a single application into a group of small services，which coordinates and cooperates with each other to provide ultimate value for users.The micro-service (  ) has many important benefits.First, it solves the problem of business complexity.It decomposes the original huge single application into a group of services.Although the total amount of functions remains the same,the application has been decomposed into manageable services.The development speed of a single service is much faster,and it is easier to understand and（  ）.Second,this architecture allows each service to be（  ）independently by a team.Developers are free to choose any appropriate technology.Third,the micro-service architecture mode enables each service to be（  ）independently.Developers never need to coordinate the deployment of local changes to their services.These types of changes can be deployed immediately after testing.Finally,the micro-service architecture enables each service to（  ）independently.', CAST('[{"key":"A","text":"architecture"},{"key":"B","text":"software"},{"key":"C","text":"application"},{"key":"D","text":"technology"}]' AS JSON), 'A',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '专业英语';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'Micro-service is a software development technology, which advocates dividing a single application into a group of small services，which coordinates and cooperates with each other to provide ultimate value for users.The micro-service (  ) has many important benefits.First, it solves the problem of business complexity.It decomposes the original huge single application into a group of services.Although the total amount of functions remains the same,the application has been decomposed into manageable services.The development speed of a single service is much faster,and it is easier to understand and（  ）.Second,this architecture allows each service to be（  ）independently by a team.Developers are free to choose any appropriate technology.Third,the micro-service architecture mode enables each service to be（  ）independently.Developers never need to coordinate the deployment of local changes to their services.These types of changes can be deployed immediately after testing.Finally,the micro-service architecture enables each service to（  ）independently.', CAST('[{"key":"A","text":"develop"},{"key":"B","text":"maintain"},{"key":"C","text":"utilize"},{"key":"D","text":"deploy"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '专业英语';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'Micro-service is a software development technology, which advocates dividing a single application into a group of small services，which coordinates and cooperates with each other to provide ultimate value for users.The micro-service (  ) has many important benefits.First, it solves the problem of business complexity.It decomposes the original huge single application into a group of services.Although the total amount of functions remains the same,the application has been decomposed into manageable services.The development speed of a single service is much faster,and it is easier to understand and（  ）.Second,this architecture allows each service to be（  ）independently by a team.Developers are free to choose any appropriate technology.Third,the micro-service architecture mode enables each service to be（  ）independently.Developers never need to coordinate the deployment of local changes to their services.These types of changes can be deployed immediately after testing.Finally,the micro-service architecture enables each service to（  ）independently.', CAST('[{"key":"A","text":"planned"},{"key":"B","text":"developed"},{"key":"C","text":"utilized"},{"key":"D","text":"deployed"}]' AS JSON), 'B',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '专业英语';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'Micro-service is a software development technology, which advocates dividing a single application into a group of small services，which coordinates and cooperates with each other to provide ultimate value for users.The micro-service (  ) has many important benefits.First, it solves the problem of business complexity.It decomposes the original huge single application into a group of services.Although the total amount of functions remains the same,the application has been decomposed into manageable services.The development speed of a single service is much faster,and it is easier to understand and（  ）.Second,this architecture allows each service to be（  ）independently by a team.Developers are free to choose any appropriate technology.Third,the micro-service architecture mode enables each service to be（  ）independently.Developers never need to coordinate the deployment of local changes to their services.These types of changes can be deployed immediately after testing.Finally,the micro-service architecture enables each service to（  ）independently.', CAST('[{"key":"A","text":"utilized"},{"key":"B","text":"developed"},{"key":"C","text":"tested"},{"key":"D","text":"deployed"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '专业英语';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SINGLE', 'Micro-service is a software development technology, which advocates dividing a single application into a group of small services，which coordinates and cooperates with each other to provide ultimate value for users.The micro-service (  ) has many important benefits.First, it solves the problem of business complexity.It decomposes the original huge single application into a group of services.Although the total amount of functions remains the same,the application has been decomposed into manageable services.The development speed of a single service is much faster,and it is easier to understand and（  ）.Second,this architecture allows each service to be（  ）independently by a team.Developers are free to choose any appropriate technology.Third,the micro-service architecture mode enables each service to be（  ）independently.Developers never need to coordinate the deployment of local changes to their services.These types of changes can be deployed immediately after testing.Finally,the micro-service architecture enables each service to（  ）independently.', CAST('[{"key":"A","text":"analyze"},{"key":"B","text":"use"},{"key":"C","text":"design"},{"key":"D","text":"expand"}]' AS JSON), 'D',
       CAST('[]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '专业英语';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述软件质量属性中安全性和可修改性的含义，并各举一个例子。', CAST('[]' AS JSON), '安全性是指系统在遭受恶意攻击时仍能正常运行或保护数据的能力，例如身份认证、访问控制、数据加密等。可修改性是指系统能够快速、经济地适应需求变化的能力，例如模块化设计、低耦合等。',
       CAST('["安全性定义正确","可修改性定义正确","各举一个恰当例子"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件架构质量属性';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简述数据流图在分层细化过程中遵循的数据平衡原则。', CAST('[]' AS JSON), '数据平衡原则包括：1）父图与子图之间的平衡：子图边界上的输入/输出数据流必须与父图对应加工的输入/输出数据流保持一致；若子图中数据流的数据项全体等于父图中的数据流，也视为平衡。2）子图内部平衡：加工的输入和输出数据流必须平衡，即每个加工必须有输入和输出。',
       CAST('["父图与子图平衡","子图内部平衡","数据流一致性","数据项全体相等也平衡"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据流图';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请用200字以内的文字简要说明数据流图和数据字典在软件需求分析和设计阶段的作用。', CAST('[]' AS JSON), '数据流图在分析阶段用于建立系统的功能模型，帮助理解系统功能；在设计阶段为模块划分和接口设计提供依据。数据字典是数据流图中所有数据元素的定义，确保数据在系统中的完整性和一致性，是所有人员工作的统一标准，支持列表、相互参照、检索、一致性检验和完整性检验。',
       CAST('["数据流图在分析阶段建立功能模型","数据流图在设计阶段提供模块划分依据","数据字典确保数据完整性和一致性","数据字典作为统一标准"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据字典';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请分别说明心跳检测和超时探测技术的基本原理及特点。', CAST('[]' AS JSON), '心跳检测：节点定期发送心跳消息，若在指定时间内未收到，则认为节点故障。特点：实现简单，但可能产生额外网络负载。超时探测：发送探测请求，若在超时时间内未收到响应，则判定故障。特点：可检测网络延迟，但可能误判。',
       CAST('["心跳检测原理","心跳检测特点","超时探测原理","超时探测特点"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '嵌入式系统故障检测';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请说明数据驱动方法的基本概念，并说明王工提出采用此方法的理由。', CAST('[]' AS JSON), '数据驱动方法是通过对系统运行过程中的监测数据进行分析，在无精准数学模型的情况下进行故障诊断，具体包括机器学习、统计分析法和信号分析法。理由：宇航系统非常复杂，难以建立精准数学模型，而数据驱动方法不需要精准模型，更适合分布式综合化电子系统。',
       CAST('["数据驱动方法定义","具体方法举例","宇航系统难以建模","数据驱动方法不需要模型"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '故障诊断方法';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简要介绍数据实时同步更新方案和异步准实时更新方案的基本思路，并说明全国仓储货物管理系统应该采用哪种方案及原因。', CAST('[]' AS JSON), '实时同步方案：数据库更新时立即更新缓存。异步准实时方案：数据库更新时记录日志，异步排队更新缓存。应采用异步准实时方案，因为系统要求响应时间小于1秒，实时同步可能因并发导致性能不可控，而异步准实时能保证性能。',
       CAST('["实时同步方案思路","异步准实时方案思路","选择异步准实时","原因：性能要求"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '数据库缓存';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请简要说明哈希算法和一致性哈希算法的基本原理，并说明采用一致性哈希算法的原因。', CAST('[]' AS JSON), '哈希算法：对key进行哈希，然后取模映射到节点，如key%N。一致性哈希：将节点和数据都映射到哈希环上，数据沿环顺时针找到第一个节点。一致性哈希在节点增减时只需迁移少量数据，而哈希算法需要重新映射几乎所有数据。',
       CAST('["哈希算法原理","一致性哈希原理","一致性哈希优点：节点变化影响小"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '缓存分片';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请解释布隆过滤器的工作原理和优缺点。', CAST('[]' AS JSON), '布隆过滤器通过一个很长的二进制向量和一系列随机映射函数记录元素是否在集合中。查询时，若元素映射的位都为1，则可能存在；若有0，则一定不存在。优点：占用内存小、查询效率高、不需要存储元素本身。缺点：有一定误判率（假阳性）、不能获取元素本身、一般不能删除元素。',
       CAST('["工作原理：位数组+哈希函数","优点：内存小、效率高","缺点：误判、不能删除"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '布隆过滤器';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请用300字以内的文字简要说明MQTT协议。', CAST('[]' AS JSON), 'MQTT（消息队列遥测传输）是一个基于发布/订阅模式的消息协议。它工作在TCP/IP协议族上，是为硬件性能低下的远程设备以及网络状况糟糕的情况下而设计的发布/订阅型消息协议。MQTT协议是轻量、简单、开放和易于实现的。',
       CAST('["MQTT是基于发布/订阅模式的消息协议","工作在TCP/IP协议族上","为硬件性能低下的远程设备和网络状况糟糕的情况设计","轻量、简单、开放、易于实现"]' AS JSON), 'EASY', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = 'MQTT协议';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请结合HTTP协议和MQTT协议的特点，为图5-1中(1)～(6)处选择合适的协议；并结合张工关于功能模块的描述，补充完善图5-1中(7)～(10)处的空白。', CAST('[]' AS JSON), '(1)HTTP (2)MQTT (3)MQTT (4)MQTT (5)HTTP (6)HTTP (7)端侧识别模块 (8)模型训练模块 (9)设备调度平台模块 (10)访客注册模块',
       CAST('["云端与边缘设备之间采用MQTT协议进行通信","云端与前端小程序之间采用HTTP协议","边缘设备与端侧识别模块之间采用MQTT协议","根据功能描述确定各模块名称"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '边缘计算架构';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请用300字以内的文字，从数据通信、数据安全和系统性能等方面简要分析在传统云计算模型中引入边缘计算模型的优势。', CAST('[]' AS JSON), '数据通信：通信更快捷，数据量更少。因为数据处理对比在边缘设备上完成，通信更多时候只传输匹配与结果的指令。数据安全：数据以加密方式存储在需要用到的边缘设备上，本地化处理比对，减少原始信息在网上的传递带来的安全隐患。黑客也无法通过攻破一个结点使整个系统瘫痪。系统性能：性能更高，以人脸识别为例，在进行识别时，只在本地进行比对不用把人脸数据传递到远程服务器对比。',
       CAST('["数据通信：通信更快捷，数据量更少","数据安全：数据本地化处理，减少传输安全隐患","系统性能：本地处理，提高响应速度","边缘计算减少对中心云的依赖"]' AS JSON), 'MEDIUM', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '边缘计算优势';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请围绕“基于构件的软件开发方法及其应用”论题，依次从以下三个方面进行论述。1. 概要叙述你参与管理和开发的软件项目，以及你在其中所承担的主要工作。2. 详细论述基于构件的软件开发方法的主要过程。3. 结合你具体参与管理和开发的实际项目，请说明具体实施过程以及碰到的主要问题。', CAST('[]' AS JSON), '基于构件的软件开发（CBSD）是一种通过组装可复用构件来构造软件的方法。主要过程包括：构件获取、构件管理、构件组装和系统演化。在项目中，我负责构件库的建立和维护，通过检索和评估选择合适的构件，并利用构件组装工具进行系统集成。遇到的主要问题包括构件的兼容性、版本管理和性能优化等。',
       CAST('["构件获取：从内部或外部获取可复用构件","构件管理：建立构件库，进行分类和检索","构件组装：根据架构选择合适的构件进行组装","系统演化：根据需求变化调整构件","常见问题：兼容性、版本管理、性能"]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '基于构件的软件开发';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请围绕“软件维护方法及其应用”论题，依次从以下三个方面进行论述。1. 概要叙述你参与管理和开发的软件项目，以及你在其中所承担的主要工作。2. 详细论述影响软件维护工作的因素有哪些。3. 结合你具体参与管理和开发的实际项目，说明在具体维护过程中，如何度量软件的可维护性，说明具体的软件维护工作类型。', CAST('[]' AS JSON), '影响软件维护的因素包括：可理解性、可测试性、可修改性、可移植性等。度量可维护性可以通过MTTR（平均修复时间）和软件复杂性度量（如圈复杂度）等。维护类型包括改正性维护、适应性维护、完善性维护和预防性维护。在项目中，我负责维护一个大型系统，通过代码审查和重构提高可维护性，并针对用户需求进行完善性维护。',
       CAST('["影响因素：可理解性、可测试性、可修改性、可移植性","度量方法：MTTR、圈复杂度等","维护类型：改正性、适应性、完善性、预防性","实际维护中应注重代码质量和文档"]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '软件维护';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请围绕“区块链技术及应用”论题，依次从以下三个方面进行论述。1. 概要叙述你参与管理和开发的软件项目以及你在其中所承担的主要工作。2. 区块链包含多种核心技术，请简要描述区块链的3种核心技术。3. 具体阐述你参与管理和开发的项目是如何应用区块链技术进行设计与实现。', CAST('[]' AS JSON), '区块链的三种核心技术包括：分布式账本、共识机制和智能合约。分布式账本确保数据不可篡改；共识机制保证节点间数据一致性；智能合约实现自动化执行。在项目中，我参与了一个供应链金融系统，利用区块链实现交易透明和防篡改，通过智能合约自动执行付款条件。',
       CAST('["分布式账本：数据不可篡改，去中心化存储","共识机制：如工作量证明、权益证明等","智能合约：自动执行合约条款","应用场景：供应链金融、资产管理等"]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '区块链技术';
INSERT INTO question
  (unit_id, type, content, options, answer, key_points, difficulty, explanation, images, tags, source_url, status)
SELECT u.id, 'SHORT', '请围绕“湖仓一体架构及其应用”论题，依次从以下三个方面进行论述。1. 概要叙述你参与管理和开发的、采用湖仓一体架构的软件项目以及你在其中所承担的主要工作。2. 请对湖仓一体架构进行总结与分析，给出其中四类关键特征，并简要对这四类关键特征的内涵进行阐述。3. 具体阐述你参与管理和开发的项目是如何采用湖仓一体架构的，并围绕上述四类关键特征，详细论述在项目设计与实现过程中遇到了哪些实际问题，是如何解决的。', CAST('[]' AS JSON), '湖仓一体架构的关键特征包括：事务一致性、实时处理能力、统一数据存储和多元数据分析。在项目中，我参与了一个数据平台建设，采用湖仓一体架构，实现了数据湖和数据仓库的融合，支持实时和离线分析。遇到的主要问题包括数据一致性保障和实时处理性能优化，通过引入Delta Lake和流批一体技术解决。',
       CAST('["事务一致性：支持ACID事务","实时处理能力：支持流式数据处理","统一数据存储：存储结构化、半结构化和非结构化数据","多元数据分析：支持多种分析引擎","实际应用：解决数据一致性和实时性挑战"]' AS JSON), 'HARD', NULL,
       NULL, NULL, NULL, 'ON'
FROM unit u
JOIN soft_exam_import_guard g ON g.id = 1
WHERE u.bank_id = @soft_bank_id AND u.name = '湖仓一体架构';

COMMIT;
DROP TEMPORARY TABLE IF EXISTS soft_exam_import_guard;
