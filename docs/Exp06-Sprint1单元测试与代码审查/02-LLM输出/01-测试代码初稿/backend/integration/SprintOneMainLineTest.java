package cn.liuhen.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import cn.liuhen.account.AccountService;
import cn.liuhen.account.AppUser;
import cn.liuhen.account.AppUserRepository;
import cn.liuhen.account.Assignment;
import cn.liuhen.account.AssignmentRepository;
import cn.liuhen.account.Course;
import cn.liuhen.account.CourseRepository;
import cn.liuhen.account.EnrollmentRepository;
import cn.liuhen.account.Role;
import cn.liuhen.common.ApiExceptionHandler.BadRequestException;
import cn.liuhen.common.ApiExceptionHandler.ConflictException;
import cn.liuhen.declaration.Declaration;
import cn.liuhen.declaration.DeclarationKind;
import cn.liuhen.declaration.DeclarationRepository;
import cn.liuhen.declaration.DeclarationService;
import cn.liuhen.policy.PolicyAckRepository;
import cn.liuhen.policy.PolicySceneRepository;
import cn.liuhen.policy.PolicyService;
import cn.liuhen.policy.PolicyVersionRepository;
import cn.liuhen.policy.Tier;
import cn.liuhen.registration.Adoption;
import cn.liuhen.registration.AiToolRepository;
import cn.liuhen.registration.Registration;
import cn.liuhen.registration.RegistrationRepository;
import cn.liuhen.registration.RegistrationService;
import cn.liuhen.registration.Stage;
import cn.liuhen.timeline.TimelineAssembler;
import cn.liuhen.work.PasteEvent;
import cn.liuhen.work.PasteEventRepository;
import cn.liuhen.work.PasteSource;
import cn.liuhen.work.Snapshot;
import cn.liuhen.work.SnapshotRepository;
import cn.liuhen.work.SnapshotService;
import cn.liuhen.work.SnapshotTrigger;
import cn.liuhen.work.Work;
import cn.liuhen.work.WorkRepository;
import cn.liuhen.work.WorkService;
import cn.liuhen.work.WorkStatus;

