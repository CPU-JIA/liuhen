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

@Entity
@Table(name = "paste_event")
public class PasteEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_id", nullable = false)
    private Long workId;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PasteSource source = PasteSource.PENDING;

    @Column(name = "offset_start", nullable = false)
    private int offsetStart;

    @Column(name = "offset_end", nullable = false)
    private int offsetEnd;

    @Column(name = "registration_id")
    private Long registrationId;

    protected PasteEvent() {
    }

    public PasteEvent(Long workId, int charCount, int offsetStart, int offsetEnd, PasteSource source, LocalDateTime occurredAt) {
        this.workId = workId;
        this.charCount = charCount;
        this.offsetStart = offsetStart;
        this.offsetEnd = offsetEnd;
        this.source = source == null ? PasteSource.PENDING : source;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Long getWorkId() { return workId; }
    public Long getSnapshotId() { return snapshotId; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public int getCharCount() { return charCount; }
    public PasteSource getSource() { return source; }
    public int getOffsetStart() { return offsetStart; }
    public int getOffsetEnd() { return offsetEnd; }
    public Long getRegistrationId() { return registrationId; }

    public void resolve(PasteSource source, Long registrationId) {
        this.source = source;
        this.registrationId = registrationId;
    }

    public void attachSnapshot(Long snapshotId) {
        if (this.snapshotId == null) {
            this.snapshotId = snapshotId;
        }
    }
}
