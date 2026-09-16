package cn.liuhen.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import cn.liuhen.TestSupport;
import cn.liuhen.account.Role;
import cn.liuhen.common.LiuhenProperties;

/** 令牌签发与解析。载荷只有 id 与角色，其余信息每次查库（设计说明第 6 节）。 */
class JwtServiceTest {

    private final JwtService jwt = new JwtService(TestSupport.props());

    @Test
    void issueThenParseGivesBackIdAndRole() {
        String token = jwt.issue(42L, Role.TEACHER);
        JwtService.Principal p = jwt.parse(token).orElseThrow();
        assertEquals(42L, p.userId());
        assertEquals(Role.TEACHER, p.role());
    }

    @Test
    void tamperedPayloadIsRejected() {
        String token = jwt.issue(42L, Role.STUDENT);
        String[] parts = token.split("\\.");
        String flipped = parts[1].charAt(5) == 'A' ? "B" : "A";
        String tampered = parts[0] + "." + parts[1].substring(0, 5) + flipped + parts[1].substring(6) + "." + parts[2];
        assertTrue(jwt.parse(tampered).isEmpty());
    }

    @Test
    void tokenFromAnotherSecretIsRejected() {
        LiuhenProperties other = new LiuhenProperties(
                new LiuhenProperties.Jwt("another-secret-that-is-also-long-enough-123456", 12),
                TestSupport.props().work(), TestSupport.props().account());
        String foreign = new JwtService(other).issue(42L, Role.ADMIN);
        assertEquals(Optional.empty(), jwt.parse(foreign));
    }

    @Test
    void garbageIsRejectedNotThrown() {
        assertTrue(jwt.parse("not.a.token").isEmpty());
        assertTrue(jwt.parse("").isEmpty());
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        LiuhenProperties weak = new LiuhenProperties(new LiuhenProperties.Jwt("short", 12),
                TestSupport.props().work(), TestSupport.props().account());
        assertThrows(IllegalStateException.class, () -> new JwtService(weak));
    }
}
