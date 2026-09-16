package cn.liuhen.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import cn.liuhen.TestSupport;
import cn.liuhen.account.AppUser;
import cn.liuhen.account.Role;
import cn.liuhen.common.ForbiddenWords;
import cn.liuhen.evidence.HashChainService;
import cn.liuhen.registration.Adoption;
import cn.liuhen.registration.Registration;
import cn.liuhen.registration.RegistrationRepository;
import cn.liuhen.registration.Stage;
import cn.liuhen.security.AccessControl;
import cn.liuhen.work.PasteEvent;
import cn.liuhen.work.PasteEventRepository;
import cn.liuhen.work.PasteSource;
import cn.liuhen.work.Snapshot;
import cn.liuhen.work.SnapshotService;
import cn.liuhen.work.SnapshotTrigger;
import cn.liuhen.work.Work;
import cn.liuhen.work.WorkService;

/** 时间线合并三类条目，两端一致，没有时长与判定（AC-WRK-05-1、05-2、AC-TCH-01-2、01-3、AC-TCH-03-3）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimelineAssemblerTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 18, 14, 0);

    @Mock
    private SnapshotService snapshots;
    @Mock
    private PasteEventRepository pastes;
    @Mock
    private RegistrationRepository registrations;
    @Mock
    private WorkService works;
    @Mock
    private AccessControl access;

    private TimelineAssembler assembler;
    private final AppUser student = TestSupport.withId(new AppUser("24020110", "周浩然", Role.STUDENT, "hash", false), 10L);
    private final AppUser teacher = TestSupport.withId(new AppUser("T0001", "T老师", Role.TEACHER, "hash", false), 1L);

    private static Snapshot snapshot(int seq, int chars, LocalDateTime at) {
        String h = "0".repeat(64);
        return Snapshot.keyframe(1L, seq, SnapshotTrigger.TIME, "x".repeat(chars), chars, h, h, h, at);
    }

    @BeforeEach
    void setUp() {
        assembler = new TimelineAssembler(snapshots, pastes, registrations, works, access);
        when(works.require(1L)).thenReturn(TestSupport.withId(new Work(5L, 10L), 1L));
        when(snapshots.listOf(1L)).thenReturn(List.of(snapshot(1, 120, T), snapshot(2, 100, T.plusMinutes(10)), snapshot(3, 400, T.plusMinutes(30))));
        PasteEvent paste = TestSupport.withId(new PasteEvent(1L, 260, 40, 300, PasteSource.WEB, T.plusMinutes(20)), 8L);
        when(pastes.findByWorkIdOrderByOccurredAtAsc(1L)).thenReturn(List.of(paste));
        Registration reg = TestSupport.withId(new Registration(1L, null, "校内模型", null, Stage.POLISH, "改语法", Adoption.MODIFIED,
                null, null, null, null, HashChainService.GENESIS, "1".repeat(64), T.plusMinutes(25)), 9L);
        when(registrations.findByWorkIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(reg));
    }

    @Test
    void mergesThreeKindsInTimeOrder() {
        List<TimelineAssembler.Item> items = assembler.build(student, 1L);
        assertEquals(List.of("SNAPSHOT", "SNAPSHOT", "PASTE", "REGISTRATION", "SNAPSHOT"),
                items.stream().map(TimelineAssembler.Item::type).toList());
    }

    @Test
    void charDeltaIsRelativeToPreviousSnapshot() {
        List<TimelineAssembler.Item> items = assembler.build(student, 1L);
        assertEquals(120, items.get(0).data().get("charDelta"), "第一版相对于空");
        assertEquals(-20, items.get(1).data().get("charDelta"));
        assertEquals(300, items.get(4).data().get("charDelta"));
    }

    @Test
    void registrationItemsCarryStatusAndReadableLabels() {
        TimelineAssembler.Item reg = assembler.build(student, 1L).get(3);
        assertEquals("改语法", reg.data().get("stage"));
        assertEquals("修改后采用", reg.data().get("adoption"));
        assertEquals("ACTIVE", reg.data().get("status"));
        assertEquals("校内模型", reg.data().get("toolNameCustom"));
    }

    @Test
    void sameSecondKeepsSnapshotPasteRegistrationOrder() {
        when(snapshots.listOf(1L)).thenReturn(List.of(snapshot(1, 10, T)));
        when(pastes.findByWorkIdOrderByOccurredAtAsc(1L)).thenReturn(List.of(new PasteEvent(1L, 150, 0, 150, PasteSource.OWN_DOC, T)));
        List<TimelineAssembler.Item> items = assembler.build(student, 1L);
        assertEquals("SNAPSHOT", items.get(0).type());
        assertEquals("PASTE", items.get(1).type());
    }

    @Test
    void studentAndTeacherGetIdenticalTimelines() {
        assertEquals(assembler.build(student, 1L), assembler.build(teacher, 1L));
    }

    @Test
    void noDurationOrJudgementAnywhere() {
        for (TimelineAssembler.Item item : assembler.build(teacher, 1L)) {
            for (var entry : item.data().entrySet()) {
                assertEquals(List.of(), ForbiddenWords.scan(entry.getKey() + String.valueOf(entry.getValue())), item.type());
                assertTrue(!entry.getKey().toLowerCase().contains("duration") && !entry.getKey().toLowerCase().contains("minutes"),
                        "时间线不得出现时长字段：" + entry.getKey());
            }
        }
    }

    @Test
    void emptyWorkGivesEmptyTimeline() {
        when(snapshots.listOf(1L)).thenReturn(List.of());
        when(pastes.findByWorkIdOrderByOccurredAtAsc(1L)).thenReturn(List.of());
        when(registrations.findByWorkIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        assertTrue(assembler.build(student, 1L).isEmpty());
    }

    @Test
    void permissionIsCheckedBeforeAnythingIsRead() {
        org.mockito.Mockito.doThrow(new cn.liuhen.common.ApiExceptionHandler.ForbiddenException())
                .when(access).requireWorkVisible(any(), any());
        org.junit.jupiter.api.Assertions.assertThrows(cn.liuhen.common.ApiExceptionHandler.ForbiddenException.class,
                () -> assembler.build(teacher, 1L));
        org.mockito.Mockito.verify(snapshots, org.mockito.Mockito.never()).listOf(any());
    }
}
