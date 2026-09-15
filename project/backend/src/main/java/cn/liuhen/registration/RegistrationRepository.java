package cn.liuhen.registration;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    List<Registration> findByWorkIdOrderByCreatedAtAsc(Long workId);

    List<Registration> findByWorkIdAndStatusOrderByCreatedAtAsc(Long workId, RegStatus status);

    Optional<Registration> findFirstByWorkIdOrderByCreatedAtDescIdDesc(Long workId);
}
