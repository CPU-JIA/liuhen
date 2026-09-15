package cn.liuhen.work;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 版本快照，只增不改不删。关键帧存全文，其余存相对 base 的差分。 */
@Entity
@Table(name = "snapshot")
public class Snapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_id", nullable = false)
    private Long workId;

    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private SnapshotTrigger trigger;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(name = "is_keyframe", nullable = false)
    private boolean keyframe;

    @Column(name = "base_snapshot_id")
    private Long baseSnapshotId;

    @Column(columnDefinition = "MEDIUMBLOB")
    private byte[] delta;

    @Column(name = "full_text", columnDefinition = "MEDIUMTEXT")
    private String fullText;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "chain_hash", nullable = false, length = 64)
    private String chainHash;

    @Column(name = "prev_chain_hash", nullable = false, length = 64)
    private String prevChainHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Snapshot() {
    }

    public static Snapshot keyframe(Long workId, int seqNo, SnapshotTrigger trigger, String fullText, int charCount,
                                    String contentHash, String prevChainHash, String chainHash, LocalDateTime at) {
        Snapshot s = new Snapshot();
        s.workId = workId;
        s.seqNo = seqNo;
        s.trigger = trigger;
        s.keyframe = true;
        s.fullText = fullText;
        s.charCount = charCount;
        s.contentHash = contentHash;
        s.prevChainHash = prevChainHash;
        s.chainHash = chainHash;
        s.createdAt = at;
        return s;
    }

    public static Snapshot delta(Long workId, int seqNo, SnapshotTrigger trigger, Long baseSnapshotId, byte[] delta, int charCount,
                                 String contentHash, String prevChainHash, String chainHash, LocalDateTime at) {
        Snapshot s = new Snapshot();
        s.workId = workId;
        s.seqNo = seqNo;
        s.trigger = trigger;
        s.keyframe = false;
        s.baseSnapshotId = baseSnapshotId;
        s.delta = delta;
        s.charCount = charCount;
        s.contentHash = contentHash;
        s.prevChainHash = prevChainHash;
        s.chainHash = chainHash;
        s.createdAt = at;
        return s;
    }

    public Long getId() { return id; }
    public Long getWorkId() { return workId; }
    public int getSeqNo() { return seqNo; }
    public SnapshotTrigger getTrigger() { return trigger; }
    public int getCharCount() { return charCount; }
    public boolean isKeyframe() { return keyframe; }
    public Long getBaseSnapshotId() { return baseSnapshotId; }
    public byte[] getDelta() { return delta; }
    public String getFullText() { return fullText; }
    public String getContentHash() { return contentHash; }
    public String getChainHash() { return chainHash; }
    public String getPrevChainHash() { return prevChainHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
