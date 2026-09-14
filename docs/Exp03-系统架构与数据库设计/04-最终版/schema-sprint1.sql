-- ============================================================
-- 留痕：课程作业 AI 使用备案与过程证据链平台
-- 数据库脚本（人工优化版）Sprint 1
-- 优化：周浩然（work / snapshot / paste_event / registration / declaration）
--       杨子航（app_user / course / enrollment / assignment / policy_* / 触发器）
-- 验证：袁飞翔，MySQL 8.4，2026-09-14
-- 依据：01-手工原稿/ER图-Chen氏-手绘录入版.md；改动理由见 03-对比与修改记录/SQL优化对比记录.md
-- 原则：只追加不更新不删除；计数单位统一为字符；不存在任何时长字段
-- ============================================================

SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS liuhen
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE liuhen;

-- ------------------------------------------------------------
-- 账号（ACC）
-- ------------------------------------------------------------
CREATE TABLE app_user (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  login_no              VARCHAR(20)  NOT NULL COMMENT '学号或工号，统一认证接入后作为映射键',
  name                  VARCHAR(50)  NOT NULL,
  role                  ENUM('STUDENT','TEACHER','REVIEWER','AFFAIRS','ADMIN') NOT NULL,
  password_hash         CHAR(60)     NOT NULL COMMENT 'bcrypt 定长 60',
  must_change_password  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '名单导入生成的账号首次登录必须改密 AC-ACC-04-2',
  failed_attempts       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'AC-ACC-04-4',
  locked_until          DATETIME(3)  NULL,
  created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_login_no (login_no)
) ENGINE=InnoDB COMMENT='用户。不设 updated_at，改密与锁定由应用显式写字段';

