package cn.liuhen.security;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import cn.liuhen.account.AppUser;
import cn.liuhen.account.Assignment;
import cn.liuhen.account.AssignmentRepository;
import cn.liuhen.account.Course;
import cn.liuhen.account.CourseRepository;
import cn.liuhen.account.EnrollmentRepository;
import cn.liuhen.account.Role;
import cn.liuhen.common.ApiExceptionHandler.ForbiddenException;
import cn.liuhen.common.ApiExceptionHandler.NotFoundException;
import cn.liuhen.work.Work;

/**
 * 四层权限（PERM-01），实现设计说明第 6 节的矩阵。
 * <p>
 * 先按角色查矩阵，再按对象查归属："本人课程" = course.teacherId 等于当前用户，"本人" = work.studentId 等于当前用户。
 * 复核员的条件放行依赖 Sprint 2 的申诉表，Sprint 1 一律拒绝（AC-PERM-01-3）。所有拒绝都是 403，不解释原因。
 */
@Service
public class AccessControl {

    public enum Action {
        MANAGE_COURSE, CONFIGURE_POLICY, JOIN_COURSE, EDIT_WORK, VIEW_WORK, VIEW_TIMELINE, VIEW_STATS
    }

    private static final Map<Role, Set<Action>> MATRIX = new EnumMap<>(Role.class);

    static {
        MATRIX.put(Role.STUDENT, EnumSet.of(Action.JOIN_COURSE, Action.EDIT_WORK, Action.VIEW_WORK, Action.VIEW_TIMELINE));
        MATRIX.put(Role.TEACHER, EnumSet.of(Action.MANAGE_COURSE, Action.CONFIGURE_POLICY, Action.VIEW_WORK, Action.VIEW_TIMELINE));
        MATRIX.put(Role.REVIEWER, EnumSet.of(Action.VIEW_WORK, Action.VIEW_TIMELINE));
        MATRIX.put(Role.AFFAIRS, EnumSet.of(Action.VIEW_STATS));
        MATRIX.put(Role.ADMIN, EnumSet.of(Action.MANAGE_COURSE, Action.VIEW_STATS));
    }

    private final CourseRepository courses;
    private final AssignmentRepository assignments;
    private final EnrollmentRepository enrollments;

    public AccessControl(CourseRepository courses, AssignmentRepository assignments, EnrollmentRepository enrollments) {
        this.courses = courses;
        this.assignments = assignments;
        this.enrollments = enrollments;
    }

    public boolean can(Role role, Action action) {
        return MATRIX.getOrDefault(role, EnumSet.noneOf(Action.class)).contains(action);
    }

    public void require(AppUser actor, Action action) {
        if (!can(actor.getRole(), action)) {
            throw new ForbiddenException();
        }
    }

    /** 教师只能管本人课程；管理员可以管全部。 */
    public Course requireCourseOwner(AppUser actor, Long courseId) {
        require(actor, Action.MANAGE_COURSE);
        Course course = courses.findById(courseId).orElseThrow(() -> new NotFoundException("课程不存在"));
        if (actor.getRole() == Role.TEACHER && !course.getTeacherId().equals(actor.getId())) {
            throw new ForbiddenException();
        }
        return course;
    }

    public Course requirePolicyOwner(AppUser actor, Long courseId) {
        require(actor, Action.CONFIGURE_POLICY);
        Course course = courses.findById(courseId).orElseThrow(() -> new NotFoundException("课程不存在"));
        if (!course.getTeacherId().equals(actor.getId())) {
            throw new ForbiddenException();
        }
        return course;
    }

    /** 学生看本人的稿；教师看本人课程下的稿；复核员 Sprint 1 一律拒绝。 */
    public void requireWorkVisible(AppUser actor, Work work) {
        require(actor, Action.VIEW_WORK);
        switch (actor.getRole()) {
            case STUDENT -> {
                if (!work.getStudentId().equals(actor.getId())) {
                    throw new ForbiddenException();
                }
            }
            case TEACHER -> {
                Assignment assignment = assignments.findById(work.getAssignmentId()).orElseThrow(ForbiddenException::new);
                Course course = courses.findById(assignment.getCourseId()).orElseThrow(ForbiddenException::new);
                if (!course.getTeacherId().equals(actor.getId())) {
                    throw new ForbiddenException();
                }
            }
            default -> throw new ForbiddenException();
        }
    }

    /** 只有本人能编辑自己的稿。 */
    public void requireWorkEditable(AppUser actor, Work work) {
        require(actor, Action.EDIT_WORK);
        if (!work.getStudentId().equals(actor.getId())) {
            throw new ForbiddenException();
        }
    }

    /** 学生必须已加入课程才能进入作业。 */
    public Assignment requireEnrolledAssignment(AppUser actor, Long assignmentId) {
        Assignment assignment = assignments.findById(assignmentId).orElseThrow(() -> new NotFoundException("作业不存在"));
        if (actor.getRole() == Role.STUDENT) {
            if (!enrollments.existsByKeyCourseIdAndKeyStudentId(assignment.getCourseId(), actor.getId())) {
                throw new ForbiddenException();
            }
            return assignment;
        }
        if (actor.getRole() == Role.TEACHER) {
            Course course = courses.findById(assignment.getCourseId()).orElseThrow(ForbiddenException::new);
            if (!course.getTeacherId().equals(actor.getId())) {
                throw new ForbiddenException();
            }
            return assignment;
        }
        throw new ForbiddenException();
    }
}
