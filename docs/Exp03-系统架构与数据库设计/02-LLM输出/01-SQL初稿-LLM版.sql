-- ============================================================
-- 留痕：课程作业 AI 使用备案与过程证据链平台
-- 数据库建表脚本（LLM 初稿）
-- 生成工具：Claude Code（Claude Fable 5.1），2026-09-14
-- 目标数据库：MySQL 8.x
-- 说明：本脚本依据 Chen 氏 ER 图录入版生成，一次性包含全部实体。
-- ============================================================

CREATE DATABASE IF NOT EXISTS liuhen DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci;
USE liuhen;

-- ------------------------------------------------------------
-- 1. 用户表
-- ------------------------------------------------------------
CREATE TABLE `user` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
  `login_no`      VARCHAR(50) NOT NULL COMMENT '学号或工号',
  `name`          VARCHAR(50) NOT NULL COMMENT '姓名',
  `role`          VARCHAR(20) NOT NULL COMMENT '角色：学生/教师/复核员/教务/管理员',
  `password`      VARCHAR(255) NOT NULL COMMENT '密码',
  `created_at`    TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. 课程表
-- ------------------------------------------------------------
CREATE TABLE `course` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '课程ID',
  `name`          VARCHAR(100) NOT NULL COMMENT '课程名称',
  `code`          VARCHAR(10) NOT NULL COMMENT '课程码',
  `teacher_id`    BIGINT NOT NULL COMMENT '授课教师',
  `created_at`    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`teacher_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) COMMENT='课程表';

-- ------------------------------------------------------------
-- 3. 选课表
-- ------------------------------------------------------------
CREATE TABLE `enrollment` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `course_id`     BIGINT NOT NULL,
  `student_id`    BIGINT NOT NULL,
  `joined_at`     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`course_id`) REFERENCES `course`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`student_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) COMMENT='选课表（学生-课程多对多）';

-- ------------------------------------------------------------
-- 4. 作业表
-- ------------------------------------------------------------
CREATE TABLE `assignment` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `course_id`     BIGINT NOT NULL,
  `title`         VARCHAR(200) NOT NULL COMMENT '作业标题',
  `deadline`      TIMESTAMP NOT NULL COMMENT '截止时间',
  `created_at`    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`course_id`) REFERENCES `course`(`id`) ON DELETE CASCADE
) COMMENT='作业表';

-- ------------------------------------------------------------
-- 5. AI 使用规则表
-- ------------------------------------------------------------
CREATE TABLE `policy` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `course_id`     BIGINT NOT NULL,
  `assignment_id` BIGINT NULL COMMENT '不为空时为作业级规则',
  `tier`          VARCHAR(20) NOT NULL COMMENT '档位：禁止/需声明/鼓励',
  `grading_note`  VARCHAR(200) COMMENT '评分态度说明',
  `version`       INT DEFAULT 1 COMMENT '版本号',
  `created_at`    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (`course_id`) REFERENCES `course`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`assignment_id`) REFERENCES `assignment`(`id`) ON DELETE CASCADE
) COMMENT='AI 使用规则表';

-- ------------------------------------------------------------
-- 6. 规则场景表
-- ------------------------------------------------------------
CREATE TABLE `policy_scene` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `policy_id`     BIGINT NOT NULL,
  `scene_name`    VARCHAR(50) NOT NULL COMMENT '场景名称',
  `allowed`       TINYINT(1) DEFAULT 1 COMMENT '是否允许',
  FOREIGN KEY (`policy_id`) REFERENCES `policy`(`id`) ON DELETE CASCADE
) COMMENT='规则允许的场景';

-- ------------------------------------------------------------
-- 7. 规则确认表
-- ------------------------------------------------------------
CREATE TABLE `policy_ack` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `policy_id`     BIGINT NOT NULL,
  `student_id`    BIGINT NOT NULL,
  `assignment_id` BIGINT NOT NULL,
  `acked_at`      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`policy_id`) REFERENCES `policy`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`student_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`assignment_id`) REFERENCES `assignment`(`id`) ON DELETE CASCADE
) COMMENT='学生确认阅读规则';

-- ------------------------------------------------------------
-- 8. 作业稿表
-- ------------------------------------------------------------
CREATE TABLE `work` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `assignment_id`   BIGINT NOT NULL,
  `student_id`      BIGINT NOT NULL,
  `content`         LONGTEXT COMMENT '当前文本',
  `word_count`      INT DEFAULT 0 COMMENT '字数',
  `writing_minutes` INT DEFAULT 0 COMMENT '累计写作时长（分钟）',
  `status`          VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态',
  `submitted_at`    TIMESTAMP NULL,
  `created_at`      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (`assignment_id`) REFERENCES `assignment`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`student_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) COMMENT='学生作业稿';

-- ------------------------------------------------------------
-- 9. 快照表
-- ------------------------------------------------------------
CREATE TABLE `snapshot` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `work_id`       BIGINT NOT NULL,
  `seq_no`        INT NOT NULL COMMENT '序号',
  `trigger_type`  VARCHAR(20) COMMENT '触发方式',
  `content`       LONGTEXT NOT NULL COMMENT '快照全文',
  `word_count`    INT DEFAULT 0,
  `hash`          VARCHAR(255) COMMENT '内容哈希',
  `prev_hash`     VARCHAR(255) COMMENT '前一条哈希',
  `created_at`    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`work_id`) REFERENCES `work`(`id`) ON DELETE CASCADE
) COMMENT='版本快照';

