package cn.liuhen.account;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Enrollment.Key> {
    boolean existsByKeyCourseIdAndKeyStudentId(Long courseId, Long studentId);
}
