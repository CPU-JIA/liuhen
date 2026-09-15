package cn.liuhen.work;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {
    Optional<Snapshot> findFirstByWorkIdOrderBySeqNoDesc(Long workId);

    Optional<Snapshot> findByWorkIdAndSeqNo(Long workId, int seqNo);

    List<Snapshot> findByWorkIdOrderBySeqNoAsc(Long workId);

    long countByWorkId(Long workId);
}
