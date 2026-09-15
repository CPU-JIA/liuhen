package cn.liuhen.policy;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import cn.liuhen.registration.Registration;

/**
 * 规则引擎（手写）。三件事：找当前生效版本、判一条登记是否超出允许场景、判学生是否需要重新确认。
 * <p>
 * 作业级规则优先于课程级（Sprint 2 的 RULE-05 只需往 policy_version 插 scopeAssignmentId 非 0 的行，本类不用改）。
 * 声明按"提交时生效版本"对照（K-2），所以调用方要把提交那一刻取到的版本传进来，而不是在这里再查一次。
 */
@Service
public class PolicyRuleEngine {

    public record SceneCheck(boolean allowed, String sceneName, Tier tier, boolean explicit) { }

    private final PolicyVersionRepository versions;
    private final PolicySceneRepository scenes;
    private final PolicyAckRepository acks;

    public PolicyRuleEngine(PolicyVersionRepository versions, PolicySceneRepository scenes, PolicyAckRepository acks) {
        this.versions = versions;
        this.scenes = scenes;
        this.acks = acks;
    }

    /** 当前生效版本：先看作业级，再看课程级；课程没配规则则为空。 */
    public Optional<PolicyVersion> current(Long courseId, Long assignmentId) {
        if (assignmentId != null) {
            Optional<PolicyVersion> assignmentLevel =
                    versions.findFirstByCourseIdAndScopeAssignmentIdOrderByVersionNoDesc(courseId, assignmentId);
            if (assignmentLevel.isPresent()) {
                return assignmentLevel;
            }
        }
        return versions.findFirstByCourseIdAndScopeAssignmentIdOrderByVersionNoDesc(courseId, PolicyVersion.COURSE_SCOPE);
    }

    public List<PolicyScene> scenesOf(PolicyVersion version) {
        return scenes.findByKeyPolicyVersionId(version.getId());
    }

    /**
     * 判定规则：
     * FORBID 一律不允许；ENCOURAGE 默认允许，除非场景被显式关掉；DECLARE 只允许显式勾选的场景。
     * 未勾选也未关掉的场景在 DECLARE 下视为不允许，explicit = false 让界面能区分"老师明确禁止"和"老师没提"。
     */
    public SceneCheck check(Registration registration, PolicyVersion version, List<PolicyScene> versionScenes) {
        String sceneName = registration.getStage().sceneName();
        Optional<PolicyScene> match = versionScenes.stream()
                .filter(s -> s.getSceneName().equals(sceneName))
                .findFirst();
        boolean explicit = match.isPresent();
        boolean allowed = switch (version.getTier()) {
            case FORBID -> false;
            case ENCOURAGE -> match.map(PolicyScene::isAllowed).orElse(true);
            case DECLARE -> match.map(PolicyScene::isAllowed).orElse(false);
        };
        return new SceneCheck(allowed, sceneName, version.getTier(), explicit);
    }

    /** 没有对当前版本的确认记录就要重新弹规则页（AC-RULE-01-6、AC-RULE-02-2）。 */
    public boolean needsReack(Long studentId, Long assignmentId, PolicyVersion current) {
        return !acks.existsByPolicyVersionIdAndStudentIdAndAssignmentId(current.getId(), studentId, assignmentId);
    }
}
