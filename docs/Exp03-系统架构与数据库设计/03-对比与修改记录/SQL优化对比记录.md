# SQL 优化对比记录：LLM 初稿 对 人工优化版

> 运行表要求"LLM 生成 SQL 初稿后人工优化，保留优化前后对比"。初稿 `02-LLM输出/01-SQL初稿-LLM版.sql`，优化版 `04-最终版/schema-sprint1.sql` 与 `schema-sprint2.sql`。
> 批注：周浩然、杨子航，2026-09-14 晚，先在打印稿上用红笔批，照片 `01-手工原稿/SQL优化批注-手写-第N页.jpg`，再改文件。验证：袁飞翔。记录：张家森。

## 基本信息

| 项目           | 内容                                                      |
| -------------- | --------------------------------------------------------- |
| LLM 工具与版本 | Claude Code，Claude Fable 5.1，2026-09-14                 |
| 初稿规模       | 19 张表，单文件，utf8（即 utf8mb3）                       |
| 优化版规模     | Sprint 1：13 张表、6 个触发器；Sprint 2：7 张表、2 个视图 |
| 验证环境       | MySQL 8.4.10，本机独立测试实例，端口 33306                |

## 第一部分：逐条改动

分类：结构 = 改了表与关系；约束 = 改了键、外键、CHECK、触发器；类型 = 改了字段类型或长度；红线 = 违反实验 1 红线；整理 = 命名与文件组织。决定：改 / 不改。

