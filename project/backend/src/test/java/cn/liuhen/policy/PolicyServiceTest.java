package cn.liuhen.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import cn.liuhen.account.Assignment;
import cn.liuhen.account.Course;
import cn.liuhen.account.Role;
import cn.liuhen.common.ApiExceptionHandler.BadRequestException;
import cn.liuhen.security.AccessControl;

/** 规则发布只追加版本、场景校验、确认记录（RULE-01、RULE-02、RULE-03）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicyServiceTest {

    @Mock
    private PolicyVersionRepository versions;
    @Mock
    private PolicySceneRepository scenes;
    @Mock
    private PolicyAckRepository acks;
    @Mock
    private AccessControl access;

    private PolicyService service;
    private final List<PolicyScene> savedScenes = new ArrayList<>();
    private final AppUser teacher = TestSupport.withId(new AppUser("T0001", "T老师", Role.TEACHER, "hash", false), 1L);
    private final AppUser student = TestSupport.withId(new AppUser("24020110", "周浩然", Role.STUDENT, "hash", false), 10L);
    private final Course course = TestSupport.withId(new Course("软件工程", "ABC234", 1L), 100L);
    private final Assignment assignment = TestSupport.withId(new Assignment(100L, "实验 1", LocalDateTime.of(2026, 10, 1, 0, 0)), 5L);

    @BeforeEach
    void setUp() {
        service = new PolicyService(versions, scenes, acks, new PolicyRuleEngine(versions, scenes, acks), access);
        when(access.requirePolicyOwner(teacher, 100L)).thenReturn(course);
        when(versions.save(any())).thenAnswer(inv -> TestSupport.withId(inv.getArgument(0), 20L));
        when(scenes.save(any())).thenAnswer(inv -> {
            savedScenes.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(versions.findFirstByCourseIdAndScopeAssignmentIdOrderByVersionNoDesc(anyLong(), anyLong())).thenReturn(Optional.empty());
    }

    private static List<PolicyService.SceneInput> scenes(String... names) {
        List<PolicyService.SceneInput> list = new ArrayList<>();
        for (String n : names) {
            list.add(new PolicyService.SceneInput(n, true));
        }
        return list;
    }

    @Test
    void firstPublishIsVersionOne() {
        PolicyVersion v = service.publish(teacher, 100L, Tier.DECLARE, "如实声明不扣分", scenes("改语法", "生成大纲"));
        assertEquals(1, v.getVersionNo());
        assertEquals(Tier.DECLARE, v.getTier());
        assertEquals(PolicyVersion.COURSE_SCOPE, v.getScopeAssignmentId());
        assertEquals(2, savedScenes.size());
        assertEquals("改语法", savedScenes.get(0).getSceneName());
    }

    @Test
    void republishAppendsNextVersionInsteadOfUpdating() {
        PolicyVersion v2 = new PolicyVersion(100L, PolicyVersion.COURSE_SCOPE, 2, Tier.ENCOURAGE, null, 1L);
        when(versions.findFirstByCourseIdAndScopeAssignmentIdOrderByVersionNoDesc(100L, PolicyVersion.COURSE_SCOPE)).thenReturn(Optional.of(v2));
        PolicyVersion v3 = service.publish(teacher, 100L, Tier.FORBID, null, null);
        assertEquals(3, v3.getVersionNo());
        assertEquals(Tier.ENCOURAGE, v2.getTier(), "旧版本原样保留");
    }

    @Test
    void tierIsRequired() {
        assertThrows(BadRequestException.class, () -> service.publish(teacher, 100L, null, null, null));
        verify(versions, never()).save(any());
    }

    @Test
    void gradingNoteAllowsTwoHundredCodePointsNotMore() {
        service.publish(teacher, 100L, Tier.DECLARE, "😀".repeat(200), null);
        assertThrows(BadRequestException.class, () -> service.publish(teacher, 100L, Tier.DECLARE, "态".repeat(201), null));
    }

    @Test
    void sceneNameIsRequiredTrimmedAndBoundedToThirty() {
        assertThrows(BadRequestException.class, () -> service.publish(teacher, 100L, Tier.DECLARE, null, scenes("  ")));
        assertThrows(BadRequestException.class, () -> service.publish(teacher, 100L, Tier.DECLARE, null, scenes("景".repeat(31))));
        service.publish(teacher, 100L, Tier.DECLARE, null, scenes(" 生成测试用例 "));
        assertEquals("生成测试用例", savedScenes.get(0).getSceneName());
    }

    @Test
    void duplicateSceneNamesInOnePublishAreRejected() {
        assertThrows(BadRequestException.class, () -> service.publish(teacher, 100L, Tier.DECLARE, null, scenes("改语法", "改语法")),
                "同名场景是复合主键冲突，应在保存前拒绝而不是报 500");
    }

    @Test
    void viewReportsWhetherStudentAckedCurrentVersion() {
        PolicyVersion v = TestSupport.withId(new PolicyVersion(100L, PolicyVersion.COURSE_SCOPE, 1, Tier.DECLARE, "备注", 1L), 20L);
        when(versions.findFirstByCourseIdAndScopeAssignmentIdOrderByVersionNoDesc(100L, PolicyVersion.COURSE_SCOPE)).thenReturn(Optional.of(v));
        when(scenes.findByKeyPolicyVersionId(20L)).thenReturn(List.of(new PolicyScene(20L, "改语法", true)));
        when(acks.existsByPolicyVersionIdAndStudentIdAndAssignmentId(20L, 10L, 5L)).thenReturn(false);
        PolicyService.PolicyView view = service.viewFor(student, assignment).orElseThrow();
        assertFalse(view.acked());
        assertEquals(1, view.scenes().size());
        assertEquals("备注", view.gradingNote());
        when(acks.existsByPolicyVersionIdAndStudentIdAndAssignmentId(20L, 10L, 5L)).thenReturn(true);
        assertTrue(service.viewFor(student, assignment).orElseThrow().acked());
    }

    @Test
    void viewIsEmptyWhenCourseHasNoRules() {
        assertTrue(service.viewFor(student, assignment).isEmpty());
    }

    @Test
    void ackIsRecordedOncePerVersion() {
        PolicyVersion v = TestSupport.withId(new PolicyVersion(100L, PolicyVersion.COURSE_SCOPE, 1, Tier.DECLARE, null, 1L), 20L);
        when(versions.findFirstByCourseIdAndScopeAssignmentIdOrderByVersionNoDesc(100L, PolicyVersion.COURSE_SCOPE)).thenReturn(Optional.of(v));
        when(acks.existsByPolicyVersionIdAndStudentIdAndAssignmentId(20L, 10L, 5L)).thenReturn(false);
        service.ack(student, assignment);
        verify(acks).save(any());
        when(acks.existsByPolicyVersionIdAndStudentIdAndAssignmentId(20L, 10L, 5L)).thenReturn(true);
        service.ack(student, assignment);
        verify(acks, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void ackWithoutRulesIsRejected() {
        assertThrows(BadRequestException.class, () -> service.ack(student, assignment));
    }
}
