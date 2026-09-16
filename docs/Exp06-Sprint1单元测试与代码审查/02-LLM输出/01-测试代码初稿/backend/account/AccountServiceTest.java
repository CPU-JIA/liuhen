package cn.liuhen.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import cn.liuhen.TestSupport;
import cn.liuhen.common.ApiExceptionHandler.BadRequestException;
import cn.liuhen.common.ApiExceptionHandler.ConflictException;
import cn.liuhen.common.ApiExceptionHandler.NotFoundException;
import cn.liuhen.security.AccessControl;
import cn.liuhen.security.JwtService;

/** 登录锁定、改密、进课、发作业、名单导入（ACC-01、ACC-02、ACC-04）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 8, 0);

    @Mock
    private AppUserRepository users;
    @Mock
    private CourseRepository courses;
    @Mock
    private EnrollmentRepository enrollments;
    @Mock
    private AssignmentRepository assignments;
    @Mock
    private AccessControl access;

    /** bcrypt 一次几十毫秒，几十条登录用例会拖慢整个套件；这里用可逆的假编码器，只验证逻辑。 */
    private final PasswordEncoder encoder = new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
            return "enc:" + raw;
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            return ("enc:" + raw).equals(encoded);
        }
    };

    private final JwtService jwt = new JwtService(TestSupport.props());
    private AccountService service;
    private AppUser student;
    private AppUser teacher;
    private Course course;

    @BeforeEach
    void setUp() {
        service = new AccountService(users, courses, enrollments, assignments, encoder, jwt, access, TestSupport.props());
        student = TestSupport.withId(new AppUser("24020110", "周浩然", Role.STUDENT, encoder.encode("020110"), true), 10L);
        teacher = TestSupport.withId(new AppUser("T0001", "T老师", Role.TEACHER, encoder.encode("Liuhen@2026"), false), 1L);
        course = TestSupport.withId(new Course("软件工程", "ABC234", 1L), 100L);
        when(users.findByLoginNo("24020110")).thenReturn(Optional.of(student));
        when(users.findByLoginNo("T0001")).thenReturn(Optional.of(teacher));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(courses.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(enrollments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(assignments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(access.requireCourseOwner(teacher, 100L)).thenReturn(course);
    }

    // ---------------------------------------------------------------- 登录与锁定 AC-ACC-04-4

    @Test
    void unknownAccountAndWrongPasswordGiveTheSameMessage() {
        when(users.findByLoginNo("nobody")).thenReturn(Optional.empty());
        BadRequestException unknown = assertThrows(BadRequestException.class, () -> service.login("nobody", "x", NOW));
        BadRequestException wrong = assertThrows(BadRequestException.class, () -> service.login("24020110", "x", NOW));
        assertEquals(unknown.getMessage(), wrong.getMessage(), "不能让人试出账号是否存在");
    }

    @Test
    void wrongPasswordCountsUp() {
        assertThrows(BadRequestException.class, () -> service.login("24020110", "wrong", NOW));
        assertEquals(1, student.getFailedAttempts());
        assertNull(student.getLockedUntil());
    }

    @Test
    void fourFailuresDoNotLock() {
        student.setFailedAttempts(3);
        assertThrows(BadRequestException.class, () -> service.login("24020110", "wrong", NOW));
        assertEquals(4, student.getFailedAttempts());
        assertNull(student.getLockedUntil());
    }

    @Test
    void fifthFailureLocksForFifteenMinutesAndResetsCounter() {
        student.setFailedAttempts(4);
        BadRequestException e = assertThrows(BadRequestException.class, () -> service.login("24020110", "wrong", NOW));
        assertTrue(e.getMessage().contains("15 分钟"));
        assertEquals(NOW.plusMinutes(15), student.getLockedUntil());
        assertEquals(0, student.getFailedAttempts());
    }

    @Test
    void lockedAccountRejectsEvenTheRightPassword() {
        student.setLockedUntil(NOW.plusMinutes(5));
        BadRequestException e = assertThrows(BadRequestException.class, () -> service.login("24020110", "020110", NOW));
        assertTrue(e.getMessage().contains("锁定"));
        assertTrue(e.getMessage().contains("6 分钟"), "剩余时间向上取整：" + e.getMessage());
    }

    @Test
    void lockEndsExactlyAtLockedUntil() {
        student.setLockedUntil(NOW);
        AccountService.LoginResult r = service.login("24020110", "020110", NOW);
        assertEquals(10L, r.userId());
        assertNull(student.getLockedUntil());
    }

    @Test
    void successResetsCounterAndIssuesUsableToken() {
        student.setFailedAttempts(3);
        AccountService.LoginResult r = service.login("24020110", "020110", NOW);
        assertEquals(0, student.getFailedAttempts());
        assertTrue(r.mustChangePassword());
        assertEquals(Role.STUDENT, jwt.parse(r.token()).orElseThrow().role());
    }

    // ---------------------------------------------------------------- 改密 AC-ACC-04-2

    @Test
    void changePasswordNeedsTheOldOne() {
        assertThrows(BadRequestException.class, () -> service.changePassword(student, "wrong", "Student@2026"));
        assertTrue(student.isMustChangePassword());
    }

    @Test
    void newPasswordMustBeAtLeastEightChars() {
        assertThrows(BadRequestException.class, () -> service.changePassword(student, "020110", "1234567"));
        assertThrows(BadRequestException.class, () -> service.changePassword(student, "020110", null));
        service.changePassword(student, "020110", "12345678");
        assertTrue(encoder.matches("12345678", student.getPasswordHash()));
        assertFalse(student.isMustChangePassword());
    }

    // ---------------------------------------------------------------- 课程与作业 ACC-01、ACC-02

    @Test
    void courseCodeHasSixUnambiguousChars() {
        when(courses.findByJoinCode(any())).thenReturn(Optional.empty());
        Course c = service.createCourse(teacher, "  软件工程 ");
        assertEquals("软件工程", c.getName());
        assertTrue(c.getJoinCode().matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}"), c.getJoinCode());
    }

    @Test
    void courseCodeIsRegeneratedWhenTaken() {
        when(courses.findByJoinCode(any())).thenReturn(Optional.of(course)).thenReturn(Optional.empty());
        service.createCourse(teacher, "软件工程");
        verify(courses, org.mockito.Mockito.times(2)).findByJoinCode(any());
    }

    @Test
    void courseNameIsRequiredAndBounded() {
        assertThrows(BadRequestException.class, () -> service.createCourse(teacher, "  "));
        assertThrows(BadRequestException.class, () -> service.createCourse(teacher, "课".repeat(101)), "超过 100 字符应被拒，而不是打到数据库报 500");
    }

    @Test
    void joinNormalizesCaseAndTrims() {
        when(courses.findByJoinCode("ABC234")).thenReturn(Optional.of(course));
        when(enrollments.existsByKeyCourseIdAndKeyStudentId(100L, 10L)).thenReturn(false);
        assertEquals(course, service.join(student, " abc234 "));
        verify(enrollments).save(any());
    }

    @Test
    void joinUnknownCodeIsNotFound() {
        when(courses.findByJoinCode(any())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.join(student, "ZZZZZZ"));
        assertThrows(BadRequestException.class, () -> service.join(student, " "));
    }

    @Test
    void joiningTwiceIsAConflict() {
        when(courses.findByJoinCode("ABC234")).thenReturn(Optional.of(course));
        when(enrollments.existsByKeyCourseIdAndKeyStudentId(100L, 10L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.join(student, "ABC234"));
        verify(enrollments, never()).save(any());
    }

    @Test
    void assignmentDeadlineMustBeAfterNow() {
        assertThrows(BadRequestException.class, () -> service.createAssignment(teacher, 100L, "实验 1", NOW, NOW));
        assertThrows(BadRequestException.class, () -> service.createAssignment(teacher, 100L, "实验 1", null, NOW));
        Assignment a = service.createAssignment(teacher, 100L, " 实验 1 ", NOW.plusSeconds(1), NOW);
        assertEquals("实验 1", a.getTitle());
    }

    @Test
    void assignmentTitleIsRequiredAndBounded() {
        assertThrows(BadRequestException.class, () -> service.createAssignment(teacher, 100L, " ", NOW.plusDays(1), NOW));
        assertThrows(BadRequestException.class, () -> service.createAssignment(teacher, 100L, "题".repeat(201), NOW.plusDays(1), NOW));
    }

    @Test
    void myCoursesDependsOnRole() {
        when(courses.findByTeacherIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(course));
        when(courses.findEnrolled(10L)).thenReturn(List.of());
        assertEquals(List.of(course), service.myCourses(teacher));
        assertEquals(List.of(), service.myCourses(student));
    }

    // ---------------------------------------------------------------- 名单导入 AC-ACC-04-1、04-3

    private static InputStream csv(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void csvWithBomHeaderChineseCommaAndTabIsAccepted() throws IOException {
        when(users.findByLoginNo(any())).thenReturn(Optional.empty());
        List<AppUser> saved = new ArrayList<>();
        when(users.save(any())).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            saved.add(u);
            return TestSupport.withId(u, 500L + saved.size());
        });
        AccountService.RosterResult r = service.importRoster(teacher, 100L, "名单.csv",
                csv("﻿学号,姓名\n24020112，丁浩然\n24020113\t孟甲\n\n24020114 , 袁飞翔 \n"));
        assertEquals(3, r.imported());
        assertEquals(List.of("24020112", "24020113", "24020114"), r.loginNos());
        assertEquals(3, saved.size());
        assertEquals("丁浩然", saved.get(0).getName());
        assertTrue(encoder.matches("020112", saved.get(0).getPasswordHash()), "初始密码是学号后 6 位");
        assertTrue(saved.get(0).isMustChangePassword());
        assertEquals(Role.STUDENT, saved.get(0).getRole());
        verify(enrollments, org.mockito.Mockito.times(3)).save(any());
    }

    @Test
    void duplicateNumbersRejectTheWholeFileWithRowNumbers() {
        when(users.findByLoginNo(any())).thenReturn(Optional.empty());
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.importRoster(teacher, 100L, "名单.csv", csv("24020112,丁浩然\n24020113,孟甲\n24020112,重复\n")));
        assertTrue(e.getMessage().contains("3"), e.getMessage());
        verify(users, never()).save(any());
        verify(enrollments, never()).save(any());
    }

    @Test
    void existingAccountIsOnlyEnrolledNotRewritten() throws IOException {
        when(enrollments.existsByKeyCourseIdAndKeyStudentId(100L, 10L)).thenReturn(false);
        service.importRoster(teacher, 100L, "名单.csv", csv("24020110,另一个名字\n"));
        verify(users, never()).save(any());
        assertEquals("周浩然", student.getName(), "已有账号以库里为准");
        assertTrue(encoder.matches("020110", student.getPasswordHash()), "已有密码不重置");
        verify(enrollments).save(any());
    }

    @Test
    void alreadyEnrolledStudentIsNotEnrolledTwice() throws IOException {
        when(enrollments.existsByKeyCourseIdAndKeyStudentId(100L, 10L)).thenReturn(true);
        AccountService.RosterResult r = service.importRoster(teacher, 100L, "名单.csv", csv("24020110,周浩然\n"));
        assertEquals(1, r.imported());
        verify(enrollments, never()).save(any());
    }

    @Test
    void emptyOrOneColumnRosterIsRejected() {
        assertThrows(BadRequestException.class, () -> service.importRoster(teacher, 100L, "名单.csv", csv("\n\n")));
        assertThrows(BadRequestException.class, () -> service.importRoster(teacher, 100L, "名单.csv", csv("24020112\n")));
        assertThrows(BadRequestException.class, () -> service.importRoster(teacher, 100L, "名单.csv", csv("24020112, \n")));
    }

    @Test
    void headerOnlyLineDoesNotBreakParsing() throws IOException {
        List<String[]> rows = AccountService.parseCsv(csv("学号\n24020112,丁浩然\n"));
        assertEquals(1, rows.size());
    }

    @Test
    void shortLoginNoUsesItselfAsInitialPassword() throws IOException {
        when(users.findByLoginNo(any())).thenReturn(Optional.empty());
        List<AppUser> saved = new ArrayList<>();
        when(users.save(any())).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            saved.add(u);
            return TestSupport.withId(u, 600L);
        });
        service.importRoster(teacher, 100L, "名单.csv", csv("S01,短学号\n"));
        assertTrue(encoder.matches("S01", saved.get(0).getPasswordHash()));
    }

    @Test
    void xlsxWithNumericStudentNumbersIsParsed() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("名单");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(24020115d);
            row.createCell(1).setCellValue("张家森");
            var blank = sheet.createRow(2);
            blank.createCell(0).setCellValue("");
            wb.write(out);
        }
        List<String[]> rows = AccountService.parseXlsx(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(1, rows.size());
        assertEquals("24020115", rows.get(0)[0], "数字单元格不能变成 2.4020115E7");
        assertEquals("张家森", rows.get(0)[1]);
    }

    @Test
    void rosterImportRequiresCourseOwner() {
        when(access.requireCourseOwner(any(), anyLong())).thenThrow(new cn.liuhen.common.ApiExceptionHandler.ForbiddenException());
        assertThrows(cn.liuhen.common.ApiExceptionHandler.ForbiddenException.class,
                () -> service.importRoster(student, 100L, "名单.csv", csv("24020112,丁浩然\n")));
    }
}
