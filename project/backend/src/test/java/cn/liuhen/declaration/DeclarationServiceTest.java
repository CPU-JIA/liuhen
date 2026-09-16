package cn.liuhen.declaration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.liuhen.TestSupport;
import cn.liuhen.account.AppUser;
import cn.liuhen.account.Assignment;
import cn.liuhen.account.AssignmentRepository;
import cn.liuhen.account.Role;
import cn.liuhen.common.ApiExceptionHandler.BadRequestException;
import cn.liuhen.common.ApiExceptionHandler.ConflictException;
import cn.liuhen.common.ApiExceptionHandler.NotFoundException;
import cn.liuhen.common.ForbiddenWords;
import cn.liuhen.evidence.HashChainService;
import cn.liuhen.policy.PolicyRuleEngine;
import cn.liuhen.policy.PolicyScene;
import cn.liuhen.policy.PolicyVersion;
import cn.liuhen.policy.Tier;
import cn.liuhen.registration.Adoption;
import cn.liuhen.registration.AiToolRepository;
import cn.liuhen.registration.Registration;
import cn.liuhen.registration.RegistrationService;
import cn.liuhen.registration.Stage;
import cn.liuhen.registration.Verification;
import cn.liuhen.security.AccessControl;
import cn.liuhen.work.PasteEventRepository;
import cn.liuhen.work.PasteSource;
import cn.liuhen.work.SnapshotService;
import cn.liuhen.work.SnapshotTrigger;
import cn.liuhen.work.Work;
import cn.liuhen.work.WorkService;
import cn.liuhen.work.WorkStatus;

