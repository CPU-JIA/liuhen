# ER 图（Chen 氏，手绘录入版）

> 杨子航手绘，2026-09-14 晚，三页，照片 `ER图-手绘-第1页.jpg`（账号与规则）、`第2页.jpg`（作业与留痕）、`第3页.jpg`（Sprint 2 部分与枚举）。录入：张家森。
> Chen 氏记法：实体 = 矩形，弱实体 = 双框矩形，属性 = 椭圆，键属性 = 下划线，多值属性 = 双框椭圆，联系 = 菱形，标识联系 = 双框菱形，基数写在连线上。
> 录入用 Graphviz DOT，`dot -Tpng ER图.dot -o ER图.png` 可渲染；机房没装 Graphviz 时直接看下面的实体表和联系表，内容一致。

## 第 1 页：账号与规则

```dot
digraph ER1 {
  graph [rankdir=LR, splines=true, nodesep=0.4, ranksep=0.6, fontname="Microsoft YaHei"];
  node  [fontname="Microsoft YaHei", fontsize=11];
  edge  [fontname="Microsoft YaHei", fontsize=10];

  // 实体
  User    [shape=box, label="用户 AppUser"];
  Course  [shape=box, label="课程 Course"];
  Assign  [shape=box, label="作业 Assignment"];
  PolicyV [shape=box, label="规则版本 PolicyVersion"];
  Scene   [shape=box, peripheries=2, label="场景 PolicyScene"];

  // 属性（键属性加下划线）
  u1 [shape=ellipse, label=<<u>user_id</u>>];  u2 [shape=ellipse, label="login_no"]; u3 [shape=ellipse, label="name"];
  u4 [shape=ellipse, label="role"]; u5 [shape=ellipse, label="password_hash"]; u6 [shape=ellipse, label="must_change_pwd"];
  u7 [shape=ellipse, label="failed_attempts"]; u8 [shape=ellipse, label="locked_until"];
  User -> {u1 u2 u3 u4 u5 u6 u7 u8} [dir=none];

  c1 [shape=ellipse, label=<<u>course_id</u>>]; c2 [shape=ellipse, label="name"]; c3 [shape=ellipse, label="join_code"]; c4 [shape=ellipse, label="created_at"];
  Course -> {c1 c2 c3 c4} [dir=none];

  a1 [shape=ellipse, label=<<u>assignment_id</u>>]; a2 [shape=ellipse, label="title"]; a3 [shape=ellipse, label="deadline"];
  Assign -> {a1 a2 a3} [dir=none];

  p1 [shape=ellipse, label=<<u>policy_version_id</u>>]; p2 [shape=ellipse, label="version_no"]; p3 [shape=ellipse, label="tier"];
  p4 [shape=ellipse, label="grading_note"]; p5 [shape=ellipse, label="created_at"];
  PolicyV -> {p1 p2 p3 p4 p5} [dir=none];

  s1 [shape=ellipse, label=<<u>scene_name</u>>]; s2 [shape=ellipse, label="allowed"];
  Scene -> {s1 s2} [dir=none];

  // 联系
  R1 [shape=diamond, label="开设"];    User -> R1 [dir=none, label="1"];  R1 -> Course [dir=none, label="N"];
  R2 [shape=diamond, label="选修"];    User -> R2 [dir=none, label="M"];  R2 -> Course [dir=none, label="N"];
  r2a [shape=ellipse, label="joined_at"]; R2 -> r2a [dir=none];
  R3 [shape=diamond, label="包含"];    Course -> R3 [dir=none, label="1"]; R3 -> Assign [dir=none, label="N"];
  R4 [shape=diamond, label="制定"];    Course -> R4 [dir=none, label="1"]; R4 -> PolicyV [dir=none, label="N"];
  R5 [shape=diamond, label="覆盖(S2)"]; Assign -> R5 [dir=none, label="1"]; R5 -> PolicyV [dir=none, label="N"];
  R6 [shape=diamond, peripheries=2, label="含"]; PolicyV -> R6 [dir=none, label="1"]; R6 -> Scene [dir=none, label="N"];
  R7 [shape=diamond, label="确认阅读"]; User -> R7 [dir=none, label="M"]; R7 -> PolicyV [dir=none, label="N"];
  r7a [shape=ellipse, label="acked_at"]; r7b [shape=ellipse, label="assignment_id"]; R7 -> {r7a r7b} [dir=none];
}
```

