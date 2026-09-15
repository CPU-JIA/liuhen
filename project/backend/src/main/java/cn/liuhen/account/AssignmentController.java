package cn.liuhen.account;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.liuhen.policy.PolicyService;
import cn.liuhen.security.AccessControl;
import cn.liuhen.security.CurrentUser;
import cn.liuhen.work.Work;
import cn.liuhen.work.WorkRepository;
import cn.liuhen.work.WorkService;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    public record WorkSummary(Long workId, Long studentId, String studentName, String status, int charCount,
                              java.time.LocalDateTime lastSavedAt, java.time.LocalDateTime submittedAt, boolean late) { }

    private final PolicyService policies;
    private final WorkService works;
    private final WorkRepository workRepo;
    private final AppUserRepository users;
    private final AccessControl access;
    private final CurrentUser currentUser;

    public AssignmentController(PolicyService policies, WorkService works, WorkRepository workRepo, AppUserRepository users,
                                AccessControl access, CurrentUser currentUser) {
        this.policies = policies;
        this.works = works;
        this.workRepo = workRepo;
        this.users = users;
        this.access = access;
        this.currentUser = currentUser;
    }

    /** 规则页数据。没配规则返回 204，前端直接进编辑器。 */
    @GetMapping("/{assignmentId}/policy")
    public ResponseEntity<PolicyService.PolicyView> policy(@PathVariable Long assignmentId) {
        AppUser actor = currentUser.require();
        Assignment assignment = access.requireEnrolledAssignment(actor, assignmentId);
        return policies.viewFor(actor, assignment).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{assignmentId}/policy/ack")
    public Map<String, Object> ack(@PathVariable Long assignmentId) {
        AppUser actor = currentUser.require();
        Assignment assignment = access.requireEnrolledAssignment(actor, assignmentId);
        policies.ack(actor, assignment);
        return Map.of("ok", true);
    }

    /** 学生进入作业，取到或新建自己的稿。 */
    @PostMapping("/{assignmentId}/work")
    public Map<String, Object> enter(@PathVariable Long assignmentId) {
        Work w = works.enter(currentUser.require(), assignmentId);
        return Map.of("workId", w.getId(), "status", w.getStatus().name(), "text", w.getCurrentText(),
                "charCount", w.getCharCount(), "lastSavedAt", w.getLastSavedAt() == null ? "" : w.getLastSavedAt().toString());
    }

    /** 教师看本作业下全班的稿。 */
    @GetMapping("/{assignmentId}/works")
    public List<WorkSummary> worksOf(@PathVariable Long assignmentId) {
        AppUser actor = currentUser.require();
        access.requireEnrolledAssignment(actor, assignmentId);
        if (actor.getRole() != Role.TEACHER) {
            throw new cn.liuhen.common.ApiExceptionHandler.ForbiddenException();
        }
        return workRepo.findByAssignmentIdOrderByStudentIdAsc(assignmentId).stream().map(w -> {
            String name = users.findById(w.getStudentId()).map(AppUser::getName).orElse("");
            return new WorkSummary(w.getId(), w.getStudentId(), name, w.getStatus().name(), w.getCharCount(),
                    w.getLastSavedAt(), w.getSubmittedAt(), w.isLate());
        }).toList();
    }
}
