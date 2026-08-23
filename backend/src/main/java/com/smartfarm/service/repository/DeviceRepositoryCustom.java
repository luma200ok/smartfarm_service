package com.smartfarm.service.repository;

import com.smartfarm.service.entity.Device;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import java.util.List;

/**
 * 장비 목록 동적 필터(contract §4.10 — kind·status·q 부분일치·zoneId, 전부 선택적 조합). 이
 * 프로젝트는 QueryDSL을 쓰지 않으므로(전 도메인 확인 — 순수 Spring Data 메서드 네이밍만 사용)
 * 표준 JPA Criteria API로 동적 조건을 구성한다(handoff의 Custom+Impl 네이밍 관례는 유지).
 */
public interface DeviceRepositoryCustom {

    List<Device> search(Long farmId, DeviceKind kind, DeviceStatus status, String q, Long zoneId);
}
