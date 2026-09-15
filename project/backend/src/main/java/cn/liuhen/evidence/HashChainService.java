package cn.liuhen.evidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * 哈希链。每条记录的 chainHash = SHA-256(prevChainHash ‖ payloadHash ‖ 毫秒时间戳)。
 * <p>
 * 同一 work 下快照与登记各自一条链，链头是 64 个 0。任何一条记录被改，从它开始往后全部对不上；
 * verify 返回第一处断点，给复核员指出"从第几条开始不可信"。手写，不依赖任何链式存储库。
 */
@Service
public class HashChainService {

    public static final String GENESIS = "0".repeat(64);

    public record ChainLink(String prevChainHash, String payloadHash, Instant at, String chainHash) { }

    public record VerifyResult(boolean ok, int firstBrokenIndex) {
        public static VerifyResult passed() {
            return new VerifyResult(true, -1);
        }

        public static VerifyResult brokenAt(int index) {
            return new VerifyResult(false, index);
        }
    }

    public String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    public String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    public ChainLink append(String prevChainHash, String payloadHash, Instant at) {
        requireHex64(prevChainHash, "prevChainHash");
        requireHex64(payloadHash, "payloadHash");
        String chainHash = sha256Hex(prevChainHash + payloadHash + at.toEpochMilli());
        return new ChainLink(prevChainHash, payloadHash, at, chainHash);
    }

    /** 按顺序重算整条链。链头必须是 GENESIS，每条的 prev 必须等于上一条的 chainHash。 */
    public VerifyResult verify(List<ChainLink> links) {
        String expectedPrev = GENESIS;
        for (int i = 0; i < links.size(); i++) {
            ChainLink link = links.get(i);
            if (!expectedPrev.equals(link.prevChainHash())) {
                return VerifyResult.brokenAt(i);
            }
            String recomputed = sha256Hex(link.prevChainHash() + link.payloadHash() + link.at().toEpochMilli());
            if (!recomputed.equals(link.chainHash())) {
                return VerifyResult.brokenAt(i);
            }
            expectedPrev = link.chainHash();
        }
        return VerifyResult.passed();
    }

    private static void requireHex64(String value, String name) {
        if (value == null || value.length() != 64) {
            throw new IllegalArgumentException(name + " 必须是 64 位十六进制");
        }
        for (int i = 0; i < 64; i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                throw new IllegalArgumentException(name + " 含非十六进制字符");
            }
        }
    }
}
