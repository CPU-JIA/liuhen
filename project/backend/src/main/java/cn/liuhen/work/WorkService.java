package cn.liuhen.work;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.liuhen.account.AppUser;
import cn.liuhen.account.Assignment;
import cn.liuhen.common.ApiExceptionHandler.BadRequestException;
import cn.liuhen.common.ApiExceptionHandler.ConflictException;
import cn.liuhen.common.ApiExceptionHandler.NotFoundException;
import cn.liuhen.common.LiuhenProperties;
import cn.liuhen.policy.PolicyRuleEngine;
import cn.liuhen.policy.PolicyVersion;
import cn.liuhen.security.AccessControl;
import cn.liuhen.work.diff.MyersDiff;

/** 作业稿：进入、自动保存、粘贴事件。提交在 DeclarationService。 */
@Service
public class WorkService {

    public record SaveResult(LocalDateTime savedAt, int charCount, boolean snapshotCreated, Integer snapshotSeq) { }

    private final WorkRepository works;
    private final PasteEventRepository pastes;
    private final SnapshotService snapshots;
    private final PolicyRuleEngine policy;
    private final AccessControl access;
    private final MyersDiff diff = new MyersDiff();
    private final LiuhenProperties.Work cfg;

    public WorkService(WorkRepository works, PasteEventRepository pastes, SnapshotService snapshots, PolicyRuleEngine policy,
                       AccessControl access, LiuhenProperties props) {
        this.works = works;
        this.pastes = pastes;
        this.snapshots = snapshots;
        this.policy = policy;
        this.access = access;
        this.cfg = props.work();
    }

    /** 学生进入作业：没有稿就建一份；规则未确认则拒绝进入编辑（AC-RULE-02-3 的后端保证）。 */
    @Transactional
    public Work enter(AppUser actor, Long assignmentId) {
        Assignment assignment = access.requireEnrolledAssignment(actor, assignmentId);
        Optional<PolicyVersion> current = policy.current(assignment.getCourseId(), assignment.getId());
        if (current.isPresent() && policy.needsReack(actor.getId(), assignment.getId(), current.get())) {
            throw new ConflictException("请先阅读并确认本作业的 AI 使用规则");
        }
        return works.findByAssignmentIdAndStudentId(assignmentId, actor.getId())
                .orElseGet(() -> works.save(new Work(assignmentId, actor.getId())));
    }

    public Work require(Long workId) {
        return works.findById(workId).orElseThrow(() -> new NotFoundException("作业稿不存在"));
    }

    /** 自动保存（AC-WRK-01-1、01-4）。改动量用差分算，再交给快照服务判断是否落版本。 */
    @Transactional
    public SaveResult save(AppUser actor, Long workId, String text, LocalDateTime now) {
        Work work = require(workId);
        access.requireWorkEditable(actor, work);
        if (work.getStatus() == WorkStatus.SUBMITTED) {
            throw new ConflictException("已提交的作业不能再修改");
        }
        String incoming = text == null ? "" : text;
        int chars = incoming.codePointCount(0, incoming.length());
        if (chars > cfg.maxChars()) {
            throw new BadRequestException("正文最多 " + cfg.maxChars() + " 字符");
        }
        int edited = diff.editedChars(work.getCurrentText(), incoming);
        work.updateText(incoming, chars, edited, now);
        Optional<Snapshot> created = snapshots.maybeCreate(work, SnapshotTrigger.TIME, now);
        return new SaveResult(now, chars, created.isPresent(), created.map(Snapshot::getSeqNo).orElse(null));
    }

    /** 粘贴超过阈值才记录（AC-WRK-03-1、03-2）。来源可以先不选，记为 PENDING。 */
    @Transactional
    public Optional<PasteEvent> recordPaste(AppUser actor, Long workId, int charCount, int offsetStart, int offsetEnd,
                                            PasteSource source, LocalDateTime now) {
        Work work = require(workId);
        access.requireWorkEditable(actor, work);
        if (charCount <= cfg.pasteThresholdChars()) {
            return Optional.empty();
        }
        if (offsetEnd <= offsetStart) {
            throw new BadRequestException("粘贴区间不合法");
        }
        return Optional.of(pastes.save(new PasteEvent(workId, charCount, offsetStart, offsetEnd, source, now)));
    }

    @Transactional
    public PasteEvent resolvePaste(AppUser actor, Long pasteId, PasteSource source, Long registrationId) {
        PasteEvent paste = pastes.findById(pasteId).orElseThrow(() -> new NotFoundException("粘贴事件不存在"));
        Work work = require(paste.getWorkId());
        access.requireWorkEditable(actor, work);
        if (source == null || source == PasteSource.PENDING) {
            throw new BadRequestException("请选择来源");
        }
        paste.resolve(source, registrationId);
        return paste;
    }

    public List<PasteEvent> pendingPastes(AppUser actor, Long workId) {
        Work work = require(workId);
        access.requireWorkVisible(actor, work);
        return pastes.findByWorkIdAndSource(workId, PasteSource.PENDING);
    }

    public int pasteThreshold() {
        return cfg.pasteThresholdChars();
    }
}