/**
 * 对着真 MySQL 跑的集成测试。只在设置了 LIUHEN_IT_DB_URL 时运行（本机临时实例，见 project/README.md）。
 * 实验 5 关掉了 Hibernate 结构校验，说好"靠集成测试兜底"，兜底就是这里：Flyway 建表、实体映射、触发器、跨事务的锁定与改密。
 * 每个用例自己造一套课程与账号，学号带时间戳，同一个库可以反复跑。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LIUHEN_IT_DB_URL", matches = ".+")
class SprintOneMainLineTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("LIUHEN_IT_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("LIUHEN_IT_DB_USER", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("LIUHEN_IT_DB_PASSWORD", ""));
    }

    @Autowired AccountService accounts;
    @Autowired PolicyService policies;
    @Autowired WorkService works;
    @Autowired RegistrationService registrations;
    @Autowired DeclarationService declarations;
    @Autowired SnapshotService snapshots;
    @Autowired TimelineAssembler timeline;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @Autowired AppUserRepository users;
    @Autowired CourseRepository courses;
    @Autowired EnrollmentRepository enrollments;
    @Autowired AssignmentRepository assignments;
    @Autowired PolicyVersionRepository policyVersions;
    @Autowired PolicySceneRepository policyScenes;
    @Autowired PolicyAckRepository policyAcks;
    @Autowired WorkRepository workRepo;
    @Autowired SnapshotRepository snapshotRepo;
    @Autowired AiToolRepository aiTools;
    @Autowired RegistrationRepository registrationRepo;
    @Autowired PasteEventRepository pasteEvents;
    @Autowired DeclarationRepository declarationRepo;

    /** 一套课程：老师、课程、作业、一个已导入的学生。 */
    private record Scenario(AppUser teacher, Course course, Assignment assignment, String studentNo, String initialPassword) { }

    private Scenario scenario() throws Exception {
        String run = Long.toString(System.nanoTime() % 100_000_000L);
        AppUser teacher = users.save(new AppUser("T" + run, "T老师", Role.TEACHER, encoder.encode("Liuhen@2026"), false));
        Course course = accounts.createCourse(teacher, "软件工程 " + run);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Assignment assignment = accounts.createAssignment(teacher, course.getId(), "实验 1 报告", now.plusDays(7), now);
        String studentNo = "S" + run;
        String csv = "学号,姓名\n" + studentNo + ",周浩然\n";
        accounts.importRoster(teacher, course.getId(), "名单.csv", new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        policies.publish(teacher, course.getId(), Tier.DECLARE, "如实声明不扣分",
                List.of(new PolicyService.SceneInput("改语法", true), new PolicyService.SceneInput("生成正文", false)));
        String initial = studentNo.substring(studentNo.length() - 6);
        return new Scenario(teacher, course, assignment, studentNo, initial);
    }

    private AppUser student(Scenario s) {
        return users.findByLoginNo(s.studentNo()).orElseThrow();
    }

    @Test
    void flywayBuiltTheSchemaAndEveryEntityMapsToIt() {
        Integer migrations = jdbc.queryForObject("select count(*) from flyway_schema_history where success = 1", Integer.class);
        assertNotNull(migrations);
        assertTrue(migrations >= 1);
        // 每个仓库查一次全表：列名对不上会在这里报 SQL 错，这就是关掉 ddl-auto validate 的代价与兜底
        users.findAll();
        courses.findAll();
        enrollments.findAll();
        assignments.findAll();
        policyVersions.findAll();
        policyScenes.findAll();
        policyAcks.findAll();
        workRepo.findAll();
        snapshotRepo.findAll();
        registrationRepo.findAll();
        pasteEvents.findAll();
        declarationRepo.findAll();
        assertTrue(aiTools.findAllByOrderByIdAsc().size() >= 8, "AC-REG-01-4 预置至少 8 个工具");
    }

    @Test
    void changedPasswordIsWhatTheNextLoginChecks() throws Exception {
        Scenario s = scenario();
        LocalDateTime now = LocalDateTime.now();
        AccountService.LoginResult first = accounts.login(s.studentNo(), s.initialPassword(), now);
        assertTrue(first.mustChangePassword(), "AC-ACC-04-2 首次登录必须改密");
        // 控制器里当前用户是在事务外查出来的，改密方法拿到的是脱管实体
        accounts.changePassword(student(s), s.initialPassword(), "Student@2026");
        AccountService.LoginResult second = accounts.login(s.studentNo(), "Student@2026", now);
        assertFalse(second.mustChangePassword());
        assertThrows(BadRequestException.class, () -> accounts.login(s.studentNo(), s.initialPassword(), now), "旧密码必须失效");
    }

    @Test
    void lockoutSurvivesTheFailedLoginTransactions() throws Exception {
        Scenario s = scenario();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 5; i++) {
            assertThrows(BadRequestException.class, () -> accounts.login(s.studentNo(), "wrong", now));
        }
        BadRequestException locked = assertThrows(BadRequestException.class, () -> accounts.login(s.studentNo(), s.initialPassword(), now));
        assertTrue(locked.getMessage().contains("锁定"), "AC-ACC-04-4：" + locked.getMessage());
        assertNotNull(student(s).getLockedUntil(), "锁定时间必须真的落库，实验 5 那个回滚 bug 的回归");
    }

    @Test
    void mainLineFromRulesToDeclarationAndTimeline() throws Exception {
        Scenario s = scenario();
        AppUser student = student(s);
        LocalDateTime t = LocalDateTime.now().withNano(0);

        assertThrows(ConflictException.class, () -> works.enter(student, s.assignment().getId()), "AC-RULE-02-3 未确认规则不能进");
        policies.ack(student, s.assignment());
        Work work = works.enter(student, s.assignment().getId());
        Long workId = work.getId();

        WorkService.SaveResult save1 = works.save(student, workId, "第一段。", t);
        assertTrue(save1.snapshotCreated(), "首次保存落第 1 版");
        WorkService.SaveResult save2 = works.save(student, workId, "第一段。" + "第二段内容。".repeat(120), t.plusSeconds(5));
        assertTrue(save2.snapshotCreated(), "AC-WRK-02-2 改动超 500 字符立即落版本");
        assertEquals(2, save2.snapshotSeq());

        PasteEvent paste = works.recordPaste(student, workId, 150, 4, 154, null, t.plusSeconds(6)).orElseThrow();
        assertThrows(ConflictException.class, () -> declarations.submit(student, workId, false, t.plusSeconds(7)), "AC-DECL-01-2 待定粘贴阻断");
        works.resolvePaste(student, paste.getId(), PasteSource.WEB, null);

        Registration reg = registrations.create(student, workId, new RegistrationService.Input(null, "校内模型", "v1", Stage.BODY,
                "让它写了第二段", Adoption.MODIFIED, "写一段", "第二段内容。", null), t.plusSeconds(8));
        assertEquals(64, reg.getChainHash().length());

        Declaration d = declarations.submit(student, workId, false, t.plusSeconds(9));
        assertEquals(DeclarationKind.AI_USED, d.getKind());
        assertTrue(d.getContentJson().contains("\"exceedsPolicy\":true"), "AC-DECL-01-4 生成正文未被允许要标注");

        Work sealed = workRepo.findById(workId).orElseThrow();
        assertEquals(WorkStatus.SUBMITTED, sealed.getStatus());
        assertThrows(ConflictException.class, () -> works.save(student, workId, "改", t.plusSeconds(10)), "AC-DECL-01-3 提交后只读");

        List<Snapshot> versions = snapshots.listOf(workId);
        assertEquals(SnapshotTrigger.SUBMIT, versions.get(versions.size() - 1).getTrigger());
        assertEquals(sealed.getCurrentText(), snapshots.reconstruct(versions.get(versions.size() - 1)));
        assertFalse(snapshots.lineDiff(workId, 1, 2).isEmpty());

        List<TimelineAssembler.Item> studentView = timeline.build(student, workId);
        List<TimelineAssembler.Item> teacherView = timeline.build(s.teacher(), workId);
        assertEquals(studentView, teacherView, "AC-WRK-05-2 两端一致");
        assertEquals(List.of("PASTE", "REGISTRATION", "SNAPSHOT"),
                studentView.stream().map(TimelineAssembler.Item::type).distinct().sorted().toList());
    }

    @Test
    void databaseRefusesDeletingSnapshotsAndRewritingRegistrations() throws Exception {
        Scenario s = scenario();
        AppUser student = student(s);
        policies.ack(student, s.assignment());
        Long workId = works.enter(student, s.assignment().getId()).getId();
        LocalDateTime t = LocalDateTime.now().withNano(0);
        works.save(student, workId, "一稿", t);
        Long snapshotId = snapshots.listOf(workId).get(0).getId();
        Registration reg = registrations.create(student, workId,
                new RegistrationService.Input(null, "校内模型", null, Stage.POLISH, "改语法", Adoption.MODIFIED, null, null, null), t);

        DataAccessException del = assertThrows(DataAccessException.class, () -> jdbc.update("delete from snapshot where id = ?", snapshotId));
        assertTrue(del.getMessage().contains("不可删除"), "AC-WRK-02-4：" + del.getMessage());
        DataAccessException edit = assertThrows(DataAccessException.class,
                () -> jdbc.update("update registration set purpose = '改了' where id = ?", reg.getId()));
        assertTrue(edit.getMessage().contains("只允许"), "AC-REG-02-3：" + edit.getMessage());
        assertEquals(1, jdbc.update("update registration set status = 'VOIDED' where id = ?", reg.getId()), "作废是唯一放行的更新");
        assertEquals(1, jdbc.queryForObject("select count(*) from snapshot where id = ?", Integer.class, snapshotId));
    }
}
