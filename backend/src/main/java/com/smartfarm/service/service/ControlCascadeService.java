package com.smartfarm.service.service;

import com.smartfarm.service.entity.ControlChange;
import com.smartfarm.service.entity.ControlChangeStatus;
import com.smartfarm.service.entity.ControlSetpoint;
import com.smartfarm.service.entity.Device;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.ControlChangeRepository;
import com.smartfarm.service.repository.ControlModeRepository;
import com.smartfarm.service.repository.ControlSetpointRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 구조 삭제 시 제어 상태 캐스케이드(contract §4.12 "계층·캐스케이드") — 존·장비가 soft delete되면
 * 그것을 참조하는 <b>PENDING 큐 항목을 DISCARDED로 폐기</b>하고, 존의 목표값은 존과 함께
 * soft delete한다. 폐기하지 않으면 적용 시점에 대상이 없는 고아 참조가 된다.
 *
 * <p>{@code ControlApplyLog}는 <b>감사 이력이라 보존</b>한다(구조가 사라져도 "언제 무엇을 적용했는가"는
 * 사실로 남는다). 90일 보존 purge만 적용된다.
 *
 * <p><b>랙 삭제 경로가 없는 이유</b>: 큐 항목이 참조하는 대상은 존(SETPOINT)과 장비(DEVICE)뿐이고,
 * §4.10이 <b>하위에 활성 장비가 있는 랙 삭제를 R004로 거부</b>하므로 랙 삭제 시점에는 그 랙을 참조하는
 * 장비가 존재하지 않는다 — 폐기할 큐 항목 자체가 생길 수 없다. 존 삭제도 같은 R004의 보호를 받지만,
 * 존 스코프인 SETPOINT 항목은 장비와 무관하게 남을 수 있어 별도 폐기가 필요하다.
 *
 * <p><b>트랜잭션</b>: 자체 트랜잭션을 열지 않는다 — 호출측({@code ZoneService#deleteZone},
 * {@code DeviceService#deleteDevice})의 쓰기 트랜잭션에 참여해 삭제와 폐기가 원자적이어야 한다.
 * 대신 존 단위 직렬화 잠금은 여기서 잡는다(삭제와 apply의 경합 차단).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlCascadeService {

    private final ControlModeRepository controlModeRepository;
    private final ControlSetpointRepository controlSetpointRepository;
    private final ControlChangeRepository controlChangeRepository;

    /**
     * 존 삭제 캐스케이드 — 그 존의 PENDING 큐 전부 폐기 + 목표값 soft delete.
     *
     * <p>먼저 존의 잠금 지점을 잡는다(contract §4.12 동시성 3) — 잠그지 않으면 "삭제가 큐를 비우는
     * 사이 apply가 그 큐를 읽어 적용"하는 경합이 남는다. 잠금 지점 행({@code control_modes})은 삭제하지
     * 않는다(ControlMode 클래스 주석 참고 — 존이 사라지면 도달 불가해지고, 지우면 잠금 지점이 사라진다).
     */
    public void discardForZone(Long farmId, Long zoneId) {
        controlModeRepository.insertDefaultIfAbsent(farmId, zoneId);
        controlModeRepository.findByZoneIdForUpdate(zoneId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.C002));

        List<ControlChange> pending =
                controlChangeRepository.findByZoneIdAndStatusOrderByIdAsc(zoneId, ControlChangeStatus.PENDING);
        pending.forEach(ControlChange::markDiscarded);

        List<ControlSetpoint> setpoints = controlSetpointRepository.findByZoneIdOrderByMetricAsc(zoneId);
        controlSetpointRepository.deleteAll(setpoints);

        if (!pending.isEmpty() || !setpoints.isEmpty()) {
            log.info("존 삭제 캐스케이드 — zoneId={}, 대기 항목 {}건 폐기, 목표값 {}건 삭제",
                    zoneId, pending.size(), setpoints.size());
        }
    }

    /**
     * 장비 삭제 캐스케이드 — 그 장비를 참조하는 PENDING 큐 항목 폐기. 장비는 존 하나에만 속하므로
     * (§4.10 자동 채움으로 삼중조가 완전) 그 존의 잠금 지점만 잡으면 충분하다.
     */
    public void discardForDevice(Device device) {
        if (device.getZoneId() != null) {
            controlModeRepository.insertDefaultIfAbsent(device.getFarmId(), device.getZoneId());
            controlModeRepository.findByZoneIdForUpdate(device.getZoneId(), device.getFarmId())
                    .orElseThrow(() -> new CustomException(ErrorCode.C002));
        }

        List<ControlChange> pending = controlChangeRepository.findByStatusAndDeviceIdInOrderByIdAsc(
                ControlChangeStatus.PENDING, List.of(device.getId()));
        pending.forEach(ControlChange::markDiscarded);

        if (!pending.isEmpty()) {
            log.info("장비 삭제 캐스케이드 — deviceId={}, 대기 항목 {}건 폐기", device.getId(), pending.size());
        }
    }
}
