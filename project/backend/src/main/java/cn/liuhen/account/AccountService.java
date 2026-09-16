package cn.liuhen.account;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.liuhen.common.ApiExceptionHandler.BadRequestException;
import cn.liuhen.common.ApiExceptionHandler.ConflictException;
import cn.liuhen.common.ApiExceptionHandler.ForbiddenException;
import cn.liuhen.common.ApiExceptionHandler.NotFoundException;
import cn.liuhen.common.LiuhenProperties;
import cn.liuhen.security.AccessControl;
import cn.liuhen.security.JwtService;

/** 账号、课程、选课、作业、名单导入。锁定与首次改密见 AC-ACC-04。 */
@Service
public class AccountService {

    public record LoginResult(String token, Long userId, String name, Role role, boolean mustChangePassword) { }

    public record RosterResult(int imported, List<String> loginNos) { }

    /** 课程码字母表去掉 0、O、1、I，避免学生输错（走查第 11 条）。 */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    /** 与建表脚本的列宽一致；超长在这里拒绝，不让它变成数据库异常的 500（实验 6 单测发现）。 */
    private static final int COURSE_NAME_MAX = 100;
    private static final int ASSIGNMENT_TITLE_MAX = 200;

    private final AppUserRepository users;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final AssignmentRepository assignments;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AccessControl access;
    private final LiuhenProperties.Account cfg;

    public AccountService(AppUserRepository users, CourseRepository courses, EnrollmentRepository enrollments,
                          AssignmentRepository assignments, PasswordEncoder encoder, JwtService jwt, AccessControl access,
                          LiuhenProperties props) {
        this.users = users;
        this.courses = courses;
        this.enrollments = enrollments;
        this.assignments = assignments;
        this.encoder = encoder;
        this.jwt = jwt;
        this.access = access;
        this.cfg = props.account();
    }

    // ---------------------------------------------------------------- 登录

