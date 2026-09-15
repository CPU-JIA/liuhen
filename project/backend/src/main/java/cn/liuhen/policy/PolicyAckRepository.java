package cn.liuhen.policy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyAckRepository extends JpaRepository<PolicyAck, Long> {
    boolean existsByPolicyVersionIdAndStudentIdAndAssignmentId(Long policyVersionId, Long studentId, Long assignmentId);
}
