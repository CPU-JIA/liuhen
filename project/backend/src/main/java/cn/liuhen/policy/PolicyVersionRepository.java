package cn.liuhen.policy;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, Long> {
    Optional<PolicyVersion> findFirstByCourseIdAndScopeAssignmentIdOrderByVersionNoDesc(Long courseId, Long scopeAssignmentId);

    List<PolicyVersion> findByCourseIdAndScopeAssignmentIdOrderByVersionNoAsc(Long courseId, Long scopeAssignmentId);
}