## 第 2 页：作业与留痕

```dot
digraph ER2 {
  graph [rankdir=LR, splines=true, nodesep=0.4, ranksep=0.6, fontname="Microsoft YaHei"];
  node  [fontname="Microsoft YaHei", fontsize=11];
  edge  [fontname="Microsoft YaHei", fontsize=10];

  User   [shape=box, label="用户 AppUser"];
  Assign [shape=box, label="作业 Assignment"];
  PolicyV[shape=box, label="规则版本 PolicyVersion"];
  Work   [shape=box, label="作业稿 Work"];
  Snap   [shape=box, peripheries=2, label="快照 Snapshot"];
  Paste  [shape=box, label="粘贴事件 PasteEvent"];
  Reg    [shape=box, label="登记 Registration"];
  Tool   [shape=box, label="AI 工具 AiTool"];
  Decl   [shape=box, label="声明 Declaration"];

  w1 [shape=ellipse, label=<<u>work_id</u>>]; w2 [shape=ellipse, label="current_text"]; w3 [shape=ellipse, label="char_count"];
  w4 [shape=ellipse, label="pending_edit_chars"]; w5 [shape=ellipse, label="status"]; w6 [shape=ellipse, label="submitted_at"]; w7 [shape=ellipse, label="is_late"];
  Work -> {w1 w2 w3 w4 w5 w6 w7} [dir=none];

  n1 [shape=ellipse, label=<<u>seq_no</u>>]; n2 [shape=ellipse, label="trigger"]; n3 [shape=ellipse, label="char_count"];
  n4 [shape=ellipse, label="is_keyframe"]; n5 [shape=ellipse, label="delta"]; n6 [shape=ellipse, label="full_text"];
  n7 [shape=ellipse, label="content_hash"]; n8 [shape=ellipse, label="chain_hash"]; n9 [shape=ellipse, label="prev_chain_hash"]; n10 [shape=ellipse, label="created_at"];
  Snap -> {n1 n2 n3 n4 n5 n6 n7 n8 n9 n10} [dir=none];

  e1 [shape=ellipse, label=<<u>paste_id</u>>]; e2 [shape=ellipse, label="occurred_at"]; e3 [shape=ellipse, label="char_count"];
  e4 [shape=ellipse, label="source"]; e5 [shape=ellipse, label="offset_start"]; e6 [shape=ellipse, label="offset_end"];
  Paste -> {e1 e2 e3 e4 e5 e6} [dir=none];

  g1 [shape=ellipse, label=<<u>registration_id</u>>]; g2 [shape=ellipse, label="tool_version"]; g3 [shape=ellipse, label="stage"];
  g4 [shape=ellipse, label="purpose"]; g5 [shape=ellipse, label="adoption"]; g6 [shape=ellipse, label="prompt_text"]; g7 [shape=ellipse, label="output_text"];
  g8 [shape=ellipse, label="verification"]; g9 [shape=ellipse, label="status"]; g10 [shape=ellipse, label="chain_hash"]; g11 [shape=ellipse, label="prev_chain_hash"]; g12 [shape=ellipse, label="created_at"];
  Reg -> {g1 g2 g3 g4 g5 g6 g7 g8 g9 g10 g11 g12} [dir=none];

  t1 [shape=ellipse, label=<<u>tool_id</u>>]; t2 [shape=ellipse, label="name"]; t3 [shape=ellipse, label="is_preset"];
  Tool -> {t1 t2 t3} [dir=none];

  d1 [shape=ellipse, label=<<u>declaration_id</u>>]; d2 [shape=ellipse, label="kind"]; d3 [shape=ellipse, label="generated_at"];
  d4 [shape=ellipse, label="is_late"]; d5 [shape=ellipse, label="content_json"]; d6 [shape=ellipse, label="content_hash"];
  Decl -> {d1 d2 d3 d4 d5 d6} [dir=none];

  R8  [shape=diamond, label="撰写"];   User -> R8 [dir=none, label="1"];   R8 -> Work [dir=none, label="N"];
  R9  [shape=diamond, label="收取"];   Assign -> R9 [dir=none, label="1"]; R9 -> Work [dir=none, label="N"];
  R10 [shape=diamond, peripheries=2, label="留存"]; Work -> R10 [dir=none, label="1"]; R10 -> Snap [dir=none, label="N"];
  R11 [shape=diamond, label="基于"];   Snap -> R11 [dir=none, label="1 (base)"]; R11 -> Snap [dir=none, label="N"];
  R12 [shape=diamond, label="发生"];   Work -> R12 [dir=none, label="1"];  R12 -> Paste [dir=none, label="N"];
  R13 [shape=diamond, label="归属"];   Paste -> R13 [dir=none, label="N"]; R13 -> Reg [dir=none, label="0..1"];
  R14 [shape=diamond, label="登记"];   Work -> R14 [dir=none, label="1"];  R14 -> Reg [dir=none, label="N"];
  R15 [shape=diamond, label="使用"];   Reg -> R15 [dir=none, label="N"];   R15 -> Tool [dir=none, label="0..1"];
  r15a [shape=ellipse, label="tool_name_custom"]; R15 -> r15a [dir=none];
  R16 [shape=diamond, label="替代"];   Reg -> R16 [dir=none, label="1 (new)"]; R16 -> Reg [dir=none, label="0..1 (old)"];
  R17 [shape=diamond, label="关联段落"]; Reg -> R17 [dir=none, label="M"]; R17 -> Snap [dir=none, label="N"];
  r17a [shape=ellipse, label="paragraph_index"]; r17b [shape=ellipse, label="paragraph_hash"]; R17 -> {r17a r17b} [dir=none];
  R18 [shape=diamond, label="生成"];   Work -> R18 [dir=none, label="1"];  R18 -> Decl [dir=none, label="0..1"];
  R19 [shape=diamond, label="依据"];   Decl -> R19 [dir=none, label="N"];  R19 -> PolicyV [dir=none, label="1"];
}
```

