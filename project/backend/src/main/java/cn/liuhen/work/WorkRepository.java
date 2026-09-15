package cn.liuhen.work;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkRepository extends JpaRepository<Work, Long> {
    Optional<Work> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    List<Work> findByAssignmentIdOrderByStudentIdAsc(Long assignmentId);
}
