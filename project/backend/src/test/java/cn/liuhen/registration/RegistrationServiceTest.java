package cn.liuhen.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
import cn.liuhen.account.AppUser;
import cn.liuhen.account.Role;
import cn.liuhen.common.ApiExceptionHandler.BadRequestException;
import cn.liuhen.common.ApiExceptionHandler.ConflictException;
import cn.liuhen.common.ApiExceptionHandler.ForbiddenException;
import cn.liuhen.evidence.HashChainService;
import cn.liuhen.security.AccessControl;
import cn.liuhen.work.Work;
import cn.liuhen.work.WorkService;

/** 登记校验、哈希链、追加式修改（REG-01、REG-02）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 11, 0);

    @Mock
    private RegistrationRepository registrations;
    @Mock
    private AiToolRepository tools;
    @Mock
    private WorkService works;
    @Mock
    private AccessControl access;

    private final HashChainService hashChain = new HashChainService();
    private final List<Registration> store = new ArrayList<>();
    private RegistrationService service;
    private final AppUser student = TestSupport.withId(new AppUser("24020110", "周浩然", Role.STUDENT, "hash", false), 10L);
    private Work work;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(registrations, tools, works, hashChain, access);
        work = TestSupport.withId(new Work(5L, 10L), 1L);
        when(works.require(1L)).thenReturn(work);
        when(tools.existsById(1L)).thenReturn(true);
        when(registrations.save(any())).thenAnswer(inv -> {
            Registration r = TestSupport.withId(inv.getArgument(0), store.size() + 1);
            store.add(r);
            return r;
        });
        when(registrations.findFirstByWorkIdOrderByCreatedAtDescIdDesc(anyLong())).thenAnswer(inv ->
                store.isEmpty() ? Optional.empty() : Optional.of(store.get(store.size() - 1)));
        when(registrations.findById(anyLong())).thenAnswer(inv ->
                store.stream().filter(r -> r.getId().equals(inv.getArgument(0))).findFirst());
    }

    private static RegistrationService.Input input(Long toolId, String custom, String version, Stage stage, String purpose,
                                                   Adoption adoption, String prompt, String output, Verification verification) {
        return new RegistrationService.Input(toolId, custom, version, stage, purpose, adoption, prompt, output, verification);
    }

    private static RegistrationService.Input minimal() {
        return input(1L, null, null, Stage.POLISH, "改语法", Adoption.MODIFIED, null, null, null);
    }

    // ---------------------------------------------------------------- 校验 AC-REG-01-1、01-2、01-3

    @Test
    void fourRequiredFieldsAreEnough() {
        Registration r = service.create(student, 1L, minimal(), NOW);
        assertEquals(RegStatus.ACTIVE, r.getStatus());
        assertEquals("改语法", r.getPurpose());
        assertNull(r.getPromptText());
        assertNull(r.getSupersedesId());
    }

    @Test
    void toolIdOrCustomNameIsRequired() {
        assertThrows(BadRequestException.class, () -> service.create(student, 1L,
                input(null, " ", null, Stage.POLISH, "改语法", Adoption.MODIFIED, null, null, null), NOW));
        Registration r = service.create(student, 1L,
                input(null, "校内模型", null, Stage.POLISH, "改语法", Adoption.MODIFIED, null, null, null), NOW);
        assertEquals("校内模型", r.getToolNameCustom());
    }

    @Test
    void unknownToolIdIsRejected() {
        when(tools.existsById(99L)).thenReturn(false);
        assertThrows(BadRequestException.class, () -> service.create(student, 1L,
                input(99L, null, null, Stage.POLISH, "改语法", Adoption.MODIFIED, null, null, null), NOW));
    }

    @Test
    void stagePurposeAdoptionAreRequired() {
        assertThrows(BadRequestException.class, () -> service.create(student, 1L,
                input(1L, null, null, null, "改语法", Adoption.MODIFIED, null, null, null), NOW));
        assertThrows(BadRequestException.class, () -> service.create(student, 1L,
                input(1L, null, null, Stage.POLISH, "  ", Adoption.MODIFIED, null, null, null), NOW));
        assertThrows(BadRequestException.class, () -> service.create(student, 1L,
                input(1L, null, null, Stage.POLISH, "改语法", null, null, null, null), NOW));
        verify(registrations, never()).save(any());
    }

    @Test
    void purposeAllowsTwoHundredCodePointsNotMore() {
        service.create(student, 1L, input(1L, null, null, Stage.POLISH, "😀".repeat(200), Adoption.MODIFIED, null, null, null), NOW);
        assertThrows(BadRequestException.class, () -> service.create(student, 1L,
                input(1L, null, null, Stage.POLISH, "用".repeat(201), Adoption.MODIFIED, null, null, null), NOW));
    }

    @Test
    void promptAndOutputAllowTenThousandCodePointsNotMore() {
        service.create(student, 1L, input(1L, null, null, Stage.POLISH, "改语法", Adoption.MODIFIED, "问".repeat(10_000), "答".repeat(10_000), null), NOW);
        assertThrows(BadRequestException.class, () -> service.create(student, 1L,
                input(1L, null, null, Stage.POLISH, "改语法", Adoption.MODIFIED, "问".repeat(10_001), null, null), NOW));
        assertThrows(BadRequestException.class, () -> service.create(student, 1L,
                input(1L, null, null, Stage.POLISH, "改语法", Adoption.MODIFIED, null, "答".repeat(10_001), null), NOW));
    }

    @Test
    void customToolNameAndVersionAreBoundedToFifty() {
        assertThrows(BadRequestException.class, () -> service.create(student, 1L,
                input(null, "名".repeat(51), null, Stage.POLISH, "改语法", Adoption.MODIFIED, null, null, null), NOW),
                "超过列宽应被拒，而不是打到数据库报 500");
        assertThrows(BadRequestException.class, () -> service.create(student, 1L,
                input(1L, null, "v".repeat(51), Stage.POLISH, "改语法", Adoption.MODIFIED, null, null, null), NOW));
    }

    @Test
    void blankOptionalFieldsBecomeNullAndPurposeIsTrimmed() {
        Registration r = service.create(student, 1L,
                input(1L, "", " ", Stage.POLISH, " 改语法 ", Adoption.MODIFIED, "", "", Verification.NONE), NOW);
        assertNull(r.getToolNameCustom());
        assertNull(r.getToolVersion());
        assertNull(r.getPromptText());
        assertNull(r.getOutputText());
        assertEquals("改语法", r.getPurpose());
        assertEquals(Verification.NONE, r.getVerification());
    }

    @Test
    void submittedWorkCannotRegister() {
        work.submit(NOW, false);
        assertThrows(ConflictException.class, () -> service.create(student, 1L, minimal(), NOW));
    }

    @Test
    void forbiddenActorCannotRegister() {
        doThrow(new ForbiddenException()).when(access).requireWorkEditable(any(), any());
        assertThrows(ForbiddenException.class, () -> service.create(student, 1L, minimal(), NOW));
        verify(registrations, never()).save(any());
    }

    // ---------------------------------------------------------------- 哈希链

    @Test
    void firstRegistrationChainsFromGenesisAndSecondFromFirst() {
        Registration first = service.create(student, 1L, minimal(), NOW);
        Registration second = service.create(student, 1L, minimal(), NOW.plusMinutes(1));
        assertEquals(HashChainService.GENESIS, first.getPrevChainHash());
        assertEquals(first.getChainHash(), second.getPrevChainHash());
        assertNotEquals(first.getChainHash(), second.getChainHash());
    }

    @Test
    void payloadFieldsAreSeparatedSoShiftedContentDiffers() {
        // 走查第 7 条：拼接无分隔符时 "ab"+"c" 与 "a"+"bc" 同哈希
        Registration ab_c = service.create(student, 1L,
                input(null, "ab", "c", Stage.POLISH, "改语法", Adoption.MODIFIED, null, null, null), NOW);
        store.clear();
        Registration a_bc = service.create(student, 1L,
                input(null, "a", "bc", Stage.POLISH, "改语法", Adoption.MODIFIED, null, null, null), NOW);
        assertNotEquals(ab_c.getChainHash(), a_bc.getChainHash());
    }

    // ---------------------------------------------------------------- 追加式修改 AC-REG-02-1、02-2

    @Test
    void supersedeVoidsOldAndLinksNew() {
        Registration old = service.create(student, 1L, minimal(), NOW);
        Registration created = service.supersede(student, 1L, old.getId(),
                input(1L, null, null, Stage.OUTLINE, "生成大纲", Adoption.REFERENCE, null, null, null), NOW.plusMinutes(1));
        assertEquals(RegStatus.VOIDED, old.getStatus());
        assertEquals(old.getId(), created.getSupersedesId());
        assertEquals(RegStatus.ACTIVE, created.getStatus());
        assertEquals(old.getChainHash(), created.getPrevChainHash(), "作废的旧条仍在链上");
    }

    @Test
    void supersedeRefusesRegistrationOfAnotherWork() {
        Registration old = service.create(student, 1L, minimal(), NOW);
        assertThrows(BadRequestException.class, () -> service.supersede(student, 2L, old.getId(), minimal(), NOW));
    }

    @Test
    void supersedeRefusesAlreadyVoided() {
        Registration old = service.create(student, 1L, minimal(), NOW);
        old.markVoided();
        assertThrows(ConflictException.class, () -> service.supersede(student, 1L, old.getId(), minimal(), NOW));
    }

    @Test
    void voidIsAStatusChangeNotADelete() {
        Registration r = service.create(student, 1L, minimal(), NOW);
        service.voidRegistration(student, 1L, r.getId());
        assertEquals(RegStatus.VOIDED, r.getStatus());
        assertEquals(1, store.size());
        verify(registrations, never()).delete(any());
        verify(registrations, never()).deleteById(any());
    }

    @Test
    void voidRefusesRegistrationOfAnotherWork() {
        Registration r = service.create(student, 1L, minimal(), NOW);
        assertThrows(BadRequestException.class, () -> service.voidRegistration(student, 2L, r.getId()));
        assertEquals(RegStatus.ACTIVE, r.getStatus());
    }
}
