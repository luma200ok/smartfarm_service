package com.smartfarm.service.repository;

import com.smartfarm.service.entity.Rack;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RackRepository extends JpaRepository<Rack, Long> {

    /** 존 트리 조회용 — 여러 존의 랙을 한 번에(N+1 방지, IN 절 배치 조회). */
    List<Rack> findByZoneIdInOrderByDisplayOrderAscIdAsc(List<Long> zoneIds);

    List<Rack> findByZoneIdOrderByDisplayOrderAscIdAsc(Long zoneId);

    /** {@code /readings/latest}에서 zoneId 미지정(farm 전체) 시 랙 도면 전체 조회용(contract §4.11). */
    List<Rack> findByFarmIdOrderByDisplayOrderAscIdAsc(Long farmId);

    /** farm 스코프 필수 — rackId 단독 조회 금지(cross-tenant IDOR 차단) */
    Optional<Rack> findByIdAndFarmId(Long id, Long farmId);

    /** 존 내 코드 중복 사전 확인(생성 시) — DB unique(zone_id, code) 위반 시 서비스가 race 대비 catch도 병행. */
    boolean existsByZoneIdAndCode(Long zoneId, String code);

    /** 존 내 코드 중복 사전 확인(수정 시) — 자기 자신은 제외. */
    boolean existsByZoneIdAndCodeAndIdNot(Long zoneId, String code, Long id);

    /**
     * 홈 대시보드 카드(이슈 #139) — 여러 농장의 랙 수·총 층 수를 한 번에 구한다(N+1 방지, farm 수와
     * 무관하게 쿼리 1개). {@code levelCount}는 rack_levels를 조인하지 않고 Rack.levelCount(비정규화,
     * RackService가 층 CRUD 시 항상 동기화)를 SUM해 구한다({@link FarmRackAggregateProjection} 참고).
     */
    @Query("SELECT r.farmId AS farmId, COUNT(r) AS rackCount, COALESCE(SUM(r.levelCount), 0L) AS levelCount "
            + "FROM Rack r WHERE r.farmId IN :farmIds GROUP BY r.farmId")
    List<FarmRackAggregateProjection> aggregateByFarmIds(@Param("farmIds") List<Long> farmIds);
}
