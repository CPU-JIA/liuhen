package cn.liuhen.registration;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiToolRepository extends JpaRepository<AiTool, Long> {
    List<AiTool> findAllByOrderByIdAsc();
}
