package cn.liuhen.common;

import java.util.List;

/**
 * 教师端、复核端、教务端文案里不允许出现的词。来自实验 1 红线 不1、不5 与验收 AC-TCH-03。
 * 测试用它扫描 messages 与接口返回，前端也有一份同样的清单。
 */
public final class ForbiddenWords {

    public static final List<String> WORDS = List.of("疑似", "作弊", "风险", "AI 率", "AI率", "时长");

    private ForbiddenWords() {
    }

    /** 返回文本里出现过的禁用词，按清单顺序，不去重也不重复。 */
    public static List<String> scan(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return WORDS.stream().filter(text::contains).toList();
    }
}
