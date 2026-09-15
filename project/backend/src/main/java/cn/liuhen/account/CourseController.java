package cn.liuhen.account;

import java.io.IOException;
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
import org.springframework.web.multipart.MultipartFile;

import cn.liuhen.common.ApiExceptionHandler.BadRequestException;
import cn.liuhen.policy.PolicyService;
import cn.liuhen.policy.PolicyVersion;
import cn.liuhen.policy.Tier;
import cn.liuhen.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    public record CreateCourse(@NotBlank String name) { }

    public record JoinRequest(@NotBlank String joinCode) { }

    public record CreateAssignment(@NotBlank String title, @NotNull LocalDateTime deadline) { }

    public record PublishPolicy(@NotNull Tier tier, String gradingNote, List<PolicyService.SceneInput> scenes) { }

    public record CourseView(Long id, String name, String joinCode, Long teacherId, LocalDateTime createdAt) {
        static CourseView of(Course c) {
            return new CourseView(c.getId(), c.getName(), c.getJoinCode(), c.getTeacherId(), c.getCreatedAt());
        }
    }

    private final AccountService accounts;
    private final PolicyService policies;
    private final CurrentUser currentUser;

    public CourseController(AccountService accounts, PolicyService policies, CurrentUser currentUser) {
        this.accounts = accounts;
        this.policies = policies;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<CourseView> mine() {
        return accounts.myCourses(currentUser.require()).stream().map(CourseView::of).toList();
    }

    @PostMapping
    public CourseView create(@Valid @RequestBody CreateCourse req) {
        return CourseView.of(accounts.createCourse(currentUser.require(), req.name()));
    }

    @PostMapping("/join")
    public CourseView join(@Valid @RequestBody JoinRequest req) {
        return CourseView.of(accounts.join(currentUser.require(), req.joinCode()));
    }

    @GetMapping("/{courseId}/assignments")
    public List<Assignment> assignments(@PathVariable Long courseId) {
        return accounts.assignmentsOf(currentUser.require(), courseId);
    }

    @PostMapping("/{courseId}/assignments")
    public Assignment createAssignment(@PathVariable Long courseId, @Valid @RequestBody CreateAssignment req) {
        return accounts.createAssignment(currentUser.require(), courseId, req.title(), req.deadline(), LocalDateTime.now());
    }

    @PostMapping("/{courseId}/roster")
    public AccountService.RosterResult roster(@PathVariable Long courseId, @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请上传名单文件");
        }
        try {
            return accounts.importRoster(currentUser.require(), courseId, file.getOriginalFilename(), file.getInputStream());
        } catch (IOException e) {
            throw new BadRequestException("名单文件读取失败");
        }
    }

    @PutMapping("/{courseId}/policy")
    public Map<String, Object> publishPolicy(@PathVariable Long courseId, @Valid @RequestBody PublishPolicy req) {
        PolicyVersion v = policies.publish(currentUser.require(), courseId, req.tier(), req.gradingNote(), req.scenes());
        return Map.of("versionId", v.getId(), "versionNo", v.getVersionNo(), "tier", v.getTier().name());
    }
}
