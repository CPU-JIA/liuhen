package cn.liuhen.work;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.liuhen.common.ApiExceptionHandler.NotFoundException;
import cn.liuhen.common.LiuhenProperties;
import cn.liuhen.evidence.HashChainService;
import cn.liuhen.work.diff.DeltaCodec;
import cn.liuhen.work.diff.MyersDiff;

/**
 * 快照服务：什么时候存、怎么存、怎么还原。
 * <p>
 * 触发：距上一快照满 N 分钟且有改动，或累计改动超过 M 字符（AC-WRK-02-1、02-2），提交与导入强制触发。
 * 存储：每 K 个快照存一次全文作为关键帧，其余存相对上一快照的差分（AC-WRK-02-5）。
 * 还原：从目标往前找到最近关键帧，顺序 apply 差分，最后用 content_hash 校验。
 * 每条快照进哈希链，链键 work:{id}:snapshot。
 */
@Service
public class SnapshotService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final SnapshotRepository snapshots;
    private final PasteEventRepository pasteEvents;
    private final HashChainService hashChain;
    private final MyersDiff diff = new MyersDiff();
    private final DeltaCodec codec = new DeltaCodec();
    private final LiuhenProperties.Work cfg;

    public SnapshotService(SnapshotRepository snapshots, PasteEventRepository pasteEvents, HashChainService hashChain, LiuhenProperties props) {
        this.snapshots = snapshots;
        this.pasteEvents = pasteEvents;
        this.hashChain = hashChain;
        this.cfg = props.work();
    }

    /** 按触发规则决定要不要存。强制触发（SUBMIT、IMPORT）不看规则。 */
    @Transactional
    public Optional<Snapshot> maybeCreate(Work work, SnapshotTrigger trigger, LocalDateTime now) {
        Optional<Snapshot> last = snapshots.findFirstByWorkIdOrderBySeqNoDesc(work.getId());
        boolean forced = trigger == SnapshotTrigger.SUBMIT || trigger == SnapshotTrigger.IMPORT;
        if (!forced) {
            if (work.getPendingEditChars() <= 0) {
                return Optional.empty();
            }
            boolean byVolume = work.getPendingEditChars() >= cfg.snapshotEditChars();
            boolean byTime = last.map(s -> Duration.between(s.getCreatedAt(), now).toMinutes() >= cfg.snapshotIntervalMinutes())
                    .orElse(true);
            if (!byVolume && !byTime) {
                return Optional.empty();
            }
            trigger = byVolume ? SnapshotTrigger.EDIT_VOLUME : SnapshotTrigger.TIME;
        } else if (last.isPresent() && last.get().getContentHash().equals(hashChain.sha256Hex(work.getCurrentText()))) {
            // 提交时文本与上一快照完全一致，不再重复存一份
            return Optional.empty();
        }
        Snapshot created = create(work, trigger, last.orElse(null), now);
        work.resetPendingEdits();
        return Optional.of(created);
    }

    private Snapshot create(Work work, SnapshotTrigger trigger, Snapshot last, LocalDateTime now) {
        String text = work.getCurrentText();
        int seq = last == null ? 1 : last.getSeqNo() + 1;
        int charCount = text.codePointCount(0, text.length());
        String contentHash = hashChain.sha256Hex(text);
        String prevChain = last == null ? HashChainService.GENESIS : last.getChainHash();
        String chainHash = hashChain.append(prevChain, contentHash, now.atZone(ZONE).toInstant()).chainHash();

        boolean keyframe = last == null || (seq - 1) % cfg.keyframeEvery() == 0;
        Snapshot snapshot;
        if (keyframe) {
            snapshot = Snapshot.keyframe(work.getId(), seq, trigger, text, charCount, contentHash, prevChain, chainHash, now);
        } else {
            String baseText = reconstruct(last);
            byte[] delta = codec.encode(diff.diff(baseText, text));
            snapshot = Snapshot.delta(work.getId(), seq, trigger, last.getId(), delta, charCount, contentHash, prevChain, chainHash, now);
        }
        Snapshot saved = snapshots.save(snapshot);
        // 尚未挂到任何快照的粘贴事件，归到本快照，Sprint 2 归因用
        for (PasteEvent p : pasteEvents.findByWorkIdOrderByOccurredAtAsc(work.getId())) {
            if (p.getSnapshotId() == null) {
                p.attachSnapshot(saved.getId());
            }
        }
        return saved;
    }

    /** 还原任意快照的全文。 */
    public String reconstruct(Snapshot target) {
        Deque<Snapshot> chain = new ArrayDeque<>();
        Snapshot cursor = target;
        while (!cursor.isKeyframe()) {
            // 正常的差分链最多 keyframeEvery - 1 步就到关键帧；再长说明 base 指针被改成了环或指错，不能无限走下去（实验 6 审查）
            if (chain.size() >= cfg.keyframeEvery()) {
                throw new IllegalStateException("快照 " + target.getSeqNo() + " 的差分链超过关键帧间隔，数据可能被改动");
            }
            chain.push(cursor);
            Long baseId = cursor.getBaseSnapshotId();
            cursor = snapshots.findById(baseId).orElseThrow(() -> new NotFoundException("差分基快照丢失：" + baseId));
        }
        String text = cursor.getFullText();
        while (!chain.isEmpty()) {
            Snapshot s = chain.pop();
            text = diff.apply(text, codec.decode(s.getDelta()));
        }
        if (!hashChain.sha256Hex(text).equals(target.getContentHash())) {
            throw new IllegalStateException("快照 " + target.getSeqNo() + " 还原后哈希不匹配，数据可能被改动");
        }
        return text;
    }

    public String reconstruct(Long workId, int seqNo) {
        Snapshot s = snapshots.findByWorkIdAndSeqNo(workId, seqNo)
                .orElseThrow(() -> new NotFoundException("版本 " + seqNo + " 不存在"));
        return reconstruct(s);
    }

    public List<Snapshot> listOf(Long workId) {
        return snapshots.findByWorkIdOrderBySeqNoAsc(workId);
    }

    public List<MyersDiff.LineChange> lineDiff(Long workId, int fromSeq, int toSeq) {
        return diff.lineDiff(reconstruct(workId, fromSeq), reconstruct(workId, toSeq));
    }
}
