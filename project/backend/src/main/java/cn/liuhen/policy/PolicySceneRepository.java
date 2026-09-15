package cn.liuhen.policy;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicySceneRepository extends JpaRepository<PolicyScene, PolicyScene.Key> {
    List<PolicyScene> findByKeyPolicyVersionId(Long policyVersionId);
}