| 序号 | 位置                                  | 初稿怎么写                                                           | 改成什么                                                                                                           | 分类 | 理由                                                                                                 | 谁     |
| ---- | ------------------------------------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ | ---- | ---------------------------------------------------------------------------------------------------- | ------ |
| 1    | snapshot                              | `content LONGTEXT` 每个快照存全文                                    | `is_keyframe` + `base_snapshot_id` + `delta MEDIUMBLOB` + `full_text`，CHECK 二选一                                | 结构 | AC-WRK-02-5 要求增量存储小于全文之和 30%。全文存法 50 个快照就是 50 份全文；关键帧每 10 个一次       | 周浩然 |
| 2    | policy                                | 一行一课程，`version` 原地递增，有 `updated_at`                      | 改名 `policy_version`，每次修改插新行，`UNIQUE(course_id, scope_assignment_id, version_no)`，无 updated_at         | 结构 | K-2：声明按提交时版本对照、学生按版本确认。原地更新则旧版本消失，AC-RULE-01-6、AC-DECL-01-5 都做不了 | 杨子航 |
| 3    | policy.assignment_id                  | `NULL` 表示课程级                                                    | `scope_assignment_id NOT NULL DEFAULT 0`，0 表示课程级                                                             | 约束 | MySQL 唯一键对 NULL 不生效，课程级会出现重复 version_no                                              | 杨子航 |
| 4    | policy_ack                            | 外键指向 `policy`                                                    | 指向 `policy_version`，加 `UNIQUE(policy_version_id, student_id, assignment_id)`                                   | 约束 | 确认的是"某一版"，不是"规则"                                                                         | 杨子航 |
| 5    | registration                          | 有 `updated_at`，修改即原地更新                                      | 删 updated_at，加 `supersedes_id` 自引用与 `status ENUM('ACTIVE','VOIDED')`，触发器只放行 status 变更              | 结构 | K-7：Sprint 2 哈希链要求记录不可变。追加式修改 AC-REG-02-1                                           | 周浩然 |
| 6    | work.writing_minutes                  | `INT COMMENT '累计写作时长（分钟）'`                                 | 删除                                                                                                               | 红线 | 红线 不5、AC-TCH-03-3：时间只记不评。LLM 主动加了一个我们明确不要的字段                              | 周浩然 |
| 7    | 所有外键                              | `ON DELETE CASCADE`                                                  | 全部 `ON DELETE RESTRICT`                                                                                          | 约束 | 系统没有删除动作。级联删除意味着删一门课就抹掉所有证据链，与项目定位相反                             | 杨子航 |
| 8    | snapshot / registration / declaration | 无保护                                                               | 6 个 BEFORE DELETE / UPDATE 触发器，SIGNAL 45000                                                                   | 约束 | 应用层不提供删除接口，数据库再挡一道。AC-WRK-02-4、AC-REG-02-3、AC-DECL-01-3 的数据层保证            | 杨子航 |
| 9    | 库字符集                              | `utf8` / `utf8_general_ci`                                           | `utf8mb4` / `utf8mb4_0900_ai_ci`                                                                                   | 类型 | utf8 在 MySQL 是三字节，提示词里有 emoji 就写不进去；8.4 已发弃用警告                                | 周浩然 |
| 10   | course.code                           | `VARCHAR(10)`，无唯一键                                              | `join_code CHAR(6) CHARACTER SET ascii COLLATE ascii_general_ci`，唯一键                                           | 类型 | A-12 定了 6 位；AC-ACC-02-4 不区分大小写靠排序规则实现，不靠应用转大写                               | 杨子航 |
| 11   | user                                  | 表名 `user`，`role VARCHAR(20)`，`password VARCHAR(255)`，无锁定字段 | `app_user`，`role ENUM`，`password_hash CHAR(60)`，加 `must_change_password`、`failed_attempts`、`locked_until`    | 结构 | AC-ACC-04-2、04-4 要求首次改密与 5 次锁定；`user` 与 MySQL 系统表同名易混                            | 杨子航 |
| 12   | enrollment                            | 代理键 `id`，无唯一约束                                              | 复合主键 `(course_id, student_id)`                                                                                 | 约束 | AC-ACC-02-3 重复加入靠主键挡                                                                         | 杨子航 |
| 13   | work                                  | 无唯一约束                                                           | `UNIQUE(assignment_id, student_id)`；加 `pending_edit_chars`、`is_late`、`last_saved_at`；CHECK char_count ≤ 50000 | 约束 | 一人一作业一稿；AC-WRK-02-2 需要累计改动量；AC-DECL-01-6 逾期                                        | 周浩然 |
| 14   | 所有 `word_count`                     | "字数"                                                               | `char_count`，注释写明"字符"                                                                                       | 整理 | A-3、A-10：全系统统一字符                                                                            | 周浩然 |
| 15   | 所有 `hash VARCHAR(255)`              | 可变长                                                               | `CHAR(64) CHARACTER SET ascii`                                                                                     | 类型 | SHA-256 十六进制定长 64；ascii 省空间且比较快                                                        | 杨子航 |
| 16   | 所有 `TIMESTAMP`                      | 秒精度，2038 上限                                                    | `DATETIME(3)`                                                                                                      | 类型 | 快照与粘贴事件同一秒内可能多条，毫秒排序；哈希链把时间戳算进去，精度要稳定                           | 周浩然 |
| 17   | paste_event.source                    | `VARCHAR(20)`，四个值                                                | `ENUM` 加 `PENDING` 并作默认；加 `offset_start`、`offset_end`；加 `snapshot_id`                                    | 结构 | AC-WRK-03-4"稍后"要记为来源待定；Sprint 2 归因需要粘贴位置                                           | 周浩然 |
| 18   | registration 工具                     | `tool_id` 可空、`tool_name` 可空，无约束                             | CHECK 二者至少一个                                                                                                 | 约束 | AC-REG-01-4 允许自定义名，但不能两个都空                                                             | 周浩然 |
| 19   | registration 枚举                     | stage / adoption / verification 都是 VARCHAR(20)                     | 三个 ENUM，取值与 ER 图枚举表一致                                                                                  | 类型 | A-7 定了采用方式四个值；VARCHAR 会出现"直接采用""直接使用"两种写法                                   | 周浩然 |
| 20   | declaration                           | `content TEXT`，无规则版本，无逾期                                   | `content_json JSON`，`policy_version_id` 外键，`is_late`，`UNIQUE(work_id)`                                        | 结构 | AC-DECL-01-1 七要素结构化才能导 PDF 和证据包；01-5 规则版本；01-6 逾期；一稿一份                     | 周浩然 |
| 21   | policy_scene                          | 代理键，`scene_name VARCHAR(50)`                                     | 复合主键 `(policy_version_id, scene_name)`，`VARCHAR(30)`                                                          | 约束 | 弱实体按 ER 图用复合键；AC-RULE-01-3 上限 30 字符                                                    | 杨子航 |
| 22   | registration_paragraph                | 代理键，无 paragraph_hash                                            | 复合主键，加 `paragraph_hash`                                                                                      | 约束 | AC-REG-03-2 原段删除后靠哈希显示"原段落已删除"                                                       | 周浩然 |
| 23   | paragraph_attribution                 | `ai_ratio FLOAT`                                                     | `DECIMAL(5,2)` 加 CHECK 0 到 100                                                                                   | 类型 | 占比要精确到两位小数，FLOAT 累加会漂                                                                 | 周浩然 |
| 24   | audit_log                             | `action VARCHAR(50)`，`ip VARCHAR(50)`                               | `action ENUM` 八个值，`object_type ENUM`，`ip VARCHAR(45)`                                                         | 类型 | 审计动作是有限集合；IPv6 最长 45                                                                     | 杨子航 |
| 25   | 文件组织                              | 19 张表一个文件                                                      | Sprint 1 十三张表一个文件，Sprint 2 七张表一个文件，另加验证脚本                                                   | 整理 | Sprint 1 不建用不到的表；实验 8 重估后 Sprint 2 表可能改                                             | 张家森 |
| 26   | 统计                                  | 无                                                                   | 两个视图 `v_affairs_*`，只含计数与课程名、工具名                                                                   | 结构 | STAT-01、AC-PERM-01-4：教务只看统计，视图里没有正文列，权限直接绑视图                                | 杨子航 |
| 27   | app_setting                           | 无                                                                   | 新增，预置保留期限 1 年                                                                                            | 结构 | PERM-03                                                                                              | 杨子航 |
| 28   | evidence_export                       | 只有 `manifest_hash`                                                 | 加 `snapshot_head`、`registration_head`、`exported_by`                                                             | 结构 | 校验时要知道导出那一刻两条链的链头                                                                   | 杨子航 |
| 29   | 初稿建议"快照全文迁对象存储"          | 建议                                                                 | 不采纳                                                                                                             | 整理 | 增量存储后课程作业规模用不着；YAGNI                                                                  | 周浩然 |
| 30   | 初稿"枚举用 VARCHAR 便于扩展"         | 假设 5                                                               | 不采纳                                                                                                             | 整理 | 取值是验收标准定死的，扩展时改 ENUM 是显式动作，比脏数据好                                           | 周浩然 |

