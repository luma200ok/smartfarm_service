package com.smartfarm.service.repository;

import com.smartfarm.service.entity.Device;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long>, DeviceRepositoryCustom {

    /** farm 스코프 필수 — deviceId 단독 조회 금지(cross-tenant IDOR 차단) */
    Optional<Device> findByIdAndFarmId(Long id, Long farmId);

    /** 요약(DeviceSummaryResponse) 계산용 — 농장 규모상 전체 로드 후 서비스 계층에서 집계. */
    List<Device> findByFarmId(Long farmId);

    /** levelCount 축소 시 잘려나가는 층들에 장비가 매달려 있는지 확인(R004 판정용). */
    boolean existsByRackLevelIdIn(List<Long> rackLevelIds);

    /** 농장 스코프 시리얼 중복 사전 확인(생성 시) — null serial은 애초에 호출하지 않는다. */
    boolean existsByFarmIdAndSerial(Long farmId, String serial);

    /** 농장 스코프 시리얼 중복 사전 확인(수정 시) — 자기 자신은 제외. */
    boolean existsByFarmIdAndSerialAndIdNot(Long farmId, String serial, Long id);
}
