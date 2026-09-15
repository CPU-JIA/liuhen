package cn.liuhen.policy;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 规则版本。只追加不更新（实验 3 对比记录第 2 条）。scopeAssignmentId 为 0 表示课程级。 */
@Entity
@Table(name = "policy_version")
public class PolicyVersion {

    public static final long COURSE_SCOPE = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "scope_assignment_id", nullable = false)
    private Long scopeAssignmentId = COURSE_SCOPE;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tier tier;

    @Column(name = "grading_note", length = 200)
    private String gradingNote;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PolicyVersion() {
    }

    public PolicyVersion(Long courseId, Long scopeAssignmentId, int versionNo, Tier tier, String gradingNote, Long createdBy) {
        this.courseId = courseId;
        this.scopeAssignmentId = scopeAssignmentId;
        this.versionNo = versionNo;
        this.tier = tier;
        this.gradingNote = gradingNote;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public Long getCourseId() { return courseId; }
    public Long getScopeAssignmentId() { return scopeAssignmentId; }
    public int getVersionNo() { return versionNo; }
    public Tier getTier() { return tier; }
    public String getGradingNote() { return gradingNote; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
