package cn.liuhen.account;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 选修。复合主键天然防重复加入（AC-ACC-02-3）。 */
@Entity
@Table(name = "enrollment")
public class Enrollment {

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "course_id")
        private Long courseId;
        @Column(name = "student_id")
        private Long studentId;

        protected Key() {
        }

        public Key(Long courseId, Long studentId) {
            this.courseId = courseId;
            this.studentId = studentId;
        }

        public Long getCourseId() { return courseId; }
        public Long getStudentId() { return studentId; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(courseId, k.courseId) && Objects.equals(studentId, k.studentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(courseId, studentId);
        }
    }

    @EmbeddedId
    private Key key;

    @Column(name = "joined_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime joinedAt;

    protected Enrollment() {
    }

    public Enrollment(Long courseId, Long studentId) {
        this.key = new Key(courseId, studentId);
    }

    public Key getKey() { return key; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
}
