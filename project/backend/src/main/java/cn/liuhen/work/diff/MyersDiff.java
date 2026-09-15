package cn.liuhen.work.diff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Myers 差分算法（E. Myers, 1986）的手写实现。
 * <p>
 * 核心 O((N+M)D) 贪心前向搜索加回溯。为了在自动保存这种"小改动、大文本"的场景下省内存，
 * 先剥掉公共前后缀再搜索；D 超过上限时退化为整段替换，保证任何输入都能在可控的时间和内存内返回。
 * 字符级按 Unicode 码点比较，避免把代理对拆成两半；行级把每一行映射成整数再跑同一个核心。
 * 课程要求核心算法手写，本类不依赖任何第三方库。
 */
public final class MyersDiff {

    /** 编辑操作。EQUAL 与 DELETE 的 text 来自旧文本，INSERT 的 text 来自新文本。 */
    public enum Op { EQUAL, INSERT, DELETE }

    public record Edit(Op op, String text) {
        /** 码点数，DeltaCodec 用它替代 EQUAL/DELETE 的原文以缩小体积。 */
        public int length() {
            return text.codePointCount(0, text.length());
        }
    }

    public record LineChange(Op op, String line) { }

    /** 搜索深度上限。超过后退化为整段替换，见 diffTokens。 */
    public static final int DEFAULT_MAX_D = 3000;

    private final int maxD;

    public MyersDiff() {
        this(DEFAULT_MAX_D);
    }

    public MyersDiff(int maxD) {
        if (maxD < 1) {
            throw new IllegalArgumentException("maxD 必须大于 0");
        }
        this.maxD = maxD;
    }

    /** 字符级差分：把 a 变成 b 的最短编辑脚本（在 maxD 内）。 */
    public List<Edit> diff(String a, String b) {
        int[] ta = a.codePoints().toArray();
        int[] tb = b.codePoints().toArray();
        List<Segment> segments = diffTokens(ta, tb);
        List<Edit> edits = new ArrayList<>(segments.size());
        for (Segment s : segments) {
            String text = switch (s.op) {
                case EQUAL, DELETE -> new String(ta, s.start, s.length);
                case INSERT -> new String(tb, s.start, s.length);
            };
            edits.add(new Edit(s.op, text));
        }
        return edits;
    }

    /** 用编辑脚本把 base 还原成新文本。EQUAL/DELETE 只消费 base 的码点数，不比对内容，调用方靠哈希校验。 */
    public String apply(String base, List<Edit> edits) {
        int[] tokens = base.codePoints().toArray();
        StringBuilder out = new StringBuilder(base.length());
        int pos = 0;
        for (Edit e : edits) {
            switch (e.op()) {
                case EQUAL -> {
                    int len = e.length();
                    requireRange(pos, len, tokens.length);
                    out.append(new String(tokens, pos, len));
                    pos += len;
                }
                case DELETE -> {
                    int len = e.length();
                    requireRange(pos, len, tokens.length);
                    pos += len;
                }
                case INSERT -> out.append(e.text());
            }
        }
        if (pos != tokens.length) {
            throw new IllegalArgumentException("编辑脚本没有消费完 base：剩余 " + (tokens.length - pos) + " 个码点");
        }
        return out.toString();
    }

    /** 行级差分，给教师端高亮用。按 \n 切分，末尾无换行的最后一行也算一行。 */
    public List<LineChange> lineDiff(String a, String b) {
        List<String> la = splitLines(a);
        List<String> lb = splitLines(b);
        Map<String, Integer> intern = new HashMap<>();
        int[] ta = internLines(la, intern);
        int[] tb = internLines(lb, intern);
        List<Segment> segments = diffTokens(ta, tb);
        List<LineChange> changes = new ArrayList<>();
        for (Segment s : segments) {
            List<String> src = s.op == Op.INSERT ? lb : la;
            for (int i = 0; i < s.length; i++) {
                changes.add(new LineChange(s.op, src.get(s.start + i)));
            }
        }
        return changes;
    }

