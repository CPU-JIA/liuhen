# SQL 初稿说明（LLM 版）

> 生成工具：Claude Code（Claude Fable 5.1），2026-09-14。与 `01-SQL初稿-LLM版.sql` 同时生成，是 LLM 对自己脚本的说明，原样保存。

## 设计假设

1. 所有表使用 InnoDB，字符集 utf8，排序规则 utf8_general_ci。
2. 每个实体一张表，主键统一为自增 BIGINT `id`。
3. 弱实体（场景、快照、段落归因）使用代理键加外键实现，未使用复合主键。
4. M:N 联系（选修、确认阅读、关联段落、申诉复核）落成中间表。
5. 枚举字段使用 VARCHAR(20) 存储，以便后续扩展取值。
6. 所有外键设置 ON DELETE CASCADE，保证删除课程时级联清理其下数据。
7. 快照表保存每个版本的全文，便于直接查询任意版本。
8. 规则表保留 `version` 字段用于记录修改次数，修改时原地更新并递增。
9. 登记表保留 `updated_at` 字段，修改登记时原地更新。
10. 作业稿表增加 `writing_minutes` 字段用于统计学生累计写作时长。
11. 时间字段统一使用 TIMESTAMP 并设置默认当前时间。
12. 哈希字段使用 VARCHAR(255) 以兼容不同哈希算法输出长度。

## 表清单

| 序号 | 表名                    | 对应实体或联系 | Sprint |
| ---- | ----------------------- | -------------- | ------ |
| 1    | user                    | 用户           | 1      |
| 2    | course                  | 课程           | 1      |
| 3    | enrollment              | 选修           | 1      |
| 4    | assignment              | 作业           | 1      |
| 5    | policy                  | 规则版本       | 1      |
| 6    | policy_scene            | 场景           | 1      |
| 7    | policy_ack              | 确认阅读       | 1      |
| 8    | work                    | 作业稿         | 1      |
| 9    | snapshot                | 快照           | 1      |
| 10   | paste_event             | 粘贴事件       | 1      |
| 11   | ai_tool                 | AI 工具        | 1      |
| 12   | registration            | 登记           | 1      |
| 13   | registration_paragraph  | 关联段落       | 1      |
| 14   | declaration             | 声明           | 1      |
| 15   | paragraph_attribution   | 段落归因       | 2      |
| 16   | evidence_export         | 证据包         | 2      |
| 17   | appeal、appeal_reviewer | 申诉、指派复核 | 2      |
| 18   | audit_log               | 审计日志       | 2      |

## 建议

- 可根据实际查询需求在 `work_id`、`created_at` 等字段上补充索引。
- 若文本量较大，可考虑将快照全文迁移到对象存储。
