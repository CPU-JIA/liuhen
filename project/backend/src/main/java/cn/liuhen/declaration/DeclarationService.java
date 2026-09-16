package cn.liuhen.declaration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.liuhen.account.AppUser;
import cn.liuhen.account.Assignment;
import cn.liuhen.account.AssignmentRepository;
import cn.liuhen.common.ApiExceptionHandler.BadRequestException;
import cn.liuhen.common.ApiExceptionHandler.ConflictException;
import cn.liuhen.common.ApiExceptionHandler.NotFoundException;
import cn.liuhen.evidence.HashChainService;
import cn.liuhen.policy.PolicyRuleEngine;
import cn.liuhen.policy.PolicyScene;
import cn.liuhen.policy.PolicyVersion;
import cn.liuhen.registration.AiToolRepository;
import cn.liuhen.registration.Registration;
import cn.liuhen.registration.RegistrationService;
import cn.liuhen.security.AccessControl;
import cn.liuhen.work.PasteEventRepository;
import cn.liuhen.work.PasteSource;
import cn.liuhen.work.SnapshotService;
import cn.liuhen.work.SnapshotTrigger;
import cn.liuhen.work.Work;
import cn.liuhen.work.WorkService;
import cn.liuhen.work.WorkStatus;

/**
 * 声明生成器（手写业务逻辑）。提交时执行，顺序固定：
 * 阻断待定粘贴 → 取提交时生效的规则版本 → 汇总 ACTIVE 登记 → 逐条对照场景 → 判逾期 → 生成 JSON 与哈希 → SUBMIT 快照 → 置 SUBMITTED。
 * 七要素：工具、版本、环节、用途、提示词、核对方式、采用方式；缺失显示"未填写"（AC-DECL-01-1）。
 * 有 AI 来源粘贴却无登记时不允许"未使用"（AC-DECL-02-2）。
 */
@Service
public class DeclarationService {

    private static final String NOT_FILLED = "未填写";

    private final DeclarationRepository declarations;
    private final AssignmentRepository assignments;
    private final PasteEventRepository pastes;
    private final AiToolRepository tools;
    private final RegistrationService registrations;
    private final PolicyRuleEngine policy;
    private final SnapshotService snapshots;
    private final WorkService works;
    private final HashChainService hashChain;
    private final AccessControl access;
    private final ObjectMapper json;

    public DeclarationService(DeclarationRepository declarations, AssignmentRepository assignments, PasteEventRepository pastes,
                              AiToolRepository tools, RegistrationService registrations, PolicyRuleEngine policy,
                              SnapshotService snapshots, WorkService works, HashChainService hashChain, AccessControl access,
                              ObjectMapper json) {
        this.declarations = declarations;
        this.assignments = assignments;
        this.pastes = pastes;
        this.tools = tools;
        this.registrations = registrations;
        this.policy = policy;
        this.snapshots = snapshots;
        this.works = works;
        this.hashChain = hashChain;
        this.access = access;
        this.json = json;
    }

