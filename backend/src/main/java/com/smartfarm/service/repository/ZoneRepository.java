package com.smartfarm.service.repository;

import com.smartfarm.service.entity.Zone;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    /** 존 트리 조회(contract §4.10 ZoneTreeResponse) — 표시 순서대로. */
    List<Zone> findByFarmIdOrderByDisplayOrderAscIdAsc(Long farmId);

    /** farm 스코프 필수 — zoneId 단독 조회 금지(cross-tenant IDOR 차단, FarmLogRepository와 동일 원칙) */
    Optional<Zone> findByIdAndFarmId(Long id, Long farmId);
}
