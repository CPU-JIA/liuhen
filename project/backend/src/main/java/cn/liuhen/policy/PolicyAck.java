package cn.liuhen.policy;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "policy_ack")
public class PolicyAck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_version_id", nullable = false)
    private Long policyVersionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "acked_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime ackedAt;

    protected PolicyAck() {
    }

    public PolicyAck(Long policyVersionId, Long studentId, Long assignmentId) {
        this.policyVersionId = policyVersionId;
        this.studentId = studentId;
        this.assignmentId = assignmentId;
    }

    public Long getId() { return id; }
    public Long getPolicyVersionId() { return policyVersionId; }
    public Long getStudentId() { return studentId; }
    public Long getAssignmentId() { return assignmentId; }
    public LocalDateTime getAckedAt() { return ackedAt; }
}