    /**
     * 连续输错 N 次锁定 M 分钟（AC-ACC-04-4）。锁定期间即使密码正确也拒绝，不透露账号是否存在。
     * 密码错误时抛出的异常不能回滚事务，否则失败计数永远写不进去；冒烟测试第一次跑出来的就是这个 bug。
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public LoginResult login(String loginNo, String password, LocalDateTime now) {
        AppUser user = users.findByLoginNo(loginNo).orElseThrow(() -> new BadRequestException("账号或密码不正确"));
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            // 剩余时间向上取整：剩 4 分 30 秒说 5 分钟；原来的 toMinutes() + 1 在整分时会多报 1 分钟（实验 6 审查）
            long minutes = (java.time.Duration.between(now, user.getLockedUntil()).toSeconds() + 59) / 60;
            throw new BadRequestException("账号已锁定，请 " + minutes + " 分钟后再试");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            int failures = user.getFailedAttempts() + 1;
            user.setFailedAttempts(failures);
            if (failures >= cfg.lockAfterFailures()) {
                user.setLockedUntil(now.plusMinutes(cfg.lockMinutes()));
                user.setFailedAttempts(0);
                throw new BadRequestException("账号已锁定，请 " + cfg.lockMinutes() + " 分钟后再试");
            }
            throw new BadRequestException("账号或密码不正确");
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        return new LoginResult(jwt.issue(user.getId(), user.getRole()), user.getId(), user.getName(), user.getRole(), user.isMustChangePassword());
    }

    @Transactional
    public void changePassword(AppUser actor, String oldPassword, String newPassword) {
        if (!encoder.matches(oldPassword, actor.getPasswordHash())) {
            throw new BadRequestException("原密码不正确");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new BadRequestException("新密码至少 8 位");
        }
        actor.setPasswordHash(encoder.encode(newPassword));
        actor.setMustChangePassword(false);
        // actor 是控制器在事务外查出来的脱管实体，只改字段不会写库：新密码与"已改密"标记都丢了，
        // 学生下次登录仍用旧密码、仍被要求改密。冒烟脚本只看了 200 没再登录，实验 6 集成测试才抓到
        users.save(actor);
    }

    // ---------------------------------------------------------------- 课程与作业

    @Transactional
    public Course createCourse(AppUser actor, String name) {
        access.require(actor, AccessControl.Action.MANAGE_COURSE);
        if (name == null || name.isBlank()) {
            throw new BadRequestException("课程名不能为空");
        }
        if (name.trim().codePointCount(0, name.trim().length()) > COURSE_NAME_MAX) {
            throw new BadRequestException("课程名不超过 " + COURSE_NAME_MAX + " 字符");
        }
        String code;
        do {
            code = randomCode();
        } while (courses.findByJoinCode(code).isPresent());
        return courses.save(new Course(name.trim(), code, actor.getId()));
    }

    public List<Course> myCourses(AppUser actor) {
        return switch (actor.getRole()) {
            case TEACHER -> courses.findByTeacherIdOrderByCreatedAtDesc(actor.getId());
            case STUDENT -> courses.findEnrolled(actor.getId());
            case ADMIN -> courses.findAll();
            default -> throw new ForbiddenException();
        };
    }

    @Transactional
    public Course join(AppUser actor, String joinCode) {
        access.require(actor, AccessControl.Action.JOIN_COURSE);
        if (joinCode == null || joinCode.isBlank()) {
            throw new BadRequestException("请输入课程码");
        }
        Course course = courses.findByJoinCode(joinCode.trim().toUpperCase())
                .orElseThrow(() -> new NotFoundException("课程码不存在"));
        if (enrollments.existsByKeyCourseIdAndKeyStudentId(course.getId(), actor.getId())) {
            throw new ConflictException("已在课程中");
        }
        enrollments.save(new Enrollment(course.getId(), actor.getId()));
        return course;
    }

    @Transactional
    public Assignment createAssignment(AppUser actor, Long courseId, String title, LocalDateTime deadline, LocalDateTime now) {
        access.requireCourseOwner(actor, courseId);
        if (title == null || title.isBlank()) {
            throw new BadRequestException("作业名不能为空");
        }
        if (title.trim().codePointCount(0, title.trim().length()) > ASSIGNMENT_TITLE_MAX) {
            throw new BadRequestException("作业名不超过 " + ASSIGNMENT_TITLE_MAX + " 字符");
        }
        if (deadline == null || !deadline.isAfter(now)) {
            throw new BadRequestException("截止时间不能早于现在");
        }
        return assignments.save(new Assignment(courseId, title.trim(), deadline));
    }

    public List<Assignment> assignmentsOf(AppUser actor, Long courseId) {
        Course course = courses.findById(courseId).orElseThrow(() -> new NotFoundException("课程不存在"));
        boolean teacher = course.getTeacherId().equals(actor.getId());
        boolean enrolled = enrollments.existsByKeyCourseIdAndKeyStudentId(courseId, actor.getId());
        if (!teacher && !enrolled && actor.getRole() != Role.ADMIN) {
            throw new ForbiddenException();
        }
        return assignments.findByCourseIdOrderByDeadlineAsc(courseId);
    }

    // ---------------------------------------------------------------- 名单导入

    /**
     * 名单两列：学号、姓名。支持 csv 与 xlsx。重复学号整份拒绝（AC-ACC-04-3）。
     * 已存在的账号只加入课程不重建；新账号初始密码为学号后 6 位，首次登录必须改密（AC-ACC-04-1、04-2）。
     */
    @Transactional
    public RosterResult importRoster(AppUser actor, Long courseId, String filename, InputStream in) throws IOException {
        Course course = access.requireCourseOwner(actor, courseId);
        List<String[]> rows = filename != null && filename.toLowerCase().endsWith(".xlsx") ? parseXlsx(in) : parseCsv(in);
        if (rows.isEmpty()) {
            throw new BadRequestException("名单为空");
        }
        Set<String> seen = new HashSet<>();
        List<Integer> duplicates = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String no = rows.get(i)[0];
            if (!seen.add(no)) {
                duplicates.add(i + 1);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new BadRequestException("名单中学号重复，行号：" + duplicates);
        }
        List<String> imported = new ArrayList<>();
        for (String[] row : rows) {
            String loginNo = row[0];
            String name = row[1];
            AppUser user = users.findByLoginNo(loginNo).orElseGet(() -> {
                String initial = loginNo.length() > 6 ? loginNo.substring(loginNo.length() - 6) : loginNo;
                return users.save(new AppUser(loginNo, name, Role.STUDENT, encoder.encode(initial), true));
            });
            if (!enrollments.existsByKeyCourseIdAndKeyStudentId(course.getId(), user.getId())) {
                enrollments.save(new Enrollment(course.getId(), user.getId()));
            }
            imported.add(loginNo);
        }
        return new RosterResult(imported.size(), imported);
    }

    static List<String[]> parseCsv(InputStream in) throws IOException {
        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        List<String[]> rows = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] cells = line.split("[,\\t，]");
            if (cells.length < 2) {
                throw new BadRequestException("名单每行需要学号和姓名两列：" + line);
            }
            String no = cells[0].trim();
            String name = cells[1].trim();
            if (no.isEmpty() || name.isEmpty()) {
                throw new BadRequestException("学号或姓名为空：" + line);
            }
            if (no.equals("学号") || no.equalsIgnoreCase("login_no")) {
                continue;
            }
            rows.add(new String[] {no, name});
        }
        return rows;
    }

    static List<String[]> parseXlsx(InputStream in) throws IOException {
        List<String[]> rows = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                Cell c0 = row.getCell(0);
                Cell c1 = row.getCell(1);
                if (c0 == null || c1 == null) {
                    continue;
                }
                String no = fmt.formatCellValue(c0).trim();
                String name = fmt.formatCellValue(c1).trim();
                if (no.isEmpty() || name.isEmpty() || no.equals("学号")) {
                    continue;
                }
                rows.add(new String[] {no, name});
            }
        }
        return rows;
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
