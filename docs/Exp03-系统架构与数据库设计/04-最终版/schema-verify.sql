-- 验收用例：对优化版 schema 做数据层的边界与红线验证
-- 执行人：袁飞翔，2026-09-14，MySQL 8.4 独立测试实例
-- 每段一个断言，期望结果写在注释里；跑完对照 03-对比与修改记录/SQL优化对比记录.md 的验证表

SET NAMES utf8mb4;
USE liuhen;

-- 造最少的数据
INSERT INTO app_user (login_no, name, role, password_hash) VALUES
('T0001','T老师','TEACHER', REPEAT('x',60)),
('24020110','周浩然','STUDENT', REPEAT('x',60)),
('24020111','杨子航','STUDENT', REPEAT('x',60)),
('A0001','管理员','ADMIN', REPEAT('x',60));
INSERT INTO course (name, join_code, teacher_id) VALUES ('软件工程','A1B2C3',1);
INSERT INTO enrollment (course_id, student_id) VALUES (1,2);
INSERT INTO assignment (course_id, title, deadline) VALUES (1,'实验 1 报告','2026-09-21 08:00:00');
INSERT INTO policy_version (course_id, version_no, tier, grading_note, created_by) VALUES (1,1,'DECLARE','如实声明不扣分',1);
INSERT INTO policy_scene VALUES (1,'改语法',1),(1,'生成大纲',1),(1,'生成正文',0);
INSERT INTO work (assignment_id, student_id, current_text, char_count) VALUES (1,2,'第一段。',4);

-- 断言 1：课程码不区分大小写 AC-ACC-02-4 → 期望返回 1 行
SELECT '断言1 课程码大小写' AS t, COUNT(*) AS expect_1 FROM course WHERE join_code = 'a1b2c3';

-- 断言 2：同一人同一作业第二稿 → 期望 ERROR 1062 Duplicate
-- INSERT INTO work (assignment_id, student_id) VALUES (1,2);

-- 断言 3：重复加入课程 → 期望 ERROR 1062
-- INSERT INTO enrollment (course_id, student_id) VALUES (1,2);

-- 断言 4：关键帧快照可插入
INSERT INTO snapshot (work_id, seq_no, trigger_type, char_count, is_keyframe, full_text, content_hash, chain_hash, prev_chain_hash)
VALUES (1,1,'TIME',4,1,'第一段。', REPEAT('a',64), REPEAT('b',64), REPEAT('0',64));
-- 断言 5：差分快照可插入
INSERT INTO snapshot (work_id, seq_no, trigger_type, char_count, is_keyframe, base_snapshot_id, delta, content_hash, chain_hash, prev_chain_hash)
VALUES (1,2,'EDIT_VOLUME',10,0,1, X'00', REPEAT('c',64), REPEAT('d',64), REPEAT('b',64));
-- 断言 6：既没全文也没差分的快照 → 期望 ERROR 3819 Check constraint
-- INSERT INTO snapshot (work_id, seq_no, trigger_type, char_count, is_keyframe, content_hash, chain_hash, prev_chain_hash) VALUES (1,3,'TIME',1,0,REPEAT('e',64),REPEAT('f',64),REPEAT('d',64));

-- 断言 7：删除快照 → 期望 ERROR 1644 'snapshot 不可删除'
-- DELETE FROM snapshot WHERE id = 1;

-- 断言 8：登记最少字段
INSERT INTO registration (work_id, tool_id, stage, purpose, adoption, chain_hash, prev_chain_hash)
VALUES (1,1,'OUTLINE','让它列提纲','REFERENCE', REPEAT('1',64), REPEAT('0',64));
-- 断言 9：登记既无字典工具也无自定义名 → 期望 ERROR 3819
-- INSERT INTO registration (work_id, stage, purpose, adoption, chain_hash, prev_chain_hash) VALUES (1,'OUTLINE','x','REFERENCE',REPEAT('2',64),REPEAT('1',64));
-- 断言 10：作废旧登记（只改 status）→ 期望成功
UPDATE registration SET status = 'VOIDED' WHERE id = 1;
-- 断言 11：改登记正文 → 期望 ERROR 1644 '只允许把 status 置为 VOIDED'
-- UPDATE registration SET purpose = '改了' WHERE id = 1;

-- 断言 12：粘贴事件默认来源 PENDING AC-WRK-03-4
INSERT INTO paste_event (work_id, char_count, offset_start, offset_end) VALUES (1,120,0,120);
SELECT '断言12 粘贴默认待定' AS t, source AS expect_PENDING FROM paste_event WHERE id = 1;

-- 断言 13：声明一稿一份 AC-DECL-01-3
INSERT INTO declaration (work_id, kind, policy_version_id, content_json, content_hash)
VALUES (1,'AI_USED',1, JSON_OBJECT('items', JSON_ARRAY()), REPEAT('9',64));
-- 断言 14：改声明 → 期望 ERROR 1644 '提交后只读'
-- UPDATE declaration SET is_late = 1 WHERE id = 1;

-- 断言 15：全库没有任何"时长"类字段 AC-TCH-03-3 → 期望 0
SELECT '断言15 无时长字段' AS t, COUNT(*) AS expect_0
FROM information_schema.columns
WHERE table_schema = 'liuhen' AND (column_name LIKE '%minute%' OR column_name LIKE '%duration%' OR column_name LIKE '%elapsed%' OR column_comment LIKE '%时长%');

-- 断言 16：教务统计视图不含正文列 AC-PERM-01-4 → 期望 0
SELECT '断言16 统计视图无正文' AS t, COUNT(*) AS expect_0
FROM information_schema.columns
WHERE table_schema = 'liuhen' AND table_name LIKE 'v_affairs%' AND (column_name LIKE '%text%' OR column_name LIKE '%prompt%' OR column_name LIKE '%name%' AND column_name NOT IN ('course_name','tool_name'));

SELECT '断言17 统计视图可查' AS t, course_name, works_submitted, declarations_ai_used, current_tier FROM v_affairs_course_stat;
