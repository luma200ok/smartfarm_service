package com.smartfarm.service.repository;

import com.smartfarm.service.entity.SavedAnalysis;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedAnalysisRepository extends JpaRepository<SavedAnalysis, Long> {

    List<SavedAnalysis> findByFarmIdOrderByCreatedAtDescIdDesc(Long farmId);

    Optional<SavedAnalysis> findByIdAndFarmId(Long id, Long farmId);

    /** 농장당 상한 판정용(SavedAnalysisService — AlarmRuleRepository#countByFarmId와 동일 패턴). */
    long countByFarmId(Long farmId);
}
