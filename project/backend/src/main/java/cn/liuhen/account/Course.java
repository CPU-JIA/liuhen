package cn.liuhen.account;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "course")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "join_code", nullable = false, length = 6)
    private String joinCode;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Course() {
    }

    public Course(String name, String joinCode, Long teacherId) {
        this.name = name;
        this.joinCode = joinCode;
        this.teacherId = teacherId;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getJoinCode() { return joinCode; }
    public Long getTeacherId() { return teacherId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
