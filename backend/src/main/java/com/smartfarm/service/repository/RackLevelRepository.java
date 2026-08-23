package com.smartfarm.service.repository;

import com.smartfarm.service.entity.RackLevel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RackLevelRepository extends JpaRepository<RackLevel, Long> {

    /** 존 트리 조회용 — 여러 랙의 층을 한 번에(N+1 방지, IN 절 배치 조회). */
    List<RackLevel> findByRackIdInOrderByLevelNoAsc(List<Long> rackIds);

    List<RackLevel> findByRackIdOrderByLevelNoAsc(Long rackId);

    /** levelCount 축소 시 잘려나가는 층(levelNo가 새 카운트를 초과하는 행) 조회. */
    List<RackLevel> findByRackIdAndLevelNoGreaterThan(Long rackId, Integer levelNo);

    /** farm 스코프 필수 — rackLevelId 단독 조회 금지(cross-tenant IDOR 차단, Device 위치 FK 검증용) */
    Optional<RackLevel> findByIdAndFarmId(Long id, Long farmId);
}