## 第 3 页：Sprint 2 部分（画了轮廓，属性只写键）

```dot
digraph ER3 {
  graph [rankdir=LR, splines=true, nodesep=0.4, ranksep=0.6, fontname="Microsoft YaHei"];
  node  [fontname="Microsoft YaHei", fontsize=11];
  edge  [fontname="Microsoft YaHei", fontsize=10];

  Work   [shape=box, label="作业稿 Work"];
  Snap   [shape=box, peripheries=2, label="快照 Snapshot"];
  User   [shape=box, label="用户 AppUser"];
  Attr   [shape=box, peripheries=2, label="段落归因 ParagraphAttribution"];
  Export [shape=box, label="证据包 EvidenceExport"];
  Appeal [shape=box, label="申诉 Appeal"];
  Audit  [shape=box, label="审计日志 AuditLog"];

  x1 [shape=ellipse, label=<<u>paragraph_index</u>>]; x2 [shape=ellipse, label="origin"]; x3 [shape=ellipse, label="ai_ratio"];
  Attr -> {x1 x2 x3} [dir=none];
  y1 [shape=ellipse, label=<<u>export_id</u>>]; y2 [shape=ellipse, label="manifest_hash"]; y3 [shape=ellipse, label="exported_at"];
  Export -> {y1 y2 y3} [dir=none];
  z1 [shape=ellipse, label=<<u>appeal_id</u>>]; z2 [shape=ellipse, label="status"]; z3 [shape=ellipse, label="opened_at"]; z4 [shape=ellipse, label="closed_at"];
  Appeal -> {z1 z2 z3 z4} [dir=none];
  l1 [shape=ellipse, label=<<u>log_id</u>>]; l2 [shape=ellipse, label="action"]; l3 [shape=ellipse, label="object"]; l4 [shape=ellipse, label="at"];
  Audit -> {l1 l2 l3 l4} [dir=none];

  R20 [shape=diamond, peripheries=2, label="归因"]; Snap -> R20 [dir=none, label="1"]; R20 -> Attr [dir=none, label="N"];
  R21 [shape=diamond, label="导出"];   Work -> R21 [dir=none, label="1"]; R21 -> Export [dir=none, label="N"];
  R22 [shape=diamond, label="申诉"];   Work -> R22 [dir=none, label="1"]; R22 -> Appeal [dir=none, label="N"];
  R23 [shape=diamond, label="指派复核"]; Appeal -> R23 [dir=none, label="M"]; R23 -> User [dir=none, label="N"];
  r23a [shape=ellipse, label="granted_at"]; r23b [shape=ellipse, label="revoked_at"]; R23 -> {r23a r23b} [dir=none];
  R24 [shape=diamond, label="操作"];   User -> R24 [dir=none, label="1"]; R24 -> Audit [dir=none, label="N"];
}
```

