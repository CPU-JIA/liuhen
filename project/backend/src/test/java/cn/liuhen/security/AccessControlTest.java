package cn.liuhen.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import cn.liuhen.TestSupport;
import cn.liuhen.account.AppUser;
import cn.liuhen.account.Assignment;
import cn.liuhen.account.AssignmentRepository;
import cn.liuhen.account.Course;
import cn.liuhen.account.CourseRepository;
import cn.liuhen.account.EnrollmentRepository;
import cn.liuhen.account.Role;
import cn.liuhen.common.ApiExceptionHandler.ForbiddenException;
import cn.liuhen.common.ApiExceptionHandler.NotFoundException;
import cn.liuhen.security.AccessControl.Action;
import cn.liuhen.work.Work;

/** 权限矩阵与对象归属（PERM-01 全部五条，设计说明第 6 节）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessControlTest {

    @Mock
    private CourseRepository courses;
    @Mock
    private AssignmentRepository assignments;
    @Mock
    private EnrollmentRepository enrollments;

    private AccessControl access;

    private final AppUser teacherA = user(1L, "T0001", Role.TEACHER);
    private final AppUser teacherB = user(2L, "T0002", Role.TEACHER);
    private final AppUser studentA = user(3L, "24020110", Role.STUDENT);
    private final AppUser studentB = user(4L, "24020111", Role.STUDENT);
    private final AppUser reviewer = user(5L, "R0001", Role.REVIEWER);
    private final AppUser affairs = user(6L, "J0001", Role.AFFAIRS);
    private final AppUser admin = user(7L, "A0001", Role.ADMIN);

    private final Course course = TestSupport.withId(new Course("软件工程", "ABC234", 1L), 100L);
    private final Assignment assignment = TestSupport.withId(new Assignment(100L, "实验 1", LocalDateTime.of(2026, 10, 1, 0, 0)), 200L);
    private final Work workOfA = TestSupport.withId(new Work(200L, 3L), 300L);

    private static AppUser user(long id, String loginNo, Role role) {
        return TestSupport.withId(new AppUser(loginNo, loginNo, role, "hash", false), id);
    }

    @BeforeEach
    void setUp() {
        access = new AccessControl(courses, assignments, enrollments);
        when(courses.findById(100L)).thenReturn(Optional.of(course));
        when(assignments.findById(200L)).thenReturn(Optional.of(assignment));
        when(enrollments.existsByKeyCourseIdAndKeyStudentId(100L, 3L)).thenReturn(true);
    }

    @Test
    void matrixMatchesDesignDocument() {
        Map<Role, Set<Action>> expected = Map.of(
                Role.STUDENT, EnumSet.of(Action.JOIN_COURSE, Action.EDIT_WORK, Action.VIEW_WORK, Action.VIEW_TIMELINE),
                Role.TEACHER, EnumSet.of(Action.MANAGE_COURSE, Action.CONFIGURE_POLICY, Action.VIEW_WORK, Action.VIEW_TIMELINE),
                Role.REVIEWER, EnumSet.of(Action.VIEW_WORK, Action.VIEW_TIMELINE),
                Role.AFFAIRS, EnumSet.of(Action.VIEW_STATS),
                Role.ADMIN, EnumSet.of(Action.MANAGE_COURSE, Action.VIEW_STATS));
        for (Role role : Role.values()) {
            for (Action action : Action.values()) {
                assertEquals(expected.get(role).contains(action), access.can(role, action), role + " × " + action);
            }
        }
    }

    @Test
    void requireThrowsForbiddenWithoutExplanation() {
        ForbiddenException e = assertThrows(ForbiddenException.class, () -> access.require(studentA, Action.MANAGE_COURSE));
        assertEquals("没有权限", e.getMessage());
    }

    @Test
    void teacherOwnsOnlyOwnCourse() {
        assertSame(course, access.requireCourseOwner(teacherA, 100L));
        assertThrows(ForbiddenException.class, () -> access.requireCourseOwner(teacherB, 100L));
    }

    @Test
    void adminManagesAnyCourseButStudentNone() {
        assertSame(course, access.requireCourseOwner(admin, 100L));
        assertThrows(ForbiddenException.class, () -> access.requireCourseOwner(studentA, 100L));
    }

    @Test
    void missingCourseIsNotFoundForTeacher() {
        assertThrows(NotFoundException.class, () -> access.requireCourseOwner(teacherA, 999L));
    }

    @Test
    void onlyTheCourseTeacherConfiguresPolicy() {
        assertSame(course, access.requirePolicyOwner(teacherA, 100L));
        assertThrows(ForbiddenException.class, () -> access.requirePolicyOwner(teacherB, 100L));
        assertThrows(ForbiddenException.class, () -> access.requirePolicyOwner(admin, 100L), "管理员不配规则，规则是老师的事");
    }

    @Test
    void studentSeesOwnWorkOnly() {
        assertDoesNotThrow(() -> access.requireWorkVisible(studentA, workOfA));
        assertThrows(ForbiddenException.class, () -> access.requireWorkVisible(studentB, workOfA));
    }

    @Test
    void teacherSeesWorkInOwnCourseOnly() {
        assertDoesNotThrow(() -> access.requireWorkVisible(teacherA, workOfA));
        assertThrows(ForbiddenException.class, () -> access.requireWorkVisible(teacherB, workOfA));
    }

    @Test
    void reviewerIsRefusedInSprintOne() {
        assertThrows(ForbiddenException.class, () -> access.requireWorkVisible(reviewer, workOfA));
    }

    @Test
    void affairsNeverSeesWorkText() {
        assertThrows(ForbiddenException.class, () -> access.requireWorkVisible(affairs, workOfA));
        assertTrue(access.can(Role.AFFAIRS, Action.VIEW_STATS));
    }

    @Test
    void adminDoesNotReadStudentWork() {
        assertFalse(access.can(Role.ADMIN, Action.VIEW_WORK));
        assertThrows(ForbiddenException.class, () -> access.requireWorkVisible(admin, workOfA));
    }

    @Test
    void onlyTheAuthorEdits() {
        assertDoesNotThrow(() -> access.requireWorkEditable(studentA, workOfA));
        assertThrows(ForbiddenException.class, () -> access.requireWorkEditable(studentB, workOfA));
        assertThrows(ForbiddenException.class, () -> access.requireWorkEditable(teacherA, workOfA));
    }

    @Test
    void studentMustBeEnrolledToOpenAssignment() {
        assertSame(assignment, access.requireEnrolledAssignment(studentA, 200L));
        assertThrows(ForbiddenException.class, () -> access.requireEnrolledAssignment(studentB, 200L));
    }

    @Test
    void teacherOpensAssignmentsOfOwnCourseOnly() {
        assertSame(assignment, access.requireEnrolledAssignment(teacherA, 200L));
        assertThrows(ForbiddenException.class, () -> access.requireEnrolledAssignment(teacherB, 200L));
    }

    @Test
    void otherRolesCannotOpenAssignments() {
        assertThrows(ForbiddenException.class, () -> access.requireEnrolledAssignment(affairs, 200L));
        assertThrows(ForbiddenException.class, () -> access.requireEnrolledAssignment(reviewer, 200L));
    }

    @Test
    void missingAssignmentIsNotFound() {
        assertThrows(NotFoundException.class, () -> access.requireEnrolledAssignment(studentA, 999L));
    }

    @Test
    void workWhoseAssignmentVanishedIsForbiddenNotServerError() {
        Work orphan = TestSupport.withId(new Work(999L, 3L), 301L);
        assertThrows(ForbiddenException.class, () -> access.requireWorkVisible(teacherA, orphan));
    }
}
