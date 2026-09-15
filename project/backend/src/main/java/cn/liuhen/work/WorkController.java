package cn.liuhen.work;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.liuhen.account.AppUser;
import cn.liuhen.common.ApiExceptionHandler.BadRequestException;
import cn.liuhen.declaration.Declaration;
import cn.liuhen.declaration.DeclarationService;
import cn.liuhen.security.AccessControl;
import cn.liuhen.security.CurrentUser;
import cn.liuhen.timeline.TimelineAssembler;
import cn.liuhen.work.diff.MyersDiff;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/works")
public class WorkController {

    public record SaveText(@NotNull String text) { }

    public record PasteRequest(int charCount, int offsetStart, int offsetEnd, PasteSource source) { }

    public record ResolvePaste(@NotNull PasteSource source, Long registrationId) { }

    public record SubmitRequest(boolean declareNotUsed) { }

    public record SnapshotView(int seqNo, String trigger, int charCount, boolean keyframe, LocalDateTime createdAt, String chainHash) { }

    private final WorkService works;
    private final SnapshotService snapshots;
    private final DeclarationService declarations;
    private final TimelineAssembler timeline;
    private final AccessControl access;
    private final CurrentUser currentUser;

    public WorkController(WorkService works, SnapshotService snapshots, DeclarationService declarations, TimelineAssembler timeline,
                          AccessControl access, CurrentUser currentUser) {
        this.works = works;
        this.snapshots = snapshots;
        this.declarations = declarations;
        this.timeline = timeline;
        this.access = access;
        this.currentUser = currentUser;
    }

    @PutMapping("/{workId}/text")
    public WorkService.SaveResult save(@PathVariable Long workId, @RequestBody SaveText req) {
        return works.save(currentUser.require(), workId, req.text(), LocalDateTime.now());
    }

    @PostMapping("/{workId}/pastes")
    public Map<String, Object> paste(@PathVariable Long workId, @RequestBody PasteRequest req) {
        return works.recordPaste(currentUser.require(), workId, req.charCount(), req.offsetStart(), req.offsetEnd(), req.source(), LocalDateTime.now())
                .<Map<String, Object>>map(p -> Map.of("recorded", true, "pasteId", p.getId(), "source", p.getSource().name()))
                .orElse(Map.of("recorded", false, "threshold", works.pasteThreshold()));
    }

    @PutMapping("/pastes/{pasteId}/source")
    public Map<String, Object> resolvePaste(@PathVariable Long pasteId, @RequestBody ResolvePaste req) {
        PasteEvent p = works.resolvePaste(currentUser.require(), pasteId, req.source(), req.registrationId());
        return Map.of("pasteId", p.getId(), "source", p.getSource().name());
    }

    @GetMapping("/{workId}/pastes/pending")
    public List<Map<String, Object>> pending(@PathVariable Long workId) {
        return works.pendingPastes(currentUser.require(), workId).stream()
                .map(p -> Map.<String, Object>of("id", p.getId(), "charCount", p.getCharCount(), "occurredAt", p.getOccurredAt()))
                .toList();
    }

    @GetMapping("/{workId}/snapshots")
    public List<SnapshotView> snapshots(@PathVariable Long workId) {
        AppUser actor = currentUser.require();
        access.requireWorkVisible(actor, works.require(workId));
        return snapshots.listOf(workId).stream()
                .map(s -> new SnapshotView(s.getSeqNo(), s.getTrigger().name(), s.getCharCount(), s.isKeyframe(), s.getCreatedAt(), s.getChainHash()))
                .toList();
    }

    @GetMapping("/{workId}/snapshots/{seqNo}")
    public Map<String, Object> snapshotText(@PathVariable Long workId, @PathVariable int seqNo) {
        AppUser actor = currentUser.require();
        access.requireWorkVisible(actor, works.require(workId));
        return Map.of("seqNo", seqNo, "text", snapshots.reconstruct(workId, seqNo));
    }

    @GetMapping("/{workId}/diff")
    public List<MyersDiff.LineChange> diff(@PathVariable Long workId, @RequestParam int from, @RequestParam int to) {
        AppUser actor = currentUser.require();
        access.requireWorkVisible(actor, works.require(workId));
        if (from == to) {
            throw new BadRequestException("请选择两个不同版本");
        }
        return snapshots.lineDiff(workId, from, to);
    }

    @GetMapping("/{workId}/timeline")
    public List<TimelineAssembler.Item> timeline(@PathVariable Long workId) {
        return timeline.build(currentUser.require(), workId);
    }

    @PostMapping("/{workId}/submit")
    public Declaration submit(@PathVariable Long workId, @RequestBody(required = false) SubmitRequest req) {
        boolean notUsed = req != null && req.declareNotUsed();
        return declarations.submit(currentUser.require(), workId, notUsed, LocalDateTime.now());
    }

    @GetMapping("/{workId}/declaration")
    public Declaration declaration(@PathVariable Long workId) {
        return declarations.view(currentUser.require(), workId);
    }
}
