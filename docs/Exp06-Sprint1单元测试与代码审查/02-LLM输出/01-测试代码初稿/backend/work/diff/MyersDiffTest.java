package cn.liuhen.work.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class MyersDiffTest {

    private final MyersDiff diff = new MyersDiff();

    private void roundTrip(String a, String b) {
        List<MyersDiff.Edit> edits = diff.diff(a, b);
        assertEquals(b, diff.apply(a, edits), "apply(diff(a,b)) 必须还原 b");
    }

    @Test
    void identicalTextsProduceSingleEqual() {
        List<MyersDiff.Edit> edits = diff.diff("第一段。", "第一段。");
        assertEquals(1, edits.size());
        assertEquals(MyersDiff.Op.EQUAL, edits.get(0).op());
    }

    @Test
    void emptyToTextIsInsert() {
        List<MyersDiff.Edit> edits = diff.diff("", "abc");
        assertEquals(List.of(new MyersDiff.Edit(MyersDiff.Op.INSERT, "abc")), edits);
        roundTrip("", "abc");
        roundTrip("abc", "");
    }

    @Test
    void classicExampleFromPaper() {
        // Myers 1986 论文例子：ABCABBA → CBABAC，最短脚本 D = 5
        List<MyersDiff.Edit> edits = diff.diff("ABCABBA", "CBABAC");
        int d = edits.stream().filter(e -> e.op() != MyersDiff.Op.EQUAL).mapToInt(MyersDiff.Edit::length).sum();
        assertEquals(5, d);
        roundTrip("ABCABBA", "CBABAC");
    }

    @Test
    void handlesChineseAndSurrogatePairs() {
        String a = "我们在做一个留痕系统😀，用来记录写作过程。";
        String b = "我们在做一个留痕平台😀，用来记录 AI 使用和写作过程。";
        roundTrip(a, b);
        assertEquals(diff.editedChars(a, b), diff.diff(a, b).stream()
                .filter(e -> e.op() != MyersDiff.Op.EQUAL).mapToInt(MyersDiff.Edit::length).sum());
    }

    @Test
    void editedCharsCountsInsertsAndDeletes() {
        assertEquals(0, diff.editedChars("abc", "abc"));
        assertEquals(1, diff.editedChars("abc", "abcd"));
        assertEquals(2, diff.editedChars("abc", "axc"));
    }

    @Test
    void randomRoundTrips() {
        Random rnd = new Random(42);
        String alphabet = "ab中文\n ";
        for (int i = 0; i < 300; i++) {
            String a = randomText(rnd, alphabet, rnd.nextInt(60));
            String b = mutate(rnd, a, alphabet);
            roundTrip(a, b);
        }
    }

    @Test
    void largeTextWithSmallEditStaysFast() {
        String base = "第一段。".repeat(12_000);
        String changed = base.substring(0, 20_000) + "插入了一句新的话。" + base.substring(20_000);
        long start = System.nanoTime();
        roundTrip(base, changed);
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ms < 3000, "5 万字符小改动应在 3 秒内完成，实际 " + ms + " ms");
    }

    @Test
    void exceedingMaxDFallsBackToReplace() {
        MyersDiff small = new MyersDiff(4);
        List<MyersDiff.Edit> edits = small.diff("abcdefghij", "0123456789");
        assertEquals(2, edits.size());
        assertEquals(MyersDiff.Op.DELETE, edits.get(0).op());
        assertEquals(MyersDiff.Op.INSERT, edits.get(1).op());
        assertEquals("0123456789", small.apply("abcdefghij", edits));
    }

    @Test
    void lineDiffMarksChangedLinesOnly() {
        String a = "第一行\n第二行\n第三行";
        String b = "第一行\n第二行改了\n第三行\n第四行";
        List<MyersDiff.LineChange> changes = diff.lineDiff(a, b);
        assertEquals(List.of(
                new MyersDiff.LineChange(MyersDiff.Op.EQUAL, "第一行"),
                new MyersDiff.LineChange(MyersDiff.Op.DELETE, "第二行"),
                new MyersDiff.LineChange(MyersDiff.Op.INSERT, "第二行改了"),
                new MyersDiff.LineChange(MyersDiff.Op.EQUAL, "第三行"),
                new MyersDiff.LineChange(MyersDiff.Op.INSERT, "第四行")), changes);
    }

    @Test
    void applyRejectsScriptThatDoesNotConsumeBase() {
        List<MyersDiff.Edit> edits = List.of(new MyersDiff.Edit(MyersDiff.Op.EQUAL, "ab"));
        assertThrows(IllegalArgumentException.class, () -> diff.apply("abc", edits));
    }

    @Test
    void fullReplacementOfLargeTextDegradesButStaysCorrectAndFast() {
        // 走查第 22 条：整篇换掉时 D 远超上限 3000，退化为整段替换，仍要能还原且不能慢
        Random rnd = new Random(7);
        String[] alphabet = {"甲", "乙", "丙", "丁", "a", "b", "c", "d", "
"};
        String a = randomTokens(rnd, alphabet, 50_000);
        String b = randomTokens(rnd, alphabet, 50_000);
        long start = System.nanoTime();
        List<MyersDiff.Edit> edits = diff.diff(a, b);
        assertEquals(b, diff.apply(a, edits));
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ms < 5000, "5 万字符整篇替换应在 5 秒内返回，实际 " + ms + " ms");
        long nonEqual = edits.stream().filter(e -> e.op() != MyersDiff.Op.EQUAL).count();
        assertTrue(nonEqual <= 2, "退化后中间只剩一删一插，实际 " + nonEqual + " 段");
    }

    @Test
    void randomRoundTripsWithLongerTextsAndMoreEdits() {
        // 走查第 22 条：原来 300 轮只有 60 字符以内、最多 5 处改动，加大到 2000 字符、30 处改动
        Random rnd = new Random(2026);
        String[] alphabet = {"a", "b", "中", "文", "😀", "
", " "};
        for (int i = 0; i < 100; i++) {
            String a = randomTokens(rnd, alphabet, rnd.nextInt(2000));
            String b = mutateTokens(rnd, a, alphabet, 30);
            roundTrip(a, b);
            roundTrip(b, a);
        }
    }

    @Test
    void emojiOnlyTextsRoundTripPerCodePoint() {
        roundTrip("😀😀😀", "😀🙂😀😀");
        assertEquals(1, diff.editedChars("😀😀😀", "😀😀😀😀"), "插入一个 emoji 算 1 个字符，不是 2 个");
        assertEquals(2, diff.editedChars("😀😀😀", "😀🙂😀"), "替换一个 emoji 是一删一插");
    }

    @Test
    void editedCharsIsSymmetricAndZeroForSameText() {
        assertEquals(diff.editedChars("abc", "axcd"), diff.editedChars("axcd", "abc"));
        assertEquals(0, diff.editedChars("", ""));
        assertEquals(3, diff.editedChars("", "abc"));
    }

    @Test
    void lineDiffIgnoresTrailingNewlineDifference() {
        List<MyersDiff.LineChange> changes = diff.lineDiff("第一行
第二行
", "第一行
第二行");
        assertTrue(changes.stream().allMatch(c -> c.op() == MyersDiff.Op.EQUAL), "末尾多一个换行不算改动：" + changes);
        assertEquals(2, changes.size());
    }

    @Test
    void lineDiffOfIdenticalTextIsAllEqual() {
        List<MyersDiff.LineChange> changes = diff.lineDiff("a
b
c", "a
b
c");
        assertEquals(3, changes.size());
        assertTrue(changes.stream().allMatch(c -> c.op() == MyersDiff.Op.EQUAL));
    }

    private static String randomTokens(Random rnd, String[] alphabet, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(alphabet[rnd.nextInt(alphabet.length)]);
        }
        return sb.toString();
    }

    /** 按码点做 ops 次随机增删，保证不会把 emoji 切成半个。 */
    private static String mutateTokens(Random rnd, String s, String[] alphabet, int maxOps) {
        List<String> tokens = new ArrayList<>(s.codePoints().mapToObj(Character::toString).toList());
        int ops = rnd.nextInt(maxOps + 1);
        for (int i = 0; i < ops; i++) {
            int pos = tokens.isEmpty() ? 0 : rnd.nextInt(tokens.size());
            if (rnd.nextBoolean() || tokens.isEmpty()) {
                tokens.add(pos, alphabet[rnd.nextInt(alphabet.length)]);
            } else {
                tokens.remove(pos);
            }
        }
        return String.join("", tokens);
    }

    private static String randomText(Random rnd, String alphabet, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static String mutate(Random rnd, String s, String alphabet) {
        StringBuilder sb = new StringBuilder(s);
        int ops = rnd.nextInt(6);
        for (int i = 0; i < ops; i++) {
            int pos = sb.length() == 0 ? 0 : rnd.nextInt(sb.length());
            if (rnd.nextBoolean() || sb.length() == 0) {
                sb.insert(pos, randomText(rnd, alphabet, 1 + rnd.nextInt(5)));
            } else {
                sb.delete(pos, Math.min(sb.length(), pos + 1 + rnd.nextInt(4)));
            }
        }
        return sb.toString();
    }
}