## 实体表（与图一致）

| 实体                           | 键                             | 主要属性                                                                                                                        | 来源卡                |
| ------------------------------ | ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------- | --------------------- |
| AppUser                        | user_id                        | login_no（学号或工号）、name、role、password_hash、must_change_pwd、failed_attempts、locked_until                               | ACC-04、PERM-01       |
| Course                         | course_id                      | name、join_code（6 位）、created_at                                                                                             | ACC-01、ACC-02        |
| Assignment                     | assignment_id                  | title、deadline                                                                                                                 | ACC-01                |
| PolicyVersion                  | policy_version_id              | version_no、tier、grading_note、created_at                                                                                      | RULE-01、RULE-03、K-2 |
| PolicyScene（弱）              | policy_version_id + scene_name | allowed                                                                                                                         | RULE-01               |
| Work                           | work_id                        | current_text、char_count、pending_edit_chars、status、submitted_at、is_late                                                     | WRK-01、DECL-01       |
| Snapshot（弱）                 | work_id + seq_no               | trigger、char_count、is_keyframe、delta、full_text、content_hash、chain_hash、prev_chain_hash、created_at                       | WRK-02、EVD-01        |
| PasteEvent                     | paste_id                       | occurred_at、char_count、source、offset_start、offset_end                                                                       | WRK-03                |
| Registration                   | registration_id                | tool_version、stage、purpose、adoption、prompt_text、output_text、verification、status、chain_hash、prev_chain_hash、created_at | REG-01、REG-02        |
| AiTool                         | tool_id                        | name、is_preset                                                                                                                 | REG-01                |
| Declaration                    | declaration_id                 | kind、generated_at、is_late、content_json、content_hash                                                                         | DECL-01、DECL-02      |
| ParagraphAttribution（弱，S2） | snapshot_id + paragraph_index  | origin、ai_ratio                                                                                                                | TCH-04                |
| EvidenceExport（S2）           | export_id                      | manifest_hash、exported_at                                                                                                      | EVD-02                |
| Appeal（S2）                   | appeal_id                      | status、opened_at、closed_at                                                                                                    | APL-01                |
| AuditLog（S2）                 | log_id                         | action、object、at                                                                                                              | PERM-02               |

## 联系表

