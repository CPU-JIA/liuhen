package cn.liuhen.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** 扫描后端源码里所有中文字符串字面量，禁用词不允许出现（AC-TCH-03-1、03-3）。 */
class ForbiddenWordsTest {

    @Test
    void scanFindsWords() {
        assertEquals(List.of("疑似", "作弊", "AI 率"), ForbiddenWords.scan("疑似作弊？AI 率 87%"));
        assertEquals(List.of(), ForbiddenWords.scan("这份作业有 12 个版本"));
    }

    @Test
    void noForbiddenWordInUserFacingStrings() throws IOException {
        Path root = Path.of("src/main/java");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (p.endsWith("ForbiddenWords.java")) {
                    continue;
                }
                String src = Files.readString(p, StandardCharsets.UTF_8);
                for (String literal : literals(src)) {
                    for (String w : ForbiddenWords.scan(literal)) {
                        hits.add(p.getFileName() + ": \"" + literal + "\" 含 " + w);
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(), "源码字符串里出现禁用词：" + hits);
    }

    /** 粗略提取双引号字面量，够用。 */
    private static List<String> literals(String src) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while ((i = src.indexOf('"', i)) >= 0) {
            int j = i + 1;
            StringBuilder sb = new StringBuilder();
            while (j < src.length() && src.charAt(j) != '"') {
                if (src.charAt(j) == '\\' && j + 1 < src.length()) {
                    j++;
                }
                sb.append(src.charAt(j));
                j++;
            }
            out.add(sb.toString());
            i = j + 1;
        }
        return out;
    }
}
