package cn.liuhen.declaration;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeclarationRepository extends JpaRepository<Declaration, Long> {
    Optional<Declaration> findByWorkId(Long workId);
}
