package cn.liuhen.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cn.liuhen.TestSupport;

/** 引擎里会查库的两个方法：当前生效版本的优先级、是否需要重新确认（AC-RULE-01-5、01-6、AC-RULE-02-2）。 */
@ExtendWith(MockitoExtension.class)
class PolicyRuleEngineLookupTest {

    @Mock
    private PolicyVersionRepository versions;
    @Mock
    private PolicySceneRepository scenes;
    @Mock
    private PolicyAckRepository acks;

    @Test
    void assignmentLevelVersionBeatsCourseLevel() {
        PolicyVersion courseLevel = new PolicyVersion(100L, PolicyVersion.COURSE_SCOPE, 3, Tier.DECLARE, null, 1L);
        PolicyVersion assignmentLevel = new PolicyVersion(100L, 5L, 1, Tier.FORBID, null, 1L);
        when(versions.findFirstByCourseIdAndScopeAssignmentIdOrderByVersionNoDesc(100L, 5L)).thenReturn(Optional.of(assignmentLevel));
        PolicyRuleEngine engine = new PolicyRuleEngine(versions, scenes, acks);
        assertEquals(Tier.FORBID, engine.current(100L, 5L).orElseThrow().getTier());
        when(versions.findFirstByCourseIdAndScopeAssignmentIdOrderByVersionNoDesc(100L, 6L)).thenReturn(Optional.empty());
        when(versions.findFirstByCourseIdAndScopeAssignmentIdOrderByVersionNoDesc(100L, PolicyVersion.COURSE_SCOPE)).thenReturn(Optional.of(courseLevel));
        assertEquals(Tier.DECLARE, engine.current(100L, 6L).orElseThrow().getTier(), "新作业沿用课程规则");
    }

    @Test
    void courseWithoutRulesHasNoCurrentVersion() {
        when(versions.findFirstByCourseIdAndScopeAssignmentIdOrderByVersionNoDesc(100L, 5L)).thenReturn(Optional.empty());
        when(versions.findFirstByCourseIdAndScopeAssignmentIdOrderByVersionNoDesc(100L, PolicyVersion.COURSE_SCOPE)).thenReturn(Optional.empty());
        assertTrue(new PolicyRuleEngine(versions, scenes, acks).current(100L, 5L).isEmpty());
    }

    @Test
    void newVersionRequiresANewAck() {
        PolicyVersion v2 = TestSupport.withId(new PolicyVersion(100L, PolicyVersion.COURSE_SCOPE, 2, Tier.DECLARE, null, 1L), 21L);
        when(acks.existsByPolicyVersionIdAndStudentIdAndAssignmentId(21L, 10L, 5L)).thenReturn(false);
        PolicyRuleEngine engine = new PolicyRuleEngine(versions, scenes, acks);
        assertTrue(engine.needsReack(10L, 5L, v2));
        when(acks.existsByPolicyVersionIdAndStudentIdAndAssignmentId(21L, 10L, 5L)).thenReturn(true);
        assertFalse(engine.needsReack(10L, 5L, v2));
    }
}
