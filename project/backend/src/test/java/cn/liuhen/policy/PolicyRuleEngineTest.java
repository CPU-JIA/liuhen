package cn.liuhen.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import cn.liuhen.registration.Adoption;
import cn.liuhen.registration.Registration;
import cn.liuhen.registration.Stage;

/** check 是纯函数，不碰仓库，所以引擎用 null 仓库构造即可。 */
class PolicyRuleEngineTest {

    private final PolicyRuleEngine engine = new PolicyRuleEngine(null, null, null);

    private static Registration reg(Stage stage) {
        return new Registration(1L, 1L, null, null, stage, "测试", Adoption.REFERENCE, null, null, null, null,
                "0".repeat(64), "1".repeat(64), LocalDateTime.now());
    }

    private static PolicyVersion version(Tier tier) {
        return new PolicyVersion(1L, PolicyVersion.COURSE_SCOPE, 1, tier, null, 1L);
    }

    @Test
    void forbidRejectsEverything() {
        PolicyVersion v = version(Tier.FORBID);
        assertFalse(engine.check(reg(Stage.POLISH), v, List.of(new PolicyScene(1L, "改语法", true))).allowed());
    }

    @Test
    void declareAllowsOnlyExplicitScenes() {
        PolicyVersion v = version(Tier.DECLARE);
        List<PolicyScene> scenes = List.of(new PolicyScene(1L, "改语法", true), new PolicyScene(1L, "生成正文", false));
        assertTrue(engine.check(reg(Stage.POLISH), v, scenes).allowed());
        assertFalse(engine.check(reg(Stage.BODY), v, scenes).allowed());
        PolicyRuleEngine.SceneCheck unmentioned = engine.check(reg(Stage.CODE), v, scenes);
        assertFalse(unmentioned.allowed());
        assertFalse(unmentioned.explicit(), "老师没提的场景要能和明确禁止区分开");
    }

    @Test
    void encourageAllowsUnlessExplicitlyDisabled() {
        PolicyVersion v = version(Tier.ENCOURAGE);
        List<PolicyScene> scenes = List.of(new PolicyScene(1L, "生成数据", false));
        assertTrue(engine.check(reg(Stage.BODY), v, scenes).allowed());
        assertFalse(engine.check(reg(Stage.DATA), v, scenes).allowed());
    }
}
