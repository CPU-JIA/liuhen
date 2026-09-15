package cn.liuhen.work;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 作业稿，一人一作业一稿。没有任何时长字段（红线 不5）。 */
@Entity
@Table(name = "work")
public class Work {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "current_text", columnDefinition = "MEDIUMTEXT")
    private String currentText;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(name = "pending_edit_chars", nullable = false)
    private int pendingEditChars;

    @Column(name = "last_saved_at")
    private LocalDateTime lastSavedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkStatus status = WorkStatus.DRAFT;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "is_late", nullable = false)
    private boolean late;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Work() {
    }

    public Work(Long assignmentId, Long studentId) {
        this.assignmentId = assignmentId;
        this.studentId = studentId;
        this.currentText = "";
    }

    public Long getId() { return id; }
    public Long getAssignmentId() { return assignmentId; }
    public Long getStudentId() { return studentId; }
    public String getCurrentText() { return currentText == null ? "" : currentText; }
    public int getCharCount() { return charCount; }
    public int getPendingEditChars() { return pendingEditChars; }
    public LocalDateTime getLastSavedAt() { return lastSavedAt; }
    public WorkStatus getStatus() { return status; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public boolean isLate() { return late; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void updateText(String text, int charCount, int addedEditChars, LocalDateTime at) {
        this.currentText = text;
        this.charCount = charCount;
        this.pendingEditChars += addedEditChars;
        this.lastSavedAt = at;
    }

    public void resetPendingEdits() {
        this.pendingEditChars = 0;
    }

    public void submit(LocalDateTime at, boolean late) {
        this.status = WorkStatus.SUBMITTED;
        this.submittedAt = at;
        this.late = late;
    }
}
