package cn.liuhen.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class HashChainServiceTest {

    private final HashChainService chain = new HashChainService();

    @Test
    void sha256KnownAnswer() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", chain.sha256Hex("abc"));
    }

    @Test
    void appendAndVerifyIntactChain() {
        List<HashChainService.ChainLink> links = build(5);
        assertTrue(chain.verify(links).ok());
    }

    @Test
    void tamperingPayloadBreaksFromThatIndex() {
        List<HashChainService.ChainLink> links = build(5);
        HashChainService.ChainLink bad = links.get(2);
        links.set(2, new HashChainService.ChainLink(bad.prevChainHash(), chain.sha256Hex("篡改"), bad.at(), bad.chainHash()));
        HashChainService.VerifyResult result = chain.verify(links);
        assertFalse(result.ok());
        assertEquals(2, result.firstBrokenIndex());
    }

    @Test
    void removingALinkBreaksNext() {
        List<HashChainService.ChainLink> links = build(5);
        links.remove(1);
        assertEquals(1, chain.verify(links).firstBrokenIndex());
    }

    @Test
    void chainMustStartAtGenesis() {
        List<HashChainService.ChainLink> links = build(3);
        links.remove(0);
        assertEquals(0, chain.verify(links).firstBrokenIndex());
    }

    @Test
    void rejectsMalformedHashes() {
        assertThrows(IllegalArgumentException.class, () -> chain.append("abc", chain.sha256Hex("x"), Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> chain.append(HashChainService.GENESIS, "zz".repeat(32), Instant.now()));
    }

    @Test
    void timestampIsPartOfTheChainHash() {
        // 记录时间本身是证据，不进哈希就可以事后改；两条只差 1 毫秒的链节哈希必须不同
        String payload = chain.sha256Hex("同一份内容");
        Instant t = Instant.parse("2026-09-18T02:00:00Z");
        assertNotEquals(chain.append(HashChainService.GENESIS, payload, t).chainHash(),
                chain.append(HashChainService.GENESIS, payload, t.plusMillis(1)).chainHash());
    }

    @Test
    void swappingTwoLinksBreaksAtTheFirstMovedOne() {
        List<HashChainService.ChainLink> links = build(4);
        HashChainService.ChainLink a = links.get(1);
        links.set(1, links.get(2));
        links.set(2, a);
        assertEquals(1, chain.verify(links).firstBrokenIndex());
    }

    @Test
    void emptyChainVerifies() {
        assertTrue(chain.verify(List.of()).ok());
        assertEquals(-1, chain.verify(List.of()).firstBrokenIndex());
    }

    @Test
    void upperCaseHexIsAcceptedAsInput() {
        String payload = chain.sha256Hex("x").toUpperCase();
        assertEquals(64, chain.append(HashChainService.GENESIS, payload, Instant.now()).chainHash().length());
    }

    @Test
    void sha256OfEmptyAndOfChinese() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", chain.sha256Hex(""));
        assertEquals(chain.sha256Hex("留痕".getBytes(java.nio.charset.StandardCharsets.UTF_8)), chain.sha256Hex("留痕"));
    }

    private List<HashChainService.ChainLink> build(int n) {
        List<HashChainService.ChainLink> links = new ArrayList<>();
        String prev = HashChainService.GENESIS;
        Instant t = Instant.parse("2026-09-15T12:00:00Z");
        for (int i = 0; i < n; i++) {
            HashChainService.ChainLink link = chain.append(prev, chain.sha256Hex("payload" + i), t.plusSeconds(i));
            links.add(link);
            prev = link.chainHash();
        }
        return links;
    }
}