CREATE TABLE course (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  join_code   CHAR(6) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '6 位数字加大写字母，比较不区分大小写 AC-ACC-02-4',
  teacher_id  BIGINT UNSIGNED NOT NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_join_code (join_code),
  KEY idx_course_teacher (teacher_id),
  CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES app_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='课程';

CREATE TABLE enrollment (
  course_id   BIGINT UNSIGNED NOT NULL,
  student_id  BIGINT UNSIGNED NOT NULL,
  joined_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (course_id, student_id),
  KEY idx_enrollment_student (student_id),
  CONSTRAINT fk_enroll_course  FOREIGN KEY (course_id)  REFERENCES course(id)   ON DELETE RESTRICT,
  CONSTRAINT fk_enroll_student FOREIGN KEY (student_id) REFERENCES app_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='选修。复合主键天然防重复加入 AC-ACC-02-3';

CREATE TABLE assignment (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  course_id   BIGINT UNSIGNED NOT NULL,
  title       VARCHAR(200) NOT NULL,
  deadline    DATETIME(3) NOT NULL COMMENT '精确到分即可，统一用 DATETIME(3)',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_assignment_course (course_id),
  CONSTRAINT fk_assignment_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='作业';

-- ------------------------------------------------------------
-- 规则（RULE）：版本化，只追加
-- ------------------------------------------------------------
CREATE TABLE policy_version (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  course_id            BIGINT UNSIGNED NOT NULL,
  scope_assignment_id  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0 = 课程级；非 0 = 作业级覆盖（Sprint 2 RULE-05）。用 0 而不用 NULL 是为了让唯一键生效',
  version_no           INT UNSIGNED NOT NULL,
  tier                 ENUM('FORBID','DECLARE','ENCOURAGE') NOT NULL,
  grading_note         VARCHAR(200) NULL COMMENT 'AC-RULE-03-1 上限 200 字符',
  created_by           BIGINT UNSIGNED NOT NULL,
  created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_policy_scope_version (course_id, scope_assignment_id, version_no),
  CONSTRAINT fk_policy_course  FOREIGN KEY (course_id)  REFERENCES course(id)   ON DELETE RESTRICT,
  CONSTRAINT fk_policy_creator FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='规则版本。每次修改插入新行，声明按提交时最新版本对照 AC-DECL-01-5，学生按版本确认 AC-RULE-01-6';

CREATE TABLE policy_scene (
  policy_version_id  BIGINT UNSIGNED NOT NULL,
  scene_name         VARCHAR(30) NOT NULL COMMENT 'AC-RULE-01-3 上限 30 字符',
  allowed            TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (policy_version_id, scene_name),
  CONSTRAINT fk_scene_policy FOREIGN KEY (policy_version_id) REFERENCES policy_version(id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='规则允许场景，弱实体，复合主键';

CREATE TABLE policy_ack (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  policy_version_id  BIGINT UNSIGNED NOT NULL,
  student_id         BIGINT UNSIGNED NOT NULL,
  assignment_id      BIGINT UNSIGNED NOT NULL,
  acked_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ack (policy_version_id, student_id, assignment_id),
  CONSTRAINT fk_ack_policy     FOREIGN KEY (policy_version_id) REFERENCES policy_version(id) ON DELETE RESTRICT,
  CONSTRAINT fk_ack_student    FOREIGN KEY (student_id)        REFERENCES app_user(id)       ON DELETE RESTRICT,
  CONSTRAINT fk_ack_assignment FOREIGN KEY (assignment_id)     REFERENCES assignment(id)     ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='学生对某规则版本的确认阅读。规则改版后无新行即需重新弹出 AC-RULE-02-2';

-- ------------------------------------------------------------
-- 写作区（WRK）
-- ------------------------------------------------------------
CREATE TABLE work (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  assignment_id       BIGINT UNSIGNED NOT NULL,
  student_id          BIGINT UNSIGNED NOT NULL,
  current_text        MEDIUMTEXT NULL COMMENT '当前文本，最长 50,000 字符 AC-WRK-01-4',
  char_count          INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '字符数，中英文一律按字符',
  pending_edit_chars  INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '自上一快照起累计增删字符，达 500 触发快照 AC-WRK-02-2',
  last_saved_at       DATETIME(3) NULL,
  status              ENUM('DRAFT','SUBMITTED') NOT NULL DEFAULT 'DRAFT',
  submitted_at        DATETIME(3) NULL,
  is_late             TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'AC-DECL-01-6',
  created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_work_assignment_student (assignment_id, student_id),
  KEY idx_work_student (student_id),
  CONSTRAINT fk_work_assignment FOREIGN KEY (assignment_id) REFERENCES assignment(id) ON DELETE RESTRICT,
  CONSTRAINT fk_work_student    FOREIGN KEY (student_id)    REFERENCES app_user(id)   ON DELETE RESTRICT,
  CONSTRAINT ck_work_char_count CHECK (char_count <= 50000)
) ENGINE=InnoDB COMMENT='作业稿，一人一作业一稿。没有任何时长字段（红线 不5 / AC-TCH-03-3）';

CREATE TABLE snapshot (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  work_id           BIGINT UNSIGNED NOT NULL,
  seq_no            INT UNSIGNED NOT NULL COMMENT '同一 work 内从 1 递增',
  trigger_type      ENUM('TIME','EDIT_VOLUME','IMPORT','SUBMIT') NOT NULL,
  char_count        INT UNSIGNED NOT NULL,
  is_keyframe       TINYINT(1) NOT NULL DEFAULT 0 COMMENT '每 10 个快照存一次全文，其余存差分 AC-WRK-02-5',
  base_snapshot_id  BIGINT UNSIGNED NULL COMMENT '差分基于的快照；关键帧为 NULL',
  delta             MEDIUMBLOB NULL COMMENT 'Myers 差分编码，DeltaCodec 手写',
  full_text         MEDIUMTEXT NULL,
  content_hash      CHAR(64) CHARACTER SET ascii NOT NULL COMMENT 'SHA-256(全文)',
  chain_hash        CHAR(64) CHARACTER SET ascii NOT NULL COMMENT 'SHA-256(prev_chain_hash || content_hash || created_at)',
  prev_chain_hash   CHAR(64) CHARACTER SET ascii NOT NULL COMMENT '首条为 64 个 0',
  created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_snapshot_work_seq (work_id, seq_no),
  KEY idx_snapshot_work_time (work_id, created_at),
  CONSTRAINT fk_snapshot_work FOREIGN KEY (work_id)          REFERENCES work(id)     ON DELETE RESTRICT,
  CONSTRAINT fk_snapshot_base FOREIGN KEY (base_snapshot_id) REFERENCES snapshot(id) ON DELETE RESTRICT,
  CONSTRAINT ck_snapshot_payload CHECK (
    (is_keyframe = 1 AND full_text IS NOT NULL AND base_snapshot_id IS NULL) OR
    (is_keyframe = 0 AND delta IS NOT NULL AND base_snapshot_id IS NOT NULL)
  )
) ENGINE=InnoDB COMMENT='版本快照，弱实体，只增不改不删 AC-WRK-02-4';

CREATE TABLE ai_tool (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name       VARCHAR(50) NOT NULL,
  is_preset  TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tool_name (name)
) ENGINE=InnoDB COMMENT='AI 工具字典，预置 8 个 AC-REG-01-4';

CREATE TABLE registration (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  work_id           BIGINT UNSIGNED NOT NULL,
  tool_id           BIGINT UNSIGNED NULL,
  tool_name_custom  VARCHAR(50) NULL COMMENT '不在字典中时填写',
  tool_version      VARCHAR(50) NULL COMMENT '可选，声明缺失时显示"未填写"',
  stage             ENUM('RESEARCH','OUTLINE','BODY','CODE','DATA','POLISH','OTHER') NOT NULL,
  purpose           VARCHAR(200) NOT NULL,
  adoption          ENUM('DIRECT','MODIFIED','REFERENCE','NOT_USED') NOT NULL COMMENT 'AC-REG-01-5',
  prompt_text       TEXT NULL COMMENT '上限 10,000 字符，应用层校验',
  output_text       TEXT NULL,
  verification      ENUM('SOURCE_CHECK','RUN_TEST','TEXTBOOK','NONE') NULL,
  status            ENUM('ACTIVE','VOIDED') NOT NULL DEFAULT 'ACTIVE',
  supersedes_id     BIGINT UNSIGNED NULL COMMENT '本条是对哪条的修改；旧条 status 置 VOIDED，链不断 AC-REG-02-1',
  chain_hash        CHAR(64) CHARACTER SET ascii NOT NULL,
  prev_chain_hash   CHAR(64) CHARACTER SET ascii NOT NULL,
  created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_reg_work_time (work_id, created_at),
  CONSTRAINT fk_reg_work       FOREIGN KEY (work_id)       REFERENCES work(id)         ON DELETE RESTRICT,
  CONSTRAINT fk_reg_tool       FOREIGN KEY (tool_id)       REFERENCES ai_tool(id)      ON DELETE RESTRICT,
  CONSTRAINT fk_reg_supersedes FOREIGN KEY (supersedes_id) REFERENCES registration(id) ON DELETE RESTRICT,
  CONSTRAINT ck_reg_tool CHECK (tool_id IS NOT NULL OR tool_name_custom IS NOT NULL)
) ENGINE=InnoDB COMMENT='AI 使用登记，追加式。没有 updated_at';

CREATE TABLE paste_event (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  work_id          BIGINT UNSIGNED NOT NULL,
  snapshot_id      BIGINT UNSIGNED NULL COMMENT '粘贴后首个快照，供 Sprint 2 归因',
  occurred_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  char_count       INT UNSIGNED NOT NULL COMMENT '超过 100 才记录 AC-WRK-03-1',
  source           ENUM('OWN_DOC','AI_TOOL','WEB','OTHER','PENDING') NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING = 点了稍后 AC-WRK-03-4',
  offset_start     INT UNSIGNED NOT NULL,
  offset_end       INT UNSIGNED NOT NULL,
  registration_id  BIGINT UNSIGNED NULL,
  PRIMARY KEY (id),
  KEY idx_paste_work_time (work_id, occurred_at),
  KEY idx_paste_work_source (work_id, source),
  CONSTRAINT fk_paste_work     FOREIGN KEY (work_id)         REFERENCES work(id)         ON DELETE RESTRICT,
  CONSTRAINT fk_paste_snapshot FOREIGN KEY (snapshot_id)     REFERENCES snapshot(id)     ON DELETE RESTRICT,
  CONSTRAINT fk_paste_reg      FOREIGN KEY (registration_id) REFERENCES registration(id) ON DELETE RESTRICT,
  CONSTRAINT ck_paste_offsets CHECK (offset_end > offset_start)
) ENGINE=InnoDB COMMENT='粘贴事件';

CREATE TABLE registration_paragraph (
  registration_id  BIGINT UNSIGNED NOT NULL,
  snapshot_id      BIGINT UNSIGNED NOT NULL,
  paragraph_index  INT UNSIGNED NOT NULL,
  paragraph_hash   CHAR(64) CHARACTER SET ascii NOT NULL COMMENT '段落文本哈希，原段删除后靠它显示"原段落已删除" AC-REG-03-2',
  PRIMARY KEY (registration_id, snapshot_id, paragraph_index),
  CONSTRAINT fk_rp_reg      FOREIGN KEY (registration_id) REFERENCES registration(id) ON DELETE RESTRICT,
  CONSTRAINT fk_rp_snapshot FOREIGN KEY (snapshot_id)     REFERENCES snapshot(id)     ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='登记与段落的关联，M:N 联系表';

-- ------------------------------------------------------------
-- 声明（DECL）
-- ------------------------------------------------------------
CREATE TABLE declaration (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  work_id            BIGINT UNSIGNED NOT NULL,
  kind               ENUM('AI_USED','NOT_USED') NOT NULL,
  policy_version_id  BIGINT UNSIGNED NOT NULL COMMENT '提交时生效的规则版本 AC-DECL-01-5',
  generated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  is_late            TINYINT(1) NOT NULL DEFAULT 0,
  content_json       JSON NOT NULL COMMENT '七要素逐条，结构化便于导出 PDF 与证据包',
  content_hash       CHAR(64) CHARACTER SET ascii NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_declaration_work (work_id),
  CONSTRAINT fk_decl_work   FOREIGN KEY (work_id)           REFERENCES work(id)           ON DELETE RESTRICT,
  CONSTRAINT fk_decl_policy FOREIGN KEY (policy_version_id) REFERENCES policy_version(id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='AI 使用声明或未使用承诺，一稿一份，提交后只读 AC-DECL-01-3';

-- ------------------------------------------------------------
-- 数据库层的"不可删、不可改"保险（应用层不提供接口，这里再挡一道）
-- ------------------------------------------------------------
DELIMITER $$

CREATE TRIGGER trg_snapshot_no_delete BEFORE DELETE ON snapshot FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'snapshot 不可删除（AC-WRK-02-4）';
END$$

CREATE TRIGGER trg_snapshot_no_update BEFORE UPDATE ON snapshot FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'snapshot 不可修改';
END$$

CREATE TRIGGER trg_registration_no_delete BEFORE DELETE ON registration FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'registration 不可删除，请作废（AC-REG-02-3）';
END$$

CREATE TRIGGER trg_registration_update_status_only BEFORE UPDATE ON registration FOR EACH ROW
BEGIN
  IF NEW.work_id <> OLD.work_id
     OR NOT (NEW.tool_id <=> OLD.tool_id)
     OR NOT (NEW.tool_name_custom <=> OLD.tool_name_custom)
     OR NOT (NEW.tool_version <=> OLD.tool_version)
     OR NEW.stage <> OLD.stage
     OR NEW.purpose <> OLD.purpose
     OR NEW.adoption <> OLD.adoption
     OR NOT (NEW.prompt_text <=> OLD.prompt_text)
     OR NOT (NEW.output_text <=> OLD.output_text)
     OR NOT (NEW.verification <=> OLD.verification)
     OR NOT (NEW.supersedes_id <=> OLD.supersedes_id)
     OR NEW.chain_hash <> OLD.chain_hash
     OR NEW.prev_chain_hash <> OLD.prev_chain_hash
     OR NEW.created_at <> OLD.created_at THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'registration 只允许把 status 置为 VOIDED，其余字段不可改';
  END IF;
END$$

CREATE TRIGGER trg_declaration_no_update BEFORE UPDATE ON declaration FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'declaration 提交后只读（AC-DECL-01-3）';
END$$

CREATE TRIGGER trg_declaration_no_delete BEFORE DELETE ON declaration FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'declaration 不可删除';
END$$

DELIMITER ;

-- ------------------------------------------------------------
-- 初始数据
-- ------------------------------------------------------------
INSERT INTO ai_tool (name, is_preset) VALUES
('ChatGPT',1),('Claude',1),('DeepSeek',1),('通义千问',1),('文心一言',1),('Kimi',1),('豆包',1),('GitHub Copilot',1);
