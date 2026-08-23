package com.smartfarm.service.repository;

import com.smartfarm.service.entity.Device;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long>, DeviceRepositoryCustom {

    /** farm 스코프 필수 — deviceId 단독 조회 금지(cross-tenant IDOR 차단) */
    Optional<Device> findByIdAndFarmId(Long id, Long farmId);

    /**
     * 가상 장비 시뮬레이터 대상(contract §4.11) — {@code kind=SENSOR}이고 {@code status != OFFLINE}
     * 인 전 농장 장비를 id 오름차순으로. 서비스 계층이 farmId별로 그룹핑해 농장당 200개 상한을
     * 적용한다(id 순서가 상한 판정 시 결정적이어야 테스트 재현이 가능).
     */
    List<Device> findByKindAndStatusNotOrderByIdAsc(DeviceKind kind, DeviceStatus status);

    /** 요약(DeviceSummaryResponse) 계산용 — 농장 규모상 전체 로드 후 서비스 계층에서 집계. */
    List<Device> findByFarmId(Long farmId);

    /** levelCount 축소 시 잘려나가는 층들에 장비가 매달려 있는지 확인(R004 판정용). */
    boolean existsByRackLevelIdIn(List<Long> rackLevelIds);

    /** 랙 직속(층이 아닌 랙 단위) 장비 존재 확인 — 랙 삭제 시 R004 판정용(리뷰 P1 #89). */
    boolean existsByRackId(Long rackId);

    /** 존 직속(랙이 아닌 존 단위) 장비 존재 확인 — 존 삭제 시 R004 판정용(리뷰 P1 #89). */
    boolean existsByZoneId(Long zoneId);

    /** 존 하위 랙 전체에 매달린 장비 존재 확인 — 존 삭제 시 R004 판정용(리뷰 P1 #89). */
    boolean existsByRackIdIn(List<Long> rackIds);

    /** 농장 스코프 시리얼 중복 사전 확인(생성 시) — null serial은 애초에 호출하지 않는다. */
    boolean existsByFarmIdAndSerial(Long farmId, String serial);

    /** 농장 스코프 시리얼 중복 사전 확인(수정 시) — 자기 자신은 제외. */
    boolean existsByFarmIdAndSerialAndIdNot(Long farmId, String serial, Long id);
}