    @Transactional
    public Declaration submit(AppUser actor, Long workId, boolean declareNotUsed, LocalDateTime now) {
        Work work = works.require(workId);
        access.requireWorkEditable(actor, work);
        if (work.getStatus() == WorkStatus.SUBMITTED) {
            throw new ConflictException("已经提交过");
        }
        if (pastes.existsByWorkIdAndSource(workId, PasteSource.PENDING)) {
            throw new ConflictException("还有粘贴内容没有选择来源，请先补填");
        }
        Assignment assignment = assignments.findById(work.getAssignmentId()).orElseThrow(() -> new NotFoundException("作业不存在"));
        PolicyVersion version = policy.current(assignment.getCourseId(), assignment.getId())
                .orElseThrow(() -> new ConflictException("本课程尚未配置 AI 使用规则，请联系老师"));
        List<PolicyScene> scenes = policy.scenesOf(version);
        List<Registration> active = registrations.activeOf(workId);
        boolean hasAiPaste = pastes.existsByWorkIdAndSource(workId, PasteSource.AI_TOOL);

        DeclarationKind kind;
        if (active.isEmpty()) {
            if (hasAiPaste) {
                throw new ConflictException("存在来源为 AI 工具的粘贴，请先登记后再提交");
            }
            if (!declareNotUsed) {
                throw new BadRequestException("没有登记记录，请勾选未使用 AI 承诺");
            }
            kind = DeclarationKind.NOT_USED;
        } else {
            if (declareNotUsed) {
                throw new BadRequestException("已有 AI 使用登记，不能同时承诺未使用；请先作废登记或取消勾选");
            }
            kind = DeclarationKind.AI_USED;
        }

        boolean late = now.isAfter(assignment.getDeadline());
        Map<String, Object> content = buildContent(actor, assignment, version, scenes, active, kind, late, now);
        String contentJson = toJson(content);
        String contentHash = hashChain.sha256Hex(contentJson);

        snapshots.maybeCreate(work, SnapshotTrigger.SUBMIT, now);
        work.submit(now, late);
        return declarations.save(new Declaration(workId, kind, version.getId(), now, late, contentJson, contentHash));
    }

    public Declaration view(AppUser actor, Long workId) {
        Work work = works.require(workId);
        access.requireWorkVisible(actor, work);
        return declarations.findByWorkId(workId).orElseThrow(() -> new NotFoundException("尚未提交，没有声明"));
    }

    private Map<String, Object> buildContent(AppUser actor, Assignment assignment, PolicyVersion version, List<PolicyScene> scenes,
                                             List<Registration> active, DeclarationKind kind, boolean late, LocalDateTime now) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("kind", kind.name());
        root.put("title", kind == DeclarationKind.AI_USED ? "AI 使用声明" : "未使用 AI 工具承诺");
        root.put("student", Map.of("loginNo", actor.getLoginNo(), "name", actor.getName()));
        root.put("assignment", Map.of("id", assignment.getId(), "title", assignment.getTitle(), "deadline", assignment.getDeadline().toString()));
        root.put("submittedAt", now.toString());
        root.put("late", late);
        root.put("policy", Map.of("versionNo", version.getVersionNo(), "tier", version.getTier().name(),
                "lastModifiedAt", version.getCreatedAt() == null ? now.toString() : version.getCreatedAt().toString()));
        List<Map<String, Object>> items = new ArrayList<>();
        int exceeded = 0;
        for (Registration r : active) {
            PolicyRuleEngine.SceneCheck check = policy.check(r, version, scenes);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tool", toolName(r));
            item.put("version", orNotFilled(r.getToolVersion()));
            item.put("stage", r.getStage().sceneName());
            item.put("purpose", r.getPurpose());
            item.put("prompt", orNotFilled(r.getPromptText()));
            item.put("verification", r.getVerification() == null ? NOT_FILLED : r.getVerification().label());
            item.put("adoption", r.getAdoption().label());
            item.put("exceedsPolicy", !check.allowed());
            item.put("registeredAt", r.getCreatedAt().toString());
            if (!check.allowed()) {
                exceeded++;
            }
            items.add(item);
        }
        root.put("items", items);
        root.put("exceededCount", exceeded);
        if (kind == DeclarationKind.NOT_USED) {
            root.put("pledge", "本人承诺本次作业未使用任何生成式 AI 工具。");
        }
        return root;
    }

    private String toolName(Registration r) {
        if (r.getToolId() != null) {
            return tools.findById(r.getToolId()).map(t -> t.getName()).orElse(NOT_FILLED);
        }
        return orNotFilled(r.getToolNameCustom());
    }

    private static String orNotFilled(String s) {
        return s == null || s.isBlank() ? NOT_FILLED : s;
    }

    private String toJson(Map<String, Object> content) {
        try {
            return json.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("声明序列化失败", e);
        }
    }
}
