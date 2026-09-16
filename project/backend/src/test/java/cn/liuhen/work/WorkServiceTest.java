package cn.liuhen.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

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
import cn.liuhen.account.Role;
import cn.liuhen.common.ApiExceptionHandler.BadRequestException;
import cn.liuhen.common.ApiExceptionHandler.ConflictException;
import cn.liuhen.common.ApiExceptionHandler.ForbiddenException;
import cn.liuhen.policy.PolicyRuleEngine;
import cn.liuhen.policy.PolicyVersion;
import cn.liuhen.policy.Tier;
import cn.liuhen.security.AccessControl;

/** 进入作业、自动保存上限与改动量、粘贴阈值（AC-RULE-02-3、AC-WRK-01-1、01-4、AC-WRK-03-1、03-2、03-5）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 9, 30);

    @Mock
    private WorkRepository works;
    @Mock
    private PasteEventRepository pastes;
    @Mock
    private SnapshotService snapshots;
    @Mock
    private PolicyRuleEngine policy;
    @Mock
    private AccessControl access;

    private WorkService service;
    private final AppUser student = TestSupport.withId(new AppUser("24020110", "周浩然", Role.STUDENT, "hash", false), 10L);
    private final Assignment assignment = TestSupport.withId(new Assignment(3L, "实验 1 报告", NOW.plusDays(7)), 5L);

    @BeforeEach
    void setUp() {
        service = new WorkService(works, pastes, snapshots, policy, access, TestSupport.props());
        when(works.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pastes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(access.requireEnrolledAssignment(any(), anyLong())).thenReturn(assignment);
        when(snapshots.maybeCreate(any(), any(), any())).thenReturn(Optional.empty());
    }

    private Work draft() {
        Work w = TestSupport.withId(new Work(5L, 10L), 1L);
        when(works.findById(1L)).thenReturn(Optional.of(w));
        return w;
    }

    @Test
    void enterRejectsWhenRulesNotAcked() {
        PolicyVersion v = new PolicyVersion(3L, PolicyVersion.COURSE_SCOPE, 1, Tier.DECLARE, null, 1L);
        when(policy.current(3L, 5L)).thenReturn(Optional.of(v));
        when(policy.needsReack(10L, 5L, v)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.enter(student, 5L));
        verify(works, never()).save(any());
    }

    @Test
    void enterWithoutAnyRulesGoesStraightIn() {
        when(policy.current(3L, 5L)).thenReturn(Optional.empty());
        when(works.findByAssignmentIdAndStudentId(5L, 10L)).thenReturn(Optional.empty());
        Work w = service.enter(student, 5L);
        assertEquals(WorkStatus.DRAFT, w.getStatus());
        assertEquals("", w.getCurrentText());
        verify(works).save(any());
    }

    @Test
    void enterReturnsExistingDraftInsteadOfCreatingASecond() {
        Work existing = new Work(5L, 10L);
        when(policy.current(3L, 5L)).thenReturn(Optional.empty());
        when(works.findByAssignmentIdAndStudentId(5L, 10L)).thenReturn(Optional.of(existing));
        assertSame(existing, service.enter(student, 5L));
        verify(works, never()).save(any());
    }

    @Test
    void saveRejectsSubmittedWork() {
        Work w = draft();
        w.submit(NOW, false);
        assertThrows(ConflictException.class, () -> service.save(student, 1L, "改", NOW));
    }

    @Test
    void saveAcceptsExactlyFiftyThousandCodePoints() {
        Work w = draft();
        String emoji = "😀".repeat(50_000);
        WorkService.SaveResult r = service.save(student, 1L, emoji, NOW);
        assertEquals(50_000, r.charCount(), "按码点数，不按 UTF-16 单元数");
        assertEquals(50_000, w.getCharCount());
        assertEquals(NOW, w.getLastSavedAt());
    }

    @Test
    void saveRejectsFiftyThousandAndOne() {
        Work w = draft();
        assertThrows(BadRequestException.class, () -> service.save(student, 1L, "字".repeat(50_001), NOW));
        assertEquals("", w.getCurrentText(), "被拒的文本不能写进去");
    }

    @Test
    void saveTreatsNullAsEmptyText() {
        Work w = draft();
        WorkService.SaveResult r = service.save(student, 1L, null, NOW);
        assertEquals(0, r.charCount());
        assertEquals("", w.getCurrentText());
    }

    @Test
    void saveAccumulatesEditedCharsAndAsksSnapshotService() {
        Work w = draft();
        service.save(student, 1L, "abc", NOW);
        service.save(student, 1L, "abcd", NOW.plusSeconds(5));
        assertEquals(4, w.getPendingEditChars(), "3 个插入加 1 个插入");
        verify(snapshots).maybeCreate(eq(w), eq(SnapshotTrigger.TIME), eq(NOW.plusSeconds(5)));
    }

    @Test
    void saveReportsSnapshotSeqWhenOneWasCreated() {
        draft();
        Snapshot s = Snapshot.keyframe(1L, 4, SnapshotTrigger.TIME, "abc", 3, "0".repeat(64), "0".repeat(64), "0".repeat(64), NOW);
        when(snapshots.maybeCreate(any(), any(), any())).thenReturn(Optional.of(s));
        WorkService.SaveResult r = service.save(student, 1L, "abc", NOW);
        assertTrue(r.snapshotCreated());
        assertEquals(4, r.snapshotSeq());
    }

    @Test
    void saveWithoutSnapshotReportsNullSeq() {
        draft();
        WorkService.SaveResult r = service.save(student, 1L, "abc", NOW);
        assertTrue(!r.snapshotCreated());
        assertNull(r.snapshotSeq());
    }

    @Test
    void pasteOfExactlyOneHundredCharsIsNotRecorded() {
        draft();
        assertTrue(service.recordPaste(student, 1L, 100, 0, 100, null, NOW).isEmpty());
        verify(pastes, never()).save(any());
    }

    @Test
    void pasteOfOneHundredAndOneCharsIsRecordedAsPending() {
        draft();
        PasteEvent p = service.recordPaste(student, 1L, 101, 10, 111, null, NOW).orElseThrow();
        assertEquals(PasteSource.PENDING, p.getSource());
        assertEquals(101, p.getCharCount());
        assertEquals(NOW, p.getOccurredAt());
    }

    @Test
    void pasteOfOneHundredAndTenMixedCharsIsOverThreshold() {
        draft();
        assertTrue(service.recordPaste(student, 1L, 110, 0, 110, null, NOW).isPresent());
    }

    @Test
    void pasteWithSourceChosenUpfrontKeepsIt() {
        draft();
        PasteEvent p = service.recordPaste(student, 1L, 200, 0, 200, PasteSource.WEB, NOW).orElseThrow();
        assertEquals(PasteSource.WEB, p.getSource());
    }

    @Test
    void pasteWithEmptyRangeIsRejected() {
        draft();
        assertThrows(BadRequestException.class, () -> service.recordPaste(student, 1L, 150, 20, 20, null, NOW));
    }

    @Test
    void resolvePasteRequiresARealSource() {
        draft();
        PasteEvent p = new PasteEvent(1L, 150, 0, 150, PasteSource.PENDING, NOW);
        when(pastes.findById(9L)).thenReturn(Optional.of(p));
        assertThrows(BadRequestException.class, () -> service.resolvePaste(student, 9L, PasteSource.PENDING, null));
        assertThrows(BadRequestException.class, () -> service.resolvePaste(student, 9L, null, null));
        service.resolvePaste(student, 9L, PasteSource.AI_TOOL, 42L);
        assertEquals(PasteSource.AI_TOOL, p.getSource());
        assertEquals(42L, p.getRegistrationId());
    }

    @Test
    void forbiddenActorCannotSave() {
        Work w = draft();
        doThrow(new ForbiddenException()).when(access).requireWorkEditable(any(), any());
        assertThrows(ForbiddenException.class, () -> service.save(student, 1L, "x", NOW));
        assertEquals("", w.getCurrentText());
        assertNull(w.getLastSavedAt());
    }
}