| 联系           | 参与实体                       | 基数     | 联系属性                        | 说明                                     |
| -------------- | ------------------------------ | -------- | ------------------------------- | ---------------------------------------- |
| 开设           | AppUser（教师）、Course        | 1 : N    |                                 |                                          |
| 选修           | AppUser（学生）、Course        | M : N    | joined_at                       | 落成 enrollment 表                       |
| 包含           | Course、Assignment             | 1 : N    |                                 |                                          |
| 制定           | Course、PolicyVersion          | 1 : N    |                                 | 每改一次多一行，不更新旧行               |
| 覆盖（S2）     | Assignment、PolicyVersion      | 1 : N    |                                 | 作业级规则                               |
| 含（标识）     | PolicyVersion、PolicyScene     | 1 : N    |                                 | 弱实体                                   |
| 确认阅读       | AppUser（学生）、PolicyVersion | M : N    | acked_at、assignment_id         | 落成 policy_ack 表，规则改版后要重新确认 |
| 撰写           | AppUser（学生）、Work          | 1 : N    |                                 | 与"收取"合起来：一人一作业一稿           |
| 收取           | Assignment、Work               | 1 : N    |                                 |                                          |
| 留存（标识）   | Work、Snapshot                 | 1 : N    |                                 | 弱实体，seq_no 从 1 递增                 |
| 基于           | Snapshot、Snapshot             | 1 : N    |                                 | 差分基于哪个快照，关键帧无 base          |
| 发生           | Work、PasteEvent               | 1 : N    |                                 |                                          |
| 归属           | PasteEvent、Registration       | N : 0..1 |                                 | 选了 AI 来源的粘贴对应一条登记           |
| 登记           | Work、Registration             | 1 : N    |                                 |                                          |
| 使用           | Registration、AiTool           | N : 0..1 | tool_name_custom                | 不在预置清单时用自定义名                 |
| 替代           | Registration、Registration     | 1 : 0..1 |                                 | 新记录指向被作废的旧记录                 |
| 关联段落       | Registration、Snapshot         | M : N    | paragraph_index、paragraph_hash | 落成 registration_paragraph 表           |
| 生成           | Work、Declaration              | 1 : 0..1 |                                 | 提交后才有                               |
| 依据           | Declaration、PolicyVersion     | N : 1    |                                 | 提交时生效的规则版本                     |
| 归因（S2）     | Snapshot、ParagraphAttribution | 1 : N    |                                 | 弱实体                                   |
| 导出（S2）     | Work、EvidenceExport           | 1 : N    |                                 |                                          |
| 申诉（S2）     | Work、Appeal                   | 1 : N    |                                 |                                          |
| 指派复核（S2） | Appeal、AppUser（复核员）      | M : N    | granted_at、revoked_at          | 落成 appeal_reviewer 表                  |
| 操作（S2）     | AppUser、AuditLog              | 1 : N    |                                 |                                          |

## 枚举值（两张图共用，杨子航写在第 3 页下方）

| 枚举          | 值                                                                                                         | 对应验收           |
| ------------- | ---------------------------------------------------------------------------------------------------------- | ------------------ |
| role          | STUDENT、TEACHER、REVIEWER、AFFAIRS、ADMIN                                                                 | PERM-01            |
| tier          | FORBID（禁止）、DECLARE（需声明）、ENCOURAGE（鼓励）                                                       | RULE-01            |
| work.status   | DRAFT、SUBMITTED                                                                                           | DECL-01-3          |
| trigger       | TIME、EDIT_VOLUME、IMPORT、SUBMIT                                                                          | WRK-02、WRK-04     |
| paste.source  | OWN_DOC、AI_TOOL、WEB、OTHER、PENDING                                                                      | WRK-03-1、WRK-03-4 |
| stage         | RESEARCH（查资料）、OUTLINE（大纲）、BODY（正文）、CODE（代码框架）、DATA（数据）、POLISH（改语法）、OTHER | REG-01             |
| adoption      | DIRECT、MODIFIED、REFERENCE、NOT_USED                                                                      | REG-01-5           |
| verification  | SOURCE_CHECK、RUN_TEST、TEXTBOOK、NONE                                                                     | REG-01-2           |
| reg.status    | ACTIVE、VOIDED                                                                                             | REG-02             |
| decl.kind     | AI_USED、NOT_USED                                                                                          | DECL-01、DECL-02   |
| attr.origin   | HUMAN、AI_PASTE、PASTE_MODIFIED                                                                            | TCH-04             |
| appeal.status | OPEN、CLOSED                                                                                               | APL-01             |

## 杨子航的批注

- 规则不更新只追加，所以是"规则版本"实体而不是"规则"实体。K-2 逼出来的。
- 快照和登记各有一条哈希链，prev_chain_hash 指向同一 work 下同类的上一条。第一条的 prev 是固定初始值。
- 没有任何"时长"属性。Snapshot.created_at 是时间戳，不是时长。
- 弱实体只有三个：场景、快照、段落归因。其余都能独立标识。
- 袁飞翔提的：PasteEvent 应该记 offset 才能在 Sprint 2 做归因，加了两个属性。
