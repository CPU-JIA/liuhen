package cn.liuhen;

import org.springframework.test.util.ReflectionTestUtils;

import cn.liuhen.common.LiuhenProperties;

/** 测试公用：与 application.yml 相同的验收数字，以及给未入库实体补 id 的小工具。 */
public final class TestSupport {

    private TestSupport() {
    }

    public static LiuhenProperties props() {
        return new LiuhenProperties(
                new LiuhenProperties.Jwt("liuhen-test-secret-0123456789-abcdefghijklmnop", 12),
                new LiuhenProperties.Work(50_000, 10, 500, 10, 100),
                new LiuhenProperties.Account(5, 15));
    }

    /** 实体的 id 由数据库生成，单元测试不走数据库，只能这样补上。 */
    public static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
