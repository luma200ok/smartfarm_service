package com.smartfarm.service.repository;

import com.smartfarm.service.entity.Rack;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RackRepository extends JpaRepository<Rack, Long> {

    /** 존 트리 조회용 — 여러 존의 랙을 한 번에(N+1 방지, IN 절 배치 조회). */
    List<Rack> findByZoneIdInOrderByDisplayOrderAscIdAsc(List<Long> zoneIds);

    List<Rack> findByZoneIdOrderByDisplayOrderAscIdAsc(Long zoneId);

    /** farm 스코프 필수 — rackId 단독 조회 금지(cross-tenant IDOR 차단) */
    Optional<Rack> findByIdAndFarmId(Long id, Long farmId);

    /** 존 내 코드 중복 사전 확인(생성 시) — DB unique(zone_id, code) 위반 시 서비스가 race 대비 catch도 병행. */
    boolean existsByZoneIdAndCode(Long zoneId, String code);

    /** 존 내 코드 중복 사전 확인(수정 시) — 자기 자신은 제외. */
    boolean existsByZoneIdAndCodeAndIdNot(Long zoneId, String code, Long id);
}
