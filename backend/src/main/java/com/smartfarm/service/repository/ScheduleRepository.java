package com.smartfarm.service.repository;

import com.smartfarm.service.entity.Schedule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    /** farm 스코프 필수 — id 단독 조회 금지(cross-tenant IDOR 차단, 다른 도메인과 동일 원칙). */
    Optional<Schedule> findByIdAndFarmId(Long id, Long farmId);

    /** 목록 조회 — AlarmRuleRepository와 동일하게 상한이 작아(50건) 페이지네이션을 두지 않는다. */
    List<Schedule> findByFarmIdOrderByIdAsc(Long farmId);

    /** 농장당 상한 판정(잠금 안쪽 count 관용구 — AlarmRuleService.createRule 선례). */
    long countByFarmId(Long farmId);
}
