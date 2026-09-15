package cn.liuhen.declaration;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** AI 使用声明或未使用承诺，一稿一份，提交后只读（数据库触发器保证）。 */
@Entity
@Table(name = "declaration")
public class Declaration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_id", nullable = false)
    private Long workId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeclarationKind kind;

    @Column(name = "policy_version_id", nullable = false)
    private Long policyVersionId;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "is_late", nullable = false)
    private boolean late;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", nullable = false)
    private String contentJson;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    protected Declaration() {
    }

    public Declaration(Long workId, DeclarationKind kind, Long policyVersionId, LocalDateTime generatedAt, boolean late,
                       String contentJson, String contentHash) {
        this.workId = workId;
        this.kind = kind;
        this.policyVersionId = policyVersionId;
        this.generatedAt = generatedAt;
        this.late = late;
        this.contentJson = contentJson;
        this.contentHash = contentHash;
    }

    public Long getId() { return id; }
    public Long getWorkId() { return workId; }
    public DeclarationKind getKind() { return kind; }
    public Long getPolicyVersionId() { return policyVersionId; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public boolean isLate() { return late; }
    public String getContentJson() { return contentJson; }
    public String getContentHash() { return contentHash; }
}