统计：

| 分类 | 条数                        |
| ---- | --------------------------- |
| 结构 | 11                          |
| 约束 | 8                           |
| 类型 | 7                           |
| 红线 | 1                           |
| 整理 | 3                           |
| 合计 | 30，改 28，不采纳初稿建议 2 |

初稿保留下来没动的：表的总体划分（每实体一表、M:N 落中间表）、字段命名风格、中文注释、ai_tool 预置八个工具、Sprint 2 五张表的基本形态。

## 第二部分：验证（袁飞翔）

环境：本机 MySQL 8.4.10 二进制起的独立实例，数据目录临时，端口 33306，客户端 utf8mb4。

| 步骤                        | 结果                                                       |
| --------------------------- | ---------------------------------------------------------- |
| 跑 LLM 初稿                 | 能跑，19 张表。库字符集 utf8mb3，8.4 发弃用警告 3719、3778 |
| 对初稿跑断言 15（时长字段） | 命中 1 列：work.writing_minutes                            |
| 跑优化版 Sprint 1           | 通过，13 张表，6 个触发器，8 条预置工具                    |
| 跑优化版 Sprint 2           | 通过，7 张表，2 个视图。合计 20 表 2 视图 6 触发器         |
| 跑 schema-verify.sql        | 17 条断言全部符合期望，见下表                              |

| 断言 | 验什么                | 期望        | 实际                                              |
| ---- | --------------------- | ----------- | ------------------------------------------------- |
| 1    | 课程码不区分大小写    | 查到 1 行   | 1                                                 |
| 2    | 同一人同一作业第二稿  | 1062 重复   | ERROR 1062 uk_work_assignment_student             |
| 3    | 重复加入课程          | 1062 重复   | ERROR 1062 enrollment.PRIMARY                     |
| 4    | 关键帧快照插入        | 成功        | 成功                                              |
| 5    | 差分快照插入          | 成功        | 成功                                              |
| 6    | 无全文无差分的快照    | 3819 CHECK  | ERROR 3819 ck_snapshot_payload                    |
| 7    | 删除快照              | 1644 触发器 | ERROR 1644 snapshot 不可删除（AC-WRK-02-4）       |
| 8    | 登记最少字段          | 成功        | 成功                                              |
| 9    | 登记无工具无自定义名  | 3819 CHECK  | ERROR 3819 ck_reg_tool                            |
| 10   | 作废旧登记只改 status | 成功        | 成功                                              |
| 11   | 改登记正文            | 1644 触发器 | ERROR 1644 只允许把 status 置为 VOIDED            |
| 12   | 粘贴事件默认来源      | PENDING     | PENDING                                           |
| 13   | 声明插入              | 成功        | 成功                                              |
| 14   | 改声明                | 1644 触发器 | ERROR 1644 declaration 提交后只读（AC-DECL-01-3） |
| 15   | 全库无时长字段        | 0           | 0                                                 |
| 16   | 统计视图无正文列      | 0           | 0                                                 |
| 17   | 统计视图可查          | 返回课程行  | 软件工程，0 份提交，当前档 DECLARE                |

## LLM 初稿的典型问题

- 它把 ER 图批注里的三句话全忽略了：只追加不更新、增量存储、规则版本化。这三件事恰好是本项目和普通 CRUD 系统的区别，也是四个手写算法的落点。
- 它主动加了一个我们没画、而且红线明确不要的字段 writing_minutes。LLM 会按"常见系统长什么样"补东西，补的正好是我们不要的。
- 默认 CASCADE 和 utf8 是训练数据里的老习惯，2026 年的 MySQL 8.4 已经对 utf8 报弃用警告。
- 优点：表的划分和中间表处理是对的，中文注释省了我们时间，预置工具清单直接能用。总体上初稿省了大约一小时的打字，但每张表都要重看。

## 人工侧的典型问题

- ER 图第一版没画 paste_event 的 offset，是袁飞翔对着 Sprint 2 的归因卡挑出来的，画图时只想着 Sprint 1。
- 类图上枚举值没写全，录入时才补，说明画图前应该先把枚举表定下来。
- 触发器是杨子航坚持加的，周浩然一开始觉得应用层挡住就够了。最后同意的理由是：答辩演示时直接在库里 DELETE 一条给老师看报错，比讲"我们应用层没这个接口"有说服力。
