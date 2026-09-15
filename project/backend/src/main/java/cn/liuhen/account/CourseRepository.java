package cn.liuhen.account;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /** join_code 列的排序规则是 ascii_general_ci，数据库层已不区分大小写（AC-ACC-02-4）。 */
    Optional<Course> findByJoinCode(String joinCode);

    List<Course> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);

    @Query("select c from Course c where c.id in (select e.key.courseId from Enrollment e where e.key.studentId = :studentId) order by c.createdAt desc")
    List<Course> findEnrolled(@Param("studentId") Long studentId);
}
