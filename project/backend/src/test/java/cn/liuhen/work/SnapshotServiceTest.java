package cn.liuhen.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import cn.liuhen.TestSupport;
import cn.liuhen.evidence.HashChainService;
import cn.liuhen.work.diff.DeltaCodec;

/** 快照触发规则、关键帧、还原与哈希校验（AC-WRK-02-1、02-2、02-3、02-5）。仓库用内存列表代替。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SnapshotServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 18, 10, 0);
    private static final long WORK_ID = 7L;

    @Mock
    private SnapshotRepository snapshots;
    @Mock
    private PasteEventRepository pasteEvents;

    private final HashChainService hashChain = new HashChainService();
    private final List<Snapshot> store = new ArrayList<>();
    private SnapshotService service;

    @BeforeEach
    void setUp() {
        service = new SnapshotService(snapshots, pasteEvents, hashChain, TestSupport.props());
        when(snapshots.save(any())).thenAnswer(inv -> {
            Snapshot s = TestSupport.withId(inv.getArgument(0), store.size() + 1);
            store.add(s);
            return s;
        });
        when(snapshots.findFirstByWorkIdOrderBySeqNoDesc(anyLong())).thenAnswer(inv ->
                store.stream().filter(s -> s.getWorkId().equals(inv.getArgument(0))).max(Comparator.comparingInt(Snapshot::getSeqNo)));
        when(snapshots.findById(anyLong())).thenAnswer(inv ->
                store.stream().filter(s -> s.getId().equals(inv.getArgument(0))).findFirst());
        when(pasteEvents.findByWorkIdOrderByOccurredAtAsc(anyLong())).thenReturn(List.of());
    }

    private static Work work(String text, int pendingEdits) {
        Work w = mock(Work.class);
        when(w.getId()).thenReturn(WORK_ID);
        when(w.getCurrentText()).thenReturn(text);
        when(w.getPendingEditChars()).thenReturn(pendingEdits);
        return w;
    }

    @Test
    void noEditsNoSnapshot() {
        assertTrue(service.maybeCreate(work("abc", 0), SnapshotTrigger.TIME, T0).isEmpty());
        verify(snapshots, never()).save(any());
    }

    @Test
    void firstSnapshotIsKeyframeChainedFromGenesis() {
        Snapshot s = service.maybeCreate(work("第一稿", 3), SnapshotTrigger.TIME, T0).orElseThrow();
        assertEquals(1, s.getSeqNo());
        assertTrue(s.isKeyframe());
        assertEquals("第一稿", s.getFullText());
        assertEquals(HashChainService.GENESIS, s.getPrevChainHash());
        assertEquals(hashChain.sha256Hex("第一稿"), s.getContentHash());
        assertEquals(3, s.getCharCount());
    }

    @Test
    void smallEditWithinTenMinutesWaits() {
        service.maybeCreate(work("a", 1), SnapshotTrigger.TIME, T0);
        assertTrue(service.maybeCreate(work("ab", 120), SnapshotTrigger.TIME, T0.plusMinutes(9)).isEmpty());
    }

    @Test
    void smallEditAtTenMinutesCreatesTimeSnapshot() {
        service.maybeCreate(work("a", 1), SnapshotTrigger.TIME, T0);
        Snapshot s = service.maybeCreate(work("ab", 120), SnapshotTrigger.TIME, T0.plusMinutes(10)).orElseThrow();
        assertEquals(SnapshotTrigger.TIME, s.getTrigger());
        assertEquals(2, s.getSeqNo());
    }

    @Test
    void fiveHundredEditsCreateVolumeSnapshotAtOnce() {
        service.maybeCreate(work("a", 1), SnapshotTrigger.TIME, T0);
        Snapshot s = service.maybeCreate(work("ab", 500), SnapshotTrigger.TIME, T0.plusMinutes(1)).orElseThrow();
        assertEquals(SnapshotTrigger.EDIT_VOLUME, s.getTrigger());
    }

    @Test
    void fourHundredNinetyNineEditsWithinIntervalWait() {
        service.maybeCreate(work("a", 1), SnapshotTrigger.TIME, T0);
        assertTrue(service.maybeCreate(work("ab", 499), SnapshotTrigger.TIME, T0.plusMinutes(1)).isEmpty());
    }

    @Test
    void creatingSnapshotResetsPendingEdits() {
        Work w = work("abc", 600);
        service.maybeCreate(w, SnapshotTrigger.TIME, T0);
        verify(w).resetPendingEdits();
    }

    @Test
    void submitWithUnchangedTextIsSkipped() {
        service.maybeCreate(work("终稿", 1), SnapshotTrigger.TIME, T0);
        assertTrue(service.maybeCreate(work("终稿", 0), SnapshotTrigger.SUBMIT, T0.plusMinutes(1)).isEmpty());
        assertEquals(1, store.size());
    }

    @Test
    void submitWithChangedTextIsForcedEvenWithoutPendingEdits() {
        service.maybeCreate(work("初稿", 1), SnapshotTrigger.TIME, T0);
        Snapshot s = service.maybeCreate(work("终稿", 0), SnapshotTrigger.SUBMIT, T0.plusMinutes(1)).orElseThrow();
        assertEquals(SnapshotTrigger.SUBMIT, s.getTrigger());
    }

    @Test
    void keyframeAtFirstEleventhAndTwentyFirst() {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 21; i++) {
            text.append("第").append(i).append("段。");
            service.maybeCreate(work(text.toString(), 600), SnapshotTrigger.TIME, T0.plusMinutes(i));
        }
        assertEquals(21, store.size());
        for (Snapshot s : store) {
            boolean expectKeyframe = s.getSeqNo() == 1 || s.getSeqNo() == 11 || s.getSeqNo() == 21;
            assertEquals(expectKeyframe, s.isKeyframe(), "第 " + s.getSeqNo() + " 个");
        }
        assertEquals(store.get(10).getId(), store.get(11).getBaseSnapshotId(), "第 12 个的差分基于第 11 个");
        assertEquals(store.get(8).getId(), store.get(9).getBaseSnapshotId(), "第 10 个的差分基于第 9 个");
    }

    @Test
    void chainHashLinksEachSnapshotToPrevious() {
        service.maybeCreate(work("a", 1), SnapshotTrigger.TIME, T0);
        service.maybeCreate(work("ab", 600), SnapshotTrigger.TIME, T0.plusMinutes(1));
        service.maybeCreate(work("abc", 600), SnapshotTrigger.TIME, T0.plusMinutes(2));
        assertEquals(store.get(0).getChainHash(), store.get(1).getPrevChainHash());
        assertEquals(store.get(1).getChainHash(), store.get(2).getPrevChainHash());
    }

    @Test
    void deltaChainReconstructsEveryVersion() {
        String[] versions = {"abc", "abcd", "abXd", "Xd", "Xd最终", ""};
        for (int i = 0; i < versions.length; i++) {
            service.maybeCreate(work(versions[i], 600), SnapshotTrigger.TIME, T0.plusMinutes(i));
        }
        for (int i = 0; i < versions.length; i++) {
            assertEquals(versions[i], service.reconstruct(store.get(i)), "版本 " + (i + 1));
        }
    }

    @Test
    void reconstructBySeqNoFindsTheRightVersion() {
        service.maybeCreate(work("一", 1), SnapshotTrigger.TIME, T0);
        service.maybeCreate(work("一二", 600), SnapshotTrigger.TIME, T0.plusMinutes(1));
        when(snapshots.findByWorkIdAndSeqNo(WORK_ID, 2)).thenReturn(Optional.of(store.get(1)));
        assertEquals("一二", service.reconstruct(WORK_ID, 2));
    }

    @Test
    void reconstructDetectsTamperedContent() {
        service.maybeCreate(work("abc", 600), SnapshotTrigger.TIME, T0);
        service.maybeCreate(work("abcd", 600), SnapshotTrigger.TIME, T0.plusMinutes(1));
        Snapshot real = store.get(1);
        Snapshot tampered = Snapshot.delta(WORK_ID, 2, SnapshotTrigger.TIME, real.getBaseSnapshotId(), real.getDelta(), 4,
                hashChain.sha256Hex("abce"), real.getPrevChainHash(), real.getChainHash(), real.getCreatedAt());
        assertThrows(IllegalStateException.class, () -> service.reconstruct(tampered));
    }

    @Test
    void reconstructRefusesACircularDeltaChainInsteadOfHanging() {
        // 审查意见：base_snapshot_id 被改成环时原实现会死循环，现在应在关键帧间隔内报错
        String h = hashChain.sha256Hex("x");
        byte[] delta = new DeltaCodec().encode(List.of());
        Snapshot a = TestSupport.withId(Snapshot.delta(WORK_ID, 2, SnapshotTrigger.TIME, 3L, delta, 1, h, h, h, T0), 2L);
        Snapshot b = TestSupport.withId(Snapshot.delta(WORK_ID, 3, SnapshotTrigger.TIME, 2L, delta, 1, h, h, h, T0), 3L);
        store.add(a);
        store.add(b);
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
                () -> assertThrows(IllegalStateException.class, () -> service.reconstruct(a)));
    }

    @Test
    void newSnapshotAdoptsOnlyUnattachedPastes() {
        PasteEvent fresh = new PasteEvent(WORK_ID, 150, 0, 150, PasteSource.PENDING, T0);
        PasteEvent old = new PasteEvent(WORK_ID, 150, 0, 150, PasteSource.WEB, T0.minusMinutes(5));
        old.attachSnapshot(99L);
        when(pasteEvents.findByWorkIdOrderByOccurredAtAsc(WORK_ID)).thenReturn(List.of(old, fresh));
        Snapshot s = service.maybeCreate(work("abc", 600), SnapshotTrigger.TIME, T0).orElseThrow();
        assertEquals(s.getId(), fresh.getSnapshotId());
        assertEquals(99L, old.getSnapshotId());
    }

    @Test
    void lineDiffBetweenTwoVersions() {
        service.maybeCreate(work("第一行\n第二行", 1), SnapshotTrigger.TIME, T0);
        service.maybeCreate(work("第一行\n第二行改了", 600), SnapshotTrigger.TIME, T0.plusMinutes(1));
        when(snapshots.findByWorkIdAndSeqNo(WORK_ID, 1)).thenReturn(Optional.of(store.get(0)));
        when(snapshots.findByWorkIdAndSeqNo(WORK_ID, 2)).thenReturn(Optional.of(store.get(1)));
        assertFalse(service.lineDiff(WORK_ID, 1, 2).isEmpty());
        assertEquals(3, service.lineDiff(WORK_ID, 1, 2).size(), "相同、删除、新增各一行");
    }
}
