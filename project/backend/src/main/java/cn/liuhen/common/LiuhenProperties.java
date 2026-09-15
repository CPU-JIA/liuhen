package cn.liuhen.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 里 liuhen.* 的强类型映射。数字全部来自实验 2 验收标准。 */
@ConfigurationProperties(prefix = "liuhen")
public record LiuhenProperties(Jwt jwt, Work work, Account account) {

    public record Jwt(String secret, int ttlHours) { }

    public record Work(int maxChars, int snapshotIntervalMinutes, int snapshotEditChars, int keyframeEvery, int pasteThresholdChars) { }

    public record Account(int lockAfterFailures, int lockMinutes) { }
}
