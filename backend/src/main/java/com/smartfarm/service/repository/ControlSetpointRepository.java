package com.smartfarm.service.repository;

import com.smartfarm.service.entity.ControlSetpoint;
import com.smartfarm.service.entity.SensorMetric;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ControlSetpointRepository extends JpaRepository<ControlSetpoint, Long> {

    /** 존의 목표값 전체(제어 상태 조회·존 삭제 캐스케이드) — 지표 선언 순서로 결정적 정렬. */
    List<ControlSetpoint> findByZoneIdOrderByMetricAsc(Long zoneId);

    /** 존×지표 1행(적용 시 upsert 판정). */
    Optional<ControlSetpoint> findByZoneIdAndMetric(Long zoneId, SensorMetric metric);

    /** 시뮬레이터 연동(contract §4.12) — 한 농장의 목표값을 1틱에 한 번 배치 조회(N+1 방지). */
    List<ControlSetpoint> findByFarmId(Long farmId);
}
