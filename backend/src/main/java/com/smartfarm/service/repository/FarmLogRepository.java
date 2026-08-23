package com.smartfarm.service.repository;

import com.smartfarm.service.entity.FarmLog;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmLogRepository extends JpaRepository<FarmLog, Long> {

    /** farm 스코프 필수 — logId 단독 조회 금지(cross-tenant IDOR 차단, DiagnosisRepository와 동일 원칙) */
    Optional<FarmLog> findByIdAndFarmId(Long id, Long farmId);

    /** 목록 조회(contract §4.8) — logDate 내림차순, 동일 날짜는 id 내림차순으로 안정 정렬. */
    Page<FarmLog> findByFarmIdOrderByLogDateDescIdDesc(Long farmId, Pageable pageable);
}
