package com.smartfarm.service.repository;

import com.smartfarm.service.entity.ControlChange;
import com.smartfarm.service.entity.ControlChangeStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ControlChangeRepository extends JpaRepository<ControlChange, Long> {

    /**
     * 존의 대기 큐(= PENDING 목록) — <b>id 오름차순</b>이 적용 순서다(같은 대상에 대한 중복 항목이
     * 있으면 나중 것이 이긴다). 낙관적 검증(CT005)의 비교 대상도 이 목록이다.
     */
    List<ControlChange> findByZoneIdAndStatusOrderByIdAsc(Long zoneId, ControlChangeStatus status);

    /** 존당 큐 상한(50건) 판정 — 항목 로드 없이 카운트만. */
    long countByZoneIdAndStatus(Long zoneId, ControlChangeStatus status);

    /** farm 스코프 필수 — changeId 단독 조회 금지(cross-tenant IDOR 차단). */
    Optional<ControlChange> findByIdAndFarmId(Long id, Long farmId);

    /** 비상 정지 — 농장 전체 PENDING 일괄 폐기 대상. */
    List<ControlChange> findByFarmIdAndStatusOrderByIdAsc(Long farmId, ControlChangeStatus status);

    /** 캐스케이드 — 삭제되는 장비를 참조하는 PENDING 항목(contract §4.12 캐스케이드). */
    List<ControlChange> findByStatusAndDeviceIdInOrderByIdAsc(ControlChangeStatus status,
                                                              Collection<Long> deviceIds);
}
