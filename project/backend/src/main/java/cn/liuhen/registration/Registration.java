package cn.liuhen.registration;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** AI 使用登记，追加式：修改 = 插新行并把旧行置 VOIDED，数据库触发器只放行 status 变更。 */
@Entity
@Table(name = "registration")
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_id", nullable = false)
    private Long workId;

    @Column(name = "tool_id")
    private Long toolId;

    @Column(name = "tool_name_custom", length = 50)
    private String toolNameCustom;

    @Column(name = "tool_version", length = 50)
    private String toolVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Stage stage;

    @Column(nullable = false, length = 200)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Adoption adoption;

    @Column(name = "prompt_text", columnDefinition = "TEXT")
    private String promptText;

    @Column(name = "output_text", columnDefinition = "TEXT")
    private String outputText;

    @Enumerated(EnumType.STRING)
    private Verification verification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegStatus status = RegStatus.ACTIVE;

    @Column(name = "supersedes_id")
    private Long supersedesId;

    @Column(name = "chain_hash", nullable = false, length = 64)
    private String chainHash;

    @Column(name = "prev_chain_hash", nullable = false, length = 64)
    private String prevChainHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Registration() {
    }

    public Registration(Long workId, Long toolId, String toolNameCustom, String toolVersion, Stage stage, String purpose,
                        Adoption adoption, String promptText, String outputText, Verification verification,
                        Long supersedesId, String prevChainHash, String chainHash, LocalDateTime createdAt) {
        this.workId = workId;
        this.toolId = toolId;
        this.toolNameCustom = toolNameCustom;
        this.toolVersion = toolVersion;
        this.stage = stage;
        this.purpose = purpose;
        this.adoption = adoption;
        this.promptText = promptText;
        this.outputText = outputText;
        this.verification = verification;
        this.supersedesId = supersedesId;
        this.prevChainHash = prevChainHash;
        this.chainHash = chainHash;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getWorkId() { return workId; }
    public Long getToolId() { return toolId; }
    public String getToolNameCustom() { return toolNameCustom; }
    public String getToolVersion() { return toolVersion; }
    public Stage getStage() { return stage; }
    public String getPurpose() { return purpose; }
    public Adoption getAdoption() { return adoption; }
    public String getPromptText() { return promptText; }
    public String getOutputText() { return outputText; }
    public Verification getVerification() { return verification; }
    public RegStatus getStatus() { return status; }
    public Long getSupersedesId() { return supersedesId; }
    public String getChainHash() { return chainHash; }
    public String getPrevChainHash() { return prevChainHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** 唯一允许的更新。其余字段的修改会被数据库触发器拒绝。 */
    public void markVoided() {
        this.status = RegStatus.VOIDED;
    }
}
