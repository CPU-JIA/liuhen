package cn.liuhen.work.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DeltaCodecTest {

    private final MyersDiff diff = new MyersDiff();
    private final DeltaCodec codec = new DeltaCodec();

    @Test
    void encodeDecodeApplyRoundTrip() {
        String a = "留痕系统的第一版正文。\n第二段。";
        String b = "留痕系统的第二版正文，改了一句。\n第二段。\n新加第三段。";
        byte[] bytes = codec.encode(diff.diff(a, b));
        assertEquals(b, diff.apply(a, codec.decode(bytes)));
    }

    @Test
    void deltaIsMuchSmallerThanFullTextForSmallEdits() {
        String a = "第一段。".repeat(5000);
        String b = a + "结尾多了一句。";
        byte[] delta = codec.encode(diff.diff(a, b));
        int full = b.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        assertTrue(delta.length * 10 < full, "差分 " + delta.length + " 字节应远小于全文 " + full + " 字节");
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[] {1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[] {'L', 9, 0, 0, 0, 0}));
    }

    @Test
    void emptyScriptEncodes() {
        List<MyersDiff.Edit> none = List.of();
        assertEquals(0, codec.decode(codec.encode(none)).size());
    }
}
