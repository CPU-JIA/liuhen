package cn.liuhen.policy;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 规则允许场景，弱实体。 */
@Entity
@Table(name = "policy_scene")
public class PolicyScene {

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "policy_version_id")
        private Long policyVersionId;
        @Column(name = "scene_name", length = 30)
        private String sceneName;

        protected Key() {
        }

        public Key(Long policyVersionId, String sceneName) {
            this.policyVersionId = policyVersionId;
            this.sceneName = sceneName;
        }

        public Long getPolicyVersionId() { return policyVersionId; }
        public String getSceneName() { return sceneName; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(policyVersionId, k.policyVersionId) && Objects.equals(sceneName, k.sceneName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(policyVersionId, sceneName);
        }
    }

    @EmbeddedId
    private Key key;

    @Column(nullable = false)
    private boolean allowed = true;

    protected PolicyScene() {
    }

    public PolicyScene(Long policyVersionId, String sceneName, boolean allowed) {
        this.key = new Key(policyVersionId, sceneName);
        this.allowed = allowed;
    }

    public Key getKey() { return key; }
    public String getSceneName() { return key.getSceneName(); }
    public boolean isAllowed() { return allowed; }
}
