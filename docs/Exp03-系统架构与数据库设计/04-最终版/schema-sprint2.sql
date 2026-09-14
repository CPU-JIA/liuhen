-- ============================================================
-- 留痕：数据库脚本（人工优化版）Sprint 2 增量
-- 在 schema-sprint1.sql 之后执行。实验 8 重估后可能调整。
-- 优化：周浩然（paragraph_attribution）、杨子航（evidence / appeal / audit / setting / 统计视图）
-- ============================================================

SET NAMES utf8mb4;
USE liuhen;

-- 段落归因（TCH-04），弱实体，复合主键
CREATE TABLE paragraph_attribution (
  snapshot_id      BIGINT UNSIGNED NOT NULL,
  paragraph_index  INT UNSIGNED NOT NULL,
  origin           ENUM('HUMAN','AI_PASTE','PASTE_MODIFIED') NOT NULL,
  ai_ratio         DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '0.00 到 100.00，衰减规则见 AttributionService',
  PRIMARY KEY (snapshot_id, paragraph_index),
  CONSTRAINT fk_attr_snapshot FOREIGN KEY (snapshot_id) REFERENCES snapshot(id) ON DELETE RESTRICT,
  CONSTRAINT ck_attr_ratio CHECK (ai_ratio BETWEEN 0 AND 100)
) ENGINE=InnoDB COMMENT='段落来源归因，随快照生成，只增';

-- 证据包导出（EVD-02）
CREATE TABLE evidence_export (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  work_id          BIGINT UNSIGNED NOT NULL,
  exported_by      BIGINT UNSIGNED NOT NULL,
  exported_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  snapshot_head    CHAR(64) CHARACTER SET ascii NOT NULL COMMENT '导出时快照链链头',
  registration_head CHAR(64) CHARACTER SET ascii NOT NULL COMMENT '导出时登记链链头',
  manifest_hash    CHAR(64) CHARACTER SET ascii NOT NULL COMMENT 'manifest.json 的 SHA-256',
  PRIMARY KEY (id),
  KEY idx_export_work (work_id, exported_at),
  CONSTRAINT fk_export_work FOREIGN KEY (work_id)     REFERENCES work(id)     ON DELETE RESTRICT,
  CONSTRAINT fk_export_user FOREIGN KEY (exported_by) REFERENCES app_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='证据包导出记录';

-- 申诉（APL-01）
CREATE TABLE appeal (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  work_id     BIGINT UNSIGNED NOT NULL,
  student_id  BIGINT UNSIGNED NOT NULL,
  export_id   BIGINT UNSIGNED NULL COMMENT '随申诉提交的证据包',
  reason      VARCHAR(500) NOT NULL,
  status      ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',
  opened_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  closed_at   DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY idx_appeal_work (work_id),
  CONSTRAINT fk_appeal_work    FOREIGN KEY (work_id)    REFERENCES work(id)            ON DELETE RESTRICT,
  CONSTRAINT fk_appeal_student FOREIGN KEY (student_id) REFERENCES app_user(id)        ON DELETE RESTRICT,
  CONSTRAINT fk_appeal_export  FOREIGN KEY (export_id)  REFERENCES evidence_export(id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='申诉';

-- 申诉复核员指派（APL-02），复核员权限随申诉开闭 AC-PERM-01-3
CREATE TABLE appeal_reviewer (
  appeal_id    BIGINT UNSIGNED NOT NULL,
  reviewer_id  BIGINT UNSIGNED NOT NULL,
  granted_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  revoked_at   DATETIME(3) NULL COMMENT '申诉关闭 30 天后回收',
  PRIMARY KEY (appeal_id, reviewer_id),
  CONSTRAINT fk_ar_appeal   FOREIGN KEY (appeal_id)   REFERENCES appeal(id)   ON DELETE RESTRICT,
  CONSTRAINT fk_ar_reviewer FOREIGN KEY (reviewer_id) REFERENCES app_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='申诉与复核员，M:N';

-- 审计日志（PERM-02），C4 上线条件
CREATE TABLE audit_log (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  actor_id     BIGINT UNSIGNED NOT NULL,
  action       ENUM('VIEW_TIMELINE','VIEW_DIFF','EXPORT_EVIDENCE','VERIFY_EVIDENCE','CHANGE_POLICY','OPEN_APPEAL','GRANT_REVIEWER','IMPORT_ROSTER') NOT NULL,
  object_type  ENUM('WORK','SNAPSHOT','POLICY_VERSION','APPEAL','COURSE') NOT NULL,
  object_id    BIGINT UNSIGNED NOT NULL,
  ip           VARCHAR(45) NULL COMMENT 'IPv6 最长 45',
  occurred_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_audit_actor_time (actor_id, occurred_at),
  KEY idx_audit_object (object_type, object_id),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES app_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='操作审计，保留 3 年';

-- 系统设置（PERM-03 数据保留期限等）
CREATE TABLE app_setting (
  setting_key    VARCHAR(50) NOT NULL,
  setting_value  VARCHAR(200) NOT NULL,
  updated_by     BIGINT UNSIGNED NOT NULL,
  updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (setting_key),
  CONSTRAINT fk_setting_user FOREIGN KEY (updated_by) REFERENCES app_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='系统设置';

-- 教务统计视图（STAT-01）：只有计数，没有任何正文、提示词、姓名 AC-PERM-01-4
CREATE OR REPLACE VIEW v_affairs_course_stat AS
SELECT
  c.id                                            AS course_id,
  c.name                                          AS course_name,
  COUNT(DISTINCT w.id)                            AS works_submitted,
  SUM(d.kind = 'AI_USED')                         AS declarations_ai_used,
  SUM(d.kind = 'NOT_USED')                        AS declarations_not_used,
  (SELECT pv.tier FROM policy_version pv
     WHERE pv.course_id = c.id AND pv.scope_assignment_id = 0
     ORDER BY pv.version_no DESC LIMIT 1)         AS current_tier
FROM course c
LEFT JOIN assignment a ON a.course_id = c.id
LEFT JOIN work w       ON w.assignment_id = a.id AND w.status = 'SUBMITTED'
LEFT JOIN declaration d ON d.work_id = w.id
GROUP BY c.id, c.name;

CREATE OR REPLACE VIEW v_affairs_tool_stat AS
SELECT
  a.course_id,
  COALESCE(t.name, r.tool_name_custom) AS tool_name,
  COUNT(*)                             AS registrations
FROM registration r
JOIN work w        ON w.id = r.work_id
JOIN assignment a  ON a.id = w.assignment_id
LEFT JOIN ai_tool t ON t.id = r.tool_id
WHERE r.status = 'ACTIVE'
GROUP BY a.course_id, COALESCE(t.name, r.tool_name_custom);

INSERT INTO app_setting (setting_key, setting_value, updated_by)
SELECT 'retention_after_graduation_years', '1', id FROM app_user WHERE role = 'ADMIN' ORDER BY id LIMIT 1;