    /** 统计把 a 变成 b 需要增删多少码点，给快照触发判断用。 */
    public int editedChars(String a, String b) {
        int n = 0;
        for (Edit e : diff(a, b)) {
            if (e.op() != Op.EQUAL) {
                n += e.length();
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 核心

    /** 一段连续同类操作。start 对 EQUAL/DELETE 指向 a，对 INSERT 指向 b。 */
    record Segment(Op op, int start, int length) { }

    List<Segment> diffTokens(int[] a, int[] b) {
        int prefix = commonPrefix(a, b);
        int suffix = commonSuffix(a, b, prefix);
        int n = a.length - prefix - suffix;
        int m = b.length - prefix - suffix;

        List<Segment> out = new ArrayList<>();
        if (prefix > 0) {
            out.add(new Segment(Op.EQUAL, 0, prefix));
        }
        if (n == 0 && m > 0) {
            out.add(new Segment(Op.INSERT, prefix, m));
        } else if (m == 0 && n > 0) {
            out.add(new Segment(Op.DELETE, prefix, n));
        } else if (n > 0) {
            List<Segment> middle = myers(a, prefix, n, b, prefix, m);
            out.addAll(middle);
        }
        if (suffix > 0) {
            out.add(new Segment(Op.EQUAL, a.length - suffix, suffix));
        }
        return merge(out);
    }

    private List<Segment> myers(int[] a, int aOff, int n, int[] b, int bOff, int m) {
        int max = Math.min(n + m, maxD);
        int size = 2 * max + 2;
        int[] v = new int[size];
        List<int[]> trace = new ArrayList<>();
        v[index(1, max)] = 0;

        for (int d = 0; d <= max; d++) {
            trace.add(v.clone());
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && v[index(k - 1, max)] < v[index(k + 1, max)])) {
                    x = v[index(k + 1, max)];
                } else {
                    x = v[index(k - 1, max)] + 1;
                }
                int y = x - k;
                while (x < n && y < m && a[aOff + x] == b[bOff + y]) {
                    x++;
                    y++;
                }
                v[index(k, max)] = x;
                if (x >= n && y >= m) {
                    return backtrack(trace, a, aOff, b, bOff, n, m, max);
                }
            }
        }
        // 超过上限：整段替换。答辩时要能说清这是有意的退化，不是 bug。
        List<Segment> fallback = new ArrayList<>(2);
        fallback.add(new Segment(Op.DELETE, aOff, n));
        fallback.add(new Segment(Op.INSERT, bOff, m));
        return fallback;
    }

    private List<Segment> backtrack(List<int[]> trace, int[] a, int aOff, int[] b, int bOff, int n, int m, int max) {
        List<Segment> reversed = new ArrayList<>();
        int x = n;
        int y = m;
        for (int d = trace.size() - 1; d >= 0; d--) {
            int[] v = trace.get(d);
            int k = x - y;
            int prevK;
            if (k == -d || (k != d && v[index(k - 1, max)] < v[index(k + 1, max)])) {
                prevK = k + 1;
            } else {
                prevK = k - 1;
            }
            int prevX = v[index(prevK, max)];
            int prevY = prevX - prevK;
            while (x > prevX && y > prevY) {
                x--;
                y--;
                push(reversed, Op.EQUAL, aOff + x, 1);
            }
            if (d > 0) {
                if (x == prevX) {
                    y--;
                    push(reversed, Op.INSERT, bOff + y, 1);
                } else {
                    x--;
                    push(reversed, Op.DELETE, aOff + x, 1);
                }
            }
        }
        List<Segment> ordered = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            ordered.add(reversed.get(i));
        }
        return ordered;
    }

    /** 回溯是倒序推入的，同类相邻段在这里先按倒序合并，最后统一 merge 再正一次。 */
    private static void push(List<Segment> reversed, Op op, int start, int length) {
        if (!reversed.isEmpty()) {
            Segment last = reversed.get(reversed.size() - 1);
            if (last.op == op && last.start == start + length) {
                reversed.set(reversed.size() - 1, new Segment(op, start, last.length + length));
                return;
            }
        }
        reversed.add(new Segment(op, start, length));
    }

    private static List<Segment> merge(List<Segment> in) {
        List<Segment> out = new ArrayList<>(in.size());
        for (Segment s : in) {
            if (s.length == 0) {
                continue;
            }
            if (!out.isEmpty()) {
                Segment last = out.get(out.size() - 1);
                if (last.op == s.op && last.start + last.length == s.start) {
                    out.set(out.size() - 1, new Segment(s.op, last.start, last.length + s.length));
                    continue;
                }
            }
            out.add(s);
        }
        return out;
    }

    private static int index(int k, int max) {
        return k + max;
    }

    private static int commonPrefix(int[] a, int[] b) {
        int limit = Math.min(a.length, b.length);
        int i = 0;
        while (i < limit && a[i] == b[i]) {
            i++;
        }
        return i;
    }

    private static int commonSuffix(int[] a, int[] b, int prefix) {
        int limit = Math.min(a.length, b.length) - prefix;
        int i = 0;
        while (i < limit && a[a.length - 1 - i] == b[b.length - 1 - i]) {
            i++;
        }
        return i;
    }

    private static void requireRange(int pos, int len, int total) {
        if (len < 0 || pos + len > total) {
            throw new IllegalArgumentException("编辑脚本越界：pos=" + pos + " len=" + len + " total=" + total);
        }
    }

    static List<String> splitLines(String s) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                lines.add(s.substring(start, i));
                start = i + 1;
            }
        }
        // 末尾换行不产生额外空行；空串算一行空行
        if (start < s.length() || s.isEmpty()) {
            lines.add(s.substring(start));
        }
        return lines;
    }

    private static int[] internLines(List<String> lines, Map<String, Integer> intern) {
        int[] tokens = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            tokens[i] = intern.computeIfAbsent(lines.get(i), k -> intern.size());
        }
        return tokens;
    }
}
