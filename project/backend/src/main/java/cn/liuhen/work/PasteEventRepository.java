package cn.liuhen.work;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasteEventRepository extends JpaRepository<PasteEvent, Long> {
    List<PasteEvent> findByWorkIdOrderByOccurredAtAsc(Long workId);

    List<PasteEvent> findByWorkIdAndSource(Long workId, PasteSource source);

    boolean existsByWorkIdAndSource(Long workId, PasteSource source);
}