/**
 * 提交时的判定表与声明内容（DECL-01、DECL-02）。
 * 判定表的三个条件：有无有效登记、有无 AI 来源粘贴、是否勾了未使用承诺。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeclarationServiceTest {

    private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 9, 30, 23, 59);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 15, 0);

    @Mock
    private DeclarationRepository declarations;
    @Mock
    private AssignmentRepository assignments;
    @Mock
    private PasteEventRepository pastes;
    @Mock
    private AiToolRepository tools;
    @Mock
    private RegistrationService registrations;
    @Mock
    private SnapshotService snapshots;
    @Mock
    private WorkService works;
    @Mock
    private AccessControl access;

    /** 规则引擎只桩掉查库的两个方法，check 走真实现，否则"超出允许场景"这一项永远测不到。 */
    private final PolicyRuleEngine policy = spy(new PolicyRuleEngine(null, null, null));
    private final HashChainService hashChain = new HashChainService();
    private final ObjectMapper json = new ObjectMapper();

    private DeclarationService service;
    private final AppUser student = TestSupport.withId(new AppUser("24020110", "周浩然", Role.STUDENT, "hash", false), 10L);
    private final Assignment assignment = TestSupport.withId(new Assignment(100L, "实验 1 报告", DEADLINE), 5L);
    private final PolicyVersion declare = TestSupport.withId(new PolicyVersion(100L, PolicyVersion.COURSE_SCOPE, 2, Tier.DECLARE, null, 1L), 20L);
    private final List<PolicyScene> scenes = List.of(new PolicyScene(20L, "改语法", true), new PolicyScene(20L, "生成正文", false));
    private Work work;

    @BeforeEach
    void setUp() {
        service = new DeclarationService(declarations, assignments, pastes, tools, registrations, policy, snapshots, works,
                hashChain, access, json);
        work = TestSupport.withId(new Work(5L, 10L), 1L);
        when(works.require(1L)).thenReturn(work);
        when(assignments.findById(5L)).thenReturn(Optional.of(assignment));
        doReturn(Optional.of(declare)).when(policy).current(100L, 5L);
        doReturn(scenes).when(policy).scenesOf(declare);
        when(pastes.existsByWorkIdAndSource(anyLong(), any())).thenReturn(false);
        when(registrations.activeOf(1L)).thenReturn(List.of());
        when(declarations.save(any())).thenAnswer(inv -> TestSupport.withId(inv.getArgument(0), 77L));
        when(snapshots.maybeCreate(any(), any(), any())).thenReturn(Optional.empty());
    }

    private static Registration registration(Stage stage, String version, String prompt, Verification verification) {
        Registration r = new Registration(1L, null, "校内模型", version, stage, "用途", Adoption.MODIFIED, prompt, null, verification,
                null, HashChainService.GENESIS, "1".repeat(64), NOW.minusHours(1));
        return TestSupport.withId(r, 30L);
    }

    private JsonNode content(Declaration d) throws Exception {
        return json.readTree(d.getContentJson());
    }

    // ---------------------------------------------------------------- 前置阻断

    @Test
    void submittingTwiceIsAConflict() {
        work.submit(NOW, false);
        assertThrows(ConflictException.class, () -> service.submit(student, 1L, false, NOW));
        verify(declarations, never()).save(any());
    }

    @Test
    void pendingPasteBlocksSubmit() {
        when(pastes.existsByWorkIdAndSource(1L, PasteSource.PENDING)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.submit(student, 1L, true, NOW));
        assertEquals(WorkStatus.DRAFT, work.getStatus());
    }

    @Test
    void courseWithoutRulesCannotAcceptSubmissions() {
        doReturn(Optional.empty()).when(policy).current(100L, 5L);
        assertThrows(ConflictException.class, () -> service.submit(student, 1L, true, NOW));
    }

    // ---------------------------------------------------------------- 判定表 AC-DECL-02-1、02-2

    @Test
    void aiPasteWithoutRegistrationBlocksNotUsedPledge() {
        when(pastes.existsByWorkIdAndSource(1L, PasteSource.AI_TOOL)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.submit(student, 1L, true, NOW));
    }

    @Test
    void noRegistrationAndNoPledgeIsRejected() {
        assertThrows(BadRequestException.class, () -> service.submit(student, 1L, false, NOW));
        assertEquals(WorkStatus.DRAFT, work.getStatus());
    }

    @Test
    void pledgeWithoutRegistrationsGivesNotUsedDeclaration() throws Exception {
        Declaration d = service.submit(student, 1L, true, NOW);
        assertEquals(DeclarationKind.NOT_USED, d.getKind());
        JsonNode c = content(d);
        assertEquals("未使用 AI 工具承诺", c.get("title").asText());
        assertTrue(c.get("pledge").asText().contains("未使用任何生成式 AI 工具"));
        assertEquals(0, c.get("items").size());
        assertEquals(WorkStatus.SUBMITTED, work.getStatus());
        assertEquals(NOW, work.getSubmittedAt());
    }

    @Test
    void registrationsGiveAiUsedDeclaration() throws Exception {
        when(registrations.activeOf(1L)).thenReturn(List.of(registration(Stage.POLISH, "v1", "帮我改语法", Verification.SOURCE_CHECK)));
        Declaration d = service.submit(student, 1L, false, NOW);
        assertEquals(DeclarationKind.AI_USED, d.getKind());
        JsonNode item = content(d).get("items").get(0);
        assertEquals("校内模型", item.get("tool").asText());
        assertEquals("v1", item.get("version").asText());
        assertEquals("改语法", item.get("stage").asText());
        assertEquals("帮我改语法", item.get("prompt").asText());
        assertEquals("核对原文", item.get("verification").asText());
        assertEquals("修改后采用", item.get("adoption").asText());
        assertFalse(item.get("exceedsPolicy").asBoolean());
    }

    @Test
    void pledgeContradictedByRegistrationsIsRejected() {
        when(registrations.activeOf(1L)).thenReturn(List.of(registration(Stage.POLISH, null, null, null)));
        assertThrows(BadRequestException.class, () -> service.submit(student, 1L, true, NOW),
                "有登记又勾未使用，两个说法矛盾，不能悄悄以登记为准");
        assertEquals(WorkStatus.DRAFT, work.getStatus());
    }

    // ---------------------------------------------------------------- 七要素与规则对照 AC-DECL-01-1、01-4

    @Test
    void missingElementsShowNotFilled() throws Exception {
        when(registrations.activeOf(1L)).thenReturn(List.of(registration(Stage.POLISH, null, null, null)));
        JsonNode item = content(service.submit(student, 1L, false, NOW)).get("items").get(0);
        assertEquals("未填写", item.get("version").asText());
        assertEquals("未填写", item.get("prompt").asText());
        assertEquals("未填写", item.get("verification").asText());
    }

    @Test
    void presetToolNameIsLookedUp() throws Exception {
        Registration r = TestSupport.withId(new Registration(1L, 3L, null, null, Stage.POLISH, "用途", Adoption.DIRECT, null, null, null,
                null, HashChainService.GENESIS, "1".repeat(64), NOW), 31L);
        cn.liuhen.registration.AiTool tool = org.mockito.Mockito.mock(cn.liuhen.registration.AiTool.class);
        when(tool.getName()).thenReturn("DeepSeek");
        when(tools.findById(3L)).thenReturn(Optional.of(tool));
        when(registrations.activeOf(1L)).thenReturn(List.of(r));
        assertEquals("DeepSeek", content(service.submit(student, 1L, false, NOW)).get("items").get(0).get("tool").asText());
    }

    @Test
    void sceneNotAllowedUnderDeclareIsMarkedExceeding() throws Exception {
        when(registrations.activeOf(1L)).thenReturn(List.of(
                registration(Stage.POLISH, null, null, null),
                registration(Stage.BODY, null, null, null),
                registration(Stage.CODE, null, null, null)));
        JsonNode c = content(service.submit(student, 1L, false, NOW));
        assertFalse(c.get("items").get(0).get("exceedsPolicy").asBoolean(), "改语法被允许");
        assertTrue(c.get("items").get(1).get("exceedsPolicy").asBoolean(), "生成正文被明确禁止");
        assertTrue(c.get("items").get(2).get("exceedsPolicy").asBoolean(), "生成代码框架老师没提，需声明档视为不允许");
        assertEquals(2, c.get("exceededCount").asInt());
    }

    @Test
    void forbidTierMarksEverythingExceeding() throws Exception {
        PolicyVersion forbid = TestSupport.withId(new PolicyVersion(100L, PolicyVersion.COURSE_SCOPE, 3, Tier.FORBID, null, 1L), 21L);
        doReturn(Optional.of(forbid)).when(policy).current(100L, 5L);
        doReturn(List.of()).when(policy).scenesOf(forbid);
        when(registrations.activeOf(1L)).thenReturn(List.of(registration(Stage.POLISH, null, null, null)));
        JsonNode c = content(service.submit(student, 1L, false, NOW));
        assertTrue(c.get("items").get(0).get("exceedsPolicy").asBoolean());
        assertEquals(3, c.get("policy").get("versionNo").asInt(), "对照的是提交时生效的版本");
        assertEquals("FORBID", c.get("policy").get("tier").asText());
    }

    // ---------------------------------------------------------------- 逾期、快照、哈希 AC-DECL-01-6

    @Test
    void lateOnlyAfterTheDeadline() {
        Declaration onTime = service.submit(student, 1L, true, DEADLINE);
        assertFalse(onTime.isLate(), "截止那一刻提交不算逾期");
        work = TestSupport.withId(new Work(5L, 10L), 1L);
        when(works.require(1L)).thenReturn(work);
        Declaration late = service.submit(student, 1L, true, DEADLINE.plusNanos(1_000_000));
        assertTrue(late.isLate());
        assertTrue(work.isLate());
    }

    @Test
    void submitForcesASubmitSnapshotBeforeSealing() {
        service.submit(student, 1L, true, NOW);
        verify(snapshots).maybeCreate(eq(work), eq(SnapshotTrigger.SUBMIT), eq(NOW));
    }

    @Test
    void contentHashIsSha256OfTheStoredJson() {
        Declaration d = service.submit(student, 1L, true, NOW);
        assertEquals(hashChain.sha256Hex(d.getContentJson()), d.getContentHash());
        assertEquals(20L, d.getPolicyVersionId());
    }

    @Test
    void declarationTextContainsNoJudgementWords() {
        when(registrations.activeOf(1L)).thenReturn(List.of(registration(Stage.BODY, null, null, null)));
        Declaration d = service.submit(student, 1L, false, NOW);
        assertEquals(List.of(), ForbiddenWords.scan(d.getContentJson()));
    }

    @Test
    void viewBeforeSubmitIsNotFound() {
        when(declarations.findByWorkId(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.view(student, 1L));
    }
}
