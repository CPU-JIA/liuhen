package cn.liuhen.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_tool")
public class AiTool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "is_preset", nullable = false)
    private boolean preset = true;

    protected AiTool() {
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isPreset() { return preset; }
}
