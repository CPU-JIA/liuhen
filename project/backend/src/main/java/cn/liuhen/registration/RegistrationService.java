package cn.liuhen.registration;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.liuhen.account.AppUser;
import cn.liuhen.common.ApiExceptionHandler.BadRequestException;
import cn.liuhen.common.ApiExceptionHandler.ConflictException;
import cn.liuhen.common.ApiExceptionHandler.NotFoundException;
import cn.liuhen.evidence.HashChainService;
import cn.liuhen.security.AccessControl;
import cn.liuhen.work.Work;
import cn.liuhen.work.WorkService;
import cn.liuhen.work.WorkStatus;

/** 登记：追加式写入，进哈希链 work:{id}:registration。 */
@Service
public class RegistrationService {

    public record Input(Long toolId, String toolNameCustom, String toolVersion, Stage stage, String purpose, Adoption adoption,
                        String promptText, String outputText, Verification verification) { }

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int TEXT_MAX = 10_000;
    private static final int PURPOSE_MAX = 200;
    /** tool_name_custom 与 tool_version 列宽 50；超长在这里拒绝而不是让数据库报错（实验 6 单测发现）。 */
    private static final int SHORT_MAX = 50;

    private final RegistrationRepository registrations;
    private final AiToolRepository tools;
    private final WorkService works;
    private final HashChainService hashChain;
    private final AccessControl access;

    public RegistrationService(RegistrationRepository registrations, AiToolRepository tools, WorkService works,
                               HashChainService hashChain, AccessControl access) {
        this.registrations = registrations;
        this.tools = tools;
        this.works = works;
        this.hashChain = hashChain;
        this.access = access;
    }

    public List<AiTool> presetTools() {
        return tools.findAllByOrderByIdAsc();
    }

    @Transactional
    public Registration create(AppUser actor, Long workId, Input in, LocalDateTime now) {
        return append(actor, workId, in, null, now);
    }

    /** 修改 = 新增一条并作废旧条（AC-REG-02-1）。旧条的作废是数据库唯一放行的更新。 */
    @Transactional
    public Registration supersede(AppUser actor, Long workId, Long oldId, Input in, LocalDateTime now) {
        Registration old = registrations.findById(oldId).orElseThrow(() -> new NotFoundException("登记不存在"));
        if (!old.getWorkId().equals(workId)) {
            throw new BadRequestException("登记不属于该作业稿");
        }
        if (old.getStatus() == RegStatus.VOIDED) {
            throw new ConflictException("该登记已作废");
        }
        Registration created = append(actor, workId, in, oldId, now);
        old.markVoided();
        return created;
    }

    @Transactional
    public void voidRegistration(AppUser actor, Long workId, Long id) {
        Work work = works.require(workId);
        access.requireWorkEditable(actor, work);
        Registration reg = registrations.findById(id).orElseThrow(() -> new NotFoundException("登记不存在"));
        if (!reg.getWorkId().equals(workId)) {
            throw new BadRequestException("登记不属于该作业稿");
        }
        reg.markVoided();
    }

    public List<Registration> listOf(AppUser actor, Long workId) {
        Work work = works.require(workId);
        access.requireWorkVisible(actor, work);
        return registrations.findByWorkIdOrderByCreatedAtAsc(workId);
    }

    public List<Registration> activeOf(Long workId) {
        return registrations.findByWorkIdAndStatusOrderByCreatedAtAsc(workId, RegStatus.ACTIVE);
    }

    private Registration append(AppUser actor, Long workId, Input in, Long supersedesId, LocalDateTime now) {
        Work work = works.require(workId);
        access.requireWorkEditable(actor, work);
        if (work.getStatus() == WorkStatus.SUBMITTED) {
            throw new ConflictException("已提交的作业不能再登记");
        }
        validate(in);
        String prev = registrations.findFirstByWorkIdOrderByCreatedAtDescIdDesc(workId)
                .map(Registration::getChainHash).orElse(HashChainService.GENESIS);
        // 字段间用不会出现在内容里的分隔符，避免 "ab"+"c" 与 "a"+"bc" 得到同一哈希（走查第 7 条）
        String payload = hashChain.sha256Hex(String.join("\u001f",
                String.valueOf(in.toolId()), nz(in.toolNameCustom()), nz(in.toolVersion()), in.stage().name(), in.purpose(),
                in.adoption().name(), nz(in.promptText()), nz(in.outputText()), in.verification() == null ? "" : in.verification().name()));
        String chain = hashChain.append(prev, payload, now.atZone(ZONE).toInstant()).chainHash();
        Registration reg = new Registration(workId, in.toolId(), blankToNull(in.toolNameCustom()), blankToNull(in.toolVersion()),
                in.stage(), in.purpose().trim(), in.adoption(), blankToNull(in.promptText()), blankToNull(in.outputText()),
                in.verification(), supersedesId, prev, chain, now);
        return registrations.save(reg);
    }

    private void validate(Input in) {
        boolean hasTool = in.toolId() != null || (in.toolNameCustom() != null && !in.toolNameCustom().isBlank());
        if (!hasTool) {
            throw new BadRequestException("请选择工具或填写工具名");
        }
        if (in.toolId() != null && !tools.existsById(in.toolId())) {
            throw new BadRequestException("工具不存在");
        }
        if (in.stage() == null) {
            throw new BadRequestException("请选择使用环节");
        }
        if (in.purpose() == null || in.purpose().isBlank()) {
            throw new BadRequestException("请填写用途");
        }
        if (in.purpose().codePointCount(0, in.purpose().length()) > PURPOSE_MAX) {
            throw new BadRequestException("用途不超过 " + PURPOSE_MAX + " 字符");
        }
        if (overShort(in.toolNameCustom()) || overShort(in.toolVersion())) {
            throw new BadRequestException("工具名与版本各不超过 " + SHORT_MAX + " 字符");
        }
        if (in.adoption() == null) {
            throw new BadRequestException("请选择采用方式");
        }
        if (tooLong(in.promptText()) || tooLong(in.outputText())) {
            throw new BadRequestException("提示词与输出各不超过 " + TEXT_MAX + " 字符");
        }
    }

    private static boolean overShort(String s) {
        return s != null && s.codePointCount(0, s.length()) > SHORT_MAX;
    }

    private static boolean tooLong(String s) {
        return s != null && s.codePointCount(0, s.length()) > TEXT_MAX;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
