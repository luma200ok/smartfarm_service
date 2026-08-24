package com.smartfarm.service.repository;

import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventStatus;
import com.smartfarm.service.entity.AlarmSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 알람 이벤트 목록 동적 필터(status·severity, 전부 선택적 조합) — 이 프로젝트는 QueryDSL을 쓰지
 * 않으므로(DeviceRepositoryCustom 선례) 표준 JPA Criteria API로 동적 조건을 구성한다(Custom+Impl
 * 네이밍 관례는 유지).
 */
public interface AlarmEventRepositoryCustom {

    Page<AlarmEvent> search(Long farmId, AlarmEventStatus status, AlarmSeverity severity, Pageable pageable);
}
