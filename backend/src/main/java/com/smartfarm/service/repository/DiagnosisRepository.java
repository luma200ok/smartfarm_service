package com.smartfarm.service.repository;

import com.smartfarm.service.entity.Diagnosis;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

    /** farm 스코프 필수 — diagnosisId 단독 조회 금지(cross-tenant IDOR 차단) */
    Optional<Diagnosis> findByIdAndFarmId(Long id, Long farmId);

    Page<Diagnosis> findByFarmIdOrderByCreatedAtDescIdDesc(Long farmId, Pageable pageable);
}
