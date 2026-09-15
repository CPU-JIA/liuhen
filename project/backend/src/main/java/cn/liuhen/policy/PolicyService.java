package cn.liuhen.policy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.liuhen.account.AppUser;
import cn.liuhen.account.Assignment;
import cn.liuhen.account.Course;
import cn.liuhen.common.ApiExceptionHandler.BadRequestException;
import cn.liuhen.security.AccessControl;

/** 规则的写入与读取。写入永远是新版本，不更新旧行。 */
@Service
public class PolicyService {

    public record SceneInput(String name, boolean allowed) { }

    public record PolicyView(Long versionId, int versionNo, Tier tier, String gradingNote,
                             List<Map<String, Object>> scenes, boolean acked, java.time.LocalDateTime updatedAt) { }

    private static final int SCENE_NAME_MAX = 30;

    private final PolicyVersionRepository versions;
    private final PolicySceneRepository scenes;
    private final PolicyAckRepository acks;
    private final PolicyRuleEngine engine;
    private final AccessControl access;

    public PolicyService(PolicyVersionRepository versions, PolicySceneRepository scenes, PolicyAckRepository acks,
                         PolicyRuleEngine engine, AccessControl access) {
        this.versions = versions;
        this.scenes = scenes;
        this.acks = acks;
        this.engine = engine;
        this.access = access;
    }

    @Transactional
    public PolicyVersion publish(AppUser actor, Long courseId, Tier tier, String gradingNote, List<SceneInput> sceneInputs) {
        Course course = access.requirePolicyOwner(actor, courseId);
        if (tier == null) {
            throw new BadRequestException("请先选择档位");
        }
        if (gradingNote != null && gradingNote.length() > 200) {
            throw new BadRequestException("评分态度不超过 200 字符");
        }
        int next = versions.findFirstByCourseIdAndScopeAssignmentIdOrderByVersionNoDesc(course.getId(), PolicyVersion.COURSE_SCOPE)
                .map(v -> v.getVersionNo() + 1).orElse(1);
        PolicyVersion saved = versions.save(new PolicyVersion(course.getId(), PolicyVersion.COURSE_SCOPE, next, tier, gradingNote, actor.getId()));
        if (sceneInputs != null) {
            for (SceneInput s : sceneInputs) {
                if (s.name() == null || s.name().isBlank()) {
                    throw new BadRequestException("场景名不能为空");
                }
                if (s.name().length() > SCENE_NAME_MAX) {
                    throw new BadRequestException("场景名不超过 30 字符");
                }
                scenes.save(new PolicyScene(saved.getId(), s.name().trim(), s.allowed()));
            }
        }
        return saved;
    }

    /** 学生进作业时看到的规则页数据；acked 为 false 时前端必须先展示规则页。 */
    public Optional<PolicyView> viewFor(AppUser actor, Assignment assignment) {
        return engine.current(assignment.getCourseId(), assignment.getId()).map(v -> {
            List<Map<String, Object>> sceneList = engine.scenesOf(v).stream()
                    .map(s -> Map.<String, Object>of("name", s.getSceneName(), "allowed", s.isAllowed()))
                    .toList();
            boolean acked = !engine.needsReack(actor.getId(), assignment.getId(), v);
            return new PolicyView(v.getId(), v.getVersionNo(), v.getTier(), v.getGradingNote(), sceneList, acked, v.getCreatedAt());
        });
    }

    @Transactional
    public void ack(AppUser actor, Assignment assignment) {
        PolicyVersion current = engine.current(assignment.getCourseId(), assignment.getId())
                .orElseThrow(() -> new BadRequestException("本课程尚未配置规则"));
        if (engine.needsReack(actor.getId(), assignment.getId(), current)) {
            acks.save(new PolicyAck(current.getId(), actor.getId(), assignment.getId()));
        }
    }
}