-- ------------------------------------------------------------
-- 10. 粘贴事件表
-- ------------------------------------------------------------
CREATE TABLE `paste_event` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `work_id`         BIGINT NOT NULL,
  `occurred_at`     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `word_count`      INT DEFAULT 0 COMMENT '粘贴字数',
  `source`          VARCHAR(20) COMMENT '来源：自有文档/AI工具/网络/其他',
  `registration_id` BIGINT NULL,
  FOREIGN KEY (`work_id`) REFERENCES `work`(`id`) ON DELETE CASCADE
) COMMENT='粘贴事件';

-- ------------------------------------------------------------
-- 11. AI 工具表
-- ------------------------------------------------------------
CREATE TABLE `ai_tool` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name`          VARCHAR(50) NOT NULL,
  `is_preset`     TINYINT(1) DEFAULT 1
) COMMENT='AI 工具字典';

-- ------------------------------------------------------------
-- 12. AI 使用登记表
-- ------------------------------------------------------------
CREATE TABLE `registration` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `work_id`         BIGINT NOT NULL,
  `tool_id`         BIGINT NULL,
  `tool_name`       VARCHAR(50) COMMENT '自定义工具名',
  `tool_version`    VARCHAR(50),
  `stage`           VARCHAR(20) COMMENT '使用环节',
  `purpose`         VARCHAR(200) COMMENT '用途',
  `adoption`        VARCHAR(20) COMMENT '采用方式',
  `prompt_text`     TEXT COMMENT '提示词',
  `output_text`     TEXT COMMENT '输出',
  `verification`    VARCHAR(20) COMMENT '核对方式',
  `status`          VARCHAR(20) DEFAULT 'ACTIVE',
  `hash`            VARCHAR(255),
  `prev_hash`       VARCHAR(255),
  `created_at`      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (`work_id`) REFERENCES `work`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`tool_id`) REFERENCES `ai_tool`(`id`) ON DELETE SET NULL
) COMMENT='AI 使用登记';

ALTER TABLE `paste_event` ADD FOREIGN KEY (`registration_id`) REFERENCES `registration`(`id`) ON DELETE SET NULL;

-- ------------------------------------------------------------
-- 13. 登记关联段落表
-- ------------------------------------------------------------
CREATE TABLE `registration_paragraph` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `registration_id` BIGINT NOT NULL,
  `snapshot_id`     BIGINT NOT NULL,
  `paragraph_index` INT NOT NULL,
  FOREIGN KEY (`registration_id`) REFERENCES `registration`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`snapshot_id`) REFERENCES `snapshot`(`id`) ON DELETE CASCADE
) COMMENT='登记与段落的关联';

-- ------------------------------------------------------------
-- 14. 声明表
-- ------------------------------------------------------------
CREATE TABLE `declaration` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `work_id`       BIGINT NOT NULL,
  `kind`          VARCHAR(20) COMMENT '类型：使用/未使用',
  `content`       TEXT COMMENT '声明内容',
  `generated_at`  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`work_id`) REFERENCES `work`(`id`) ON DELETE CASCADE
) COMMENT='AI 使用声明';

-- ------------------------------------------------------------
-- 15. 段落归因表（Sprint 2）
-- ------------------------------------------------------------
CREATE TABLE `paragraph_attribution` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `snapshot_id`     BIGINT NOT NULL,
  `paragraph_index` INT NOT NULL,
  `origin`          VARCHAR(20) COMMENT '来源：人写/AI粘贴/粘贴后修改',
  `ai_ratio`        FLOAT DEFAULT 0 COMMENT 'AI 占比',
  FOREIGN KEY (`snapshot_id`) REFERENCES `snapshot`(`id`) ON DELETE CASCADE
) COMMENT='段落归因';

-- ------------------------------------------------------------
-- 16. 证据包导出表（Sprint 2）
-- ------------------------------------------------------------
CREATE TABLE `evidence_export` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `work_id`       BIGINT NOT NULL,
  `manifest_hash` VARCHAR(255),
  `exported_at`   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`work_id`) REFERENCES `work`(`id`) ON DELETE CASCADE
) COMMENT='证据包导出记录';

-- ------------------------------------------------------------
-- 17. 申诉表（Sprint 2）
-- ------------------------------------------------------------
CREATE TABLE `appeal` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `work_id`       BIGINT NOT NULL,
  `status`        VARCHAR(20) DEFAULT 'OPEN',
  `opened_at`     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `closed_at`     TIMESTAMP NULL,
  FOREIGN KEY (`work_id`) REFERENCES `work`(`id`) ON DELETE CASCADE
) COMMENT='申诉';

CREATE TABLE `appeal_reviewer` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `appeal_id`     BIGINT NOT NULL,
  `reviewer_id`   BIGINT NOT NULL,
  `granted_at`    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `revoked_at`    TIMESTAMP NULL,
  FOREIGN KEY (`appeal_id`) REFERENCES `appeal`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`reviewer_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) COMMENT='申诉复核员指派';

-- ------------------------------------------------------------
-- 18. 审计日志表（Sprint 2）
-- ------------------------------------------------------------
CREATE TABLE `audit_log` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `actor_id`      BIGINT,
  `action`        VARCHAR(50),
  `object_type`   VARCHAR(50),
  `object_id`     BIGINT,
  `ip`            VARCHAR(50),
  `created_at`    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`actor_id`) REFERENCES `user`(`id`) ON DELETE SET NULL
) COMMENT='审计日志';

-- ------------------------------------------------------------
-- 初始数据
-- ------------------------------------------------------------
INSERT INTO `ai_tool` (`name`) VALUES
('ChatGPT'), ('Claude'), ('DeepSeek'), ('通义千问'), ('文心一言'), ('Kimi'), ('豆包'), ('GitHub Copilot');
