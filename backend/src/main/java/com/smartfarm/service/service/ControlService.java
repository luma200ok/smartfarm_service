package com.smartfarm.service.service;

import com.smartfarm.service.dto.ControlApplyLogResponse;
import com.smartfarm.service.dto.ControlApplyRequest;
import com.smartfarm.service.dto.ControlApplyResponse;
import com.smartfarm.service.dto.ControlChangeRequest;
import com.smartfarm.service.dto.ControlChangeResponse;
import com.smartfarm.service.dto.ControlDeviceResponse;
import com.smartfarm.service.dto.ControlModeRequest;
import com.smartfarm.service.dto.ControlSetpointResponse;
import com.smartfarm.service.dto.ControlStateResponse;
import com.smartfarm.service.dto.EmergencyStopResponse;
import com.smartfarm.service.entity.ControlApplyLog;
import com.smartfarm.service.entity.ControlChange;
import com.smartfarm.service.entity.ControlChangeKind;
import com.smartfarm.service.entity.ControlChangeStatus;
import com.smartfarm.service.entity.ControlMode;
import com.smartfarm.service.entity.ControlSetpoint;
import com.smartfarm.service.entity.Device;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.entity.OperationMode;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.entity.Zone;
import com.smartfarm.service.exception.ControlQueueConflictException;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.ControlApplyLogRepository;
import com.smartfarm.service.repository.ControlChangeRepository;
import com.smartfarm.service.repository.ControlModeRepository;
import com.smartfarm.service.repository.ControlSetpointRepository;
import com.smartfarm.service.repository.DeviceRepository;
import com.smartfarm.service.repository.ZoneRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제어 도메인(contract §4.12, 이슈 #100) — 존 운전 모드, 존×지표 목표값, 적용 대기 큐(적재·취소·
 * 일괄 적용), 농장 전체 비상 정지.
 *
 * <p><b>동시성 설계(이 클래스의 핵심 — Critical 사이클인 이유)</b>
 * <ol>
 *   <li><b>존 단위 직렬화</b>: 모든 쓰기 경로는 {@link #lockZone}로 그 존의 {@code control_modes}
 *       행을 {@code PESSIMISTIC_WRITE} 잠근 <b>뒤에</b> 읽고 쓴다. #91에서 P2로 남은 TOCTOU(읽고-쓰기
 *       사이에 락이 없어 검사 결과가 무효가 되는 문제)를 여기서 반복하지 않기 위해, 큐 상한 판정·
 *       낙관적 검증·모드 판정 같은 "검사"가 전부 락 안쪽에 있다.</li>
 *   <li><b>낙관적 검증(CT005)</b>: {@code apply}는 {@code expectedChangeIds}를 필수로 받아 잠금 하에서
 *       읽은 현재 PENDING 집합과 대조하고, 다르면 최신 큐를 실어 거부한다.</li>
 *   <li><b>단일 트랜잭션</b>: PENDING→APPLIED 전이 · 목표값/장비 상태 갱신 · 적용 이력 기록이 한
 *       트랜잭션이다(부분 적용 상태를 남기지 않는다). 서비스 메서드 하나가 트랜잭션 경계다.</li>
 *   <li><b>비상 정지 우선</b>: 전 존 장비 OFF + MANUAL + 농장의 모든 PENDING 폐기. 정지 이전에 쌓인
 *       큐가 정지 이후에 적용될 경로가 없다(폐기는 정지와 같은 트랜잭션).</li>
 *   <li><b>상태 전이는 엔티티 메서드</b>({@code ControlChange#markApplied/markDiscarded}) —
 *       APPLIED/DISCARDED는 종료 상태이며 재전이는 엔티티가 막는다.</li>
 * </ol>
 *
 * <p><b>데드락</b>: 존별 경로는 락을 <b>정확히 하나</b>만 잡고, 여러 존을 잡는 유일한 경로인 비상
 * 정지는 zoneId 오름차순으로 잡는다 — 순환 대기가 성립하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ControlService {

    /** 존당 PENDING 큐 상한(contract §4.12) — 무제한이면 apply 트랜잭션이 무한정 커진다. */
    static final int MAX_PENDING_PER_ZONE = 50;

    /** 제어 가능한 지표(contract §4.12) — 응답에는 미설정 포함 항상 이 4종 전부를 싣는다. */
    private static final List<SensorMetric> CONTROLLABLE_METRICS = Arrays.stream(SensorMetric.values())
            .filter(SensorMetric::isControllable)
            .toList();

    /**
     * 목표값 허용 범위 — 물리적으로 불가능한 값이 들어오면 그대로 시뮬레이터 기저값이 되어 그래프가
     * 망가진다(§4.12 시뮬레이터 연동). 임계치 알림(§4.6)의 "정상 범위"가 아니라 <b>입력 sanity 범위</b>라
     * 넉넉하게 잡는다 — 좁히면 정상적인 실험값까지 막는다. 위반은 C001.
     */
    private static final Map<SensorMetric, TargetRange> TARGET_RANGES = new EnumMap<>(Map.of(
            SensorMetric.TEMPERATURE, new TargetRange(-20.0, 60.0),
            SensorMetric.HUMIDITY, new TargetRange(0.0, 100.0),
            SensorMetric.CO2, new TargetRange(0.0, 5000.0),
            SensorMetric.PPFD, new TargetRange(0.0, 2000.0)));

    /** 장비 토글로 지정 가능한 상태 — 켜기(NORMAL)/끄기(OFF)만. 나머지는 관측 결과이지 조작 대상이 아니다. */
    private static final Set<DeviceStatus> TOGGLEABLE_STATUSES = Set.of(DeviceStatus.NORMAL, DeviceStatus.OFF);

    private final ZoneRepository zoneRepository;
    private final DeviceRepository deviceRepository;
    private final ControlModeRepository controlModeRepository;
    private final ControlSetpointRepository controlSetpointRepository;
    private final ControlChangeRepository controlChangeRepository;
    private final ControlApplyLogRepository controlApplyLogRepository;
    private final FarmAccessGuard farmAccessGuard;
    private final DemoAccountGuard demoAccountGuard;

    private record TargetRange(double min, double max) {

        boolean contains(double value) {
            return value >= min && value <= max;
        }
    }

    // ------------------------------------------------------------------ 조회

    /**
     * 존 제어 상태 1회 조회(contract §4.12) — 모드 + 목표값 4종 + 장비 상태 + 대기 큐 + 최근 이력.
     * 읽기 경로라 잠금 지점 행을 만들지 않는다 — 모드 행이 아직 없으면 {@code AUTO}로 간주한다
     * (contract §4.12 "미설정 존은 AUTO 기본으로 간주 — 행 생성 전에도 조회가 성립해야 한다").
     */
    public ControlStateResponse findControlState(Long farmId, Long userId, Long zoneId) {
        farmAccessGuard.requireMember(farmId, userId);
        Zone zone = findZoneOrThrow(farmId, zoneId);
        ControlMode mode = controlModeRepository.findByZoneIdAndFarmId(zoneId, farmId).orElse(null);
        return buildState(zone, mode);
    }

    // ------------------------------------------------------------------ 모드

    /**
     * 운전 모드 변경(contract §4.12). ⚠️ 새 모드에서 허용되지 않는 <b>PENDING 항목은 함께 폐기</b>한다 —
     * 남겨두면 그 항목은 영원히 적용될 수 없으면서(apply가 CT003으로 전체 거부) 큐 상한만 차지해
     * "무엇을 지워야 적용되는지" 알 수 없는 교착이 된다. 폐기 사실은 응답의 큐(비워진 상태)로 즉시 보인다.
     */
    @Transactional
    public ControlStateResponse changeMode(Long farmId, Long userId, Long zoneId, ControlModeRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireMember(farmId, userId);
        Zone zone = findZoneOrThrow(farmId, zoneId);
        ControlMode mode = lockZone(farmId, zoneId);

        mode.changeMode(request.mode(), userId);

        List<ControlChange> pending =
                controlChangeRepository.findByZoneIdAndStatusOrderByIdAsc(zoneId, ControlChangeStatus.PENDING);
        List<ControlChange> disallowed = pending.stream()
                .filter(change -> !isAllowedIn(request.mode(), change.getKind()))
                .toList();
        disallowed.forEach(ControlChange::markDiscarded);
        if (!disallowed.isEmpty()) {
            log.info("운전 모드 변경({}) — 새 모드에서 허용되지 않는 대기 항목 {}건 폐기: zoneId={}",
                    request.mode(), disallowed.size(), zoneId);
        }
        return buildState(zone, mode);
    }

    // ------------------------------------------------------------------ 대기 큐

    /**
     * 대기 큐 적재(contract §4.12) — <b>장비에 즉시 반영하지 않는다</b>. 검증 순서는
     * ① 요청 형태(C001) → ② 모드별 허용 조작(CT003) → ③ 리소스 소속(E001, 미소속은 404) →
     * ④ 통신 두절(CT002) → ⑤ 큐 상한(CT004)이다.
     *
     * <p>⑤ 상한 판정이 잠금 안쪽이라, 동시 적재 2건이 49→51로 새는 TOCTOU가 없다.
     */
    @Transactional
    public ControlChangeResponse enqueueChange(Long farmId, Long userId, Long zoneId,
                                                ControlChangeRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireMember(farmId, userId);
        findZoneOrThrow(farmId, zoneId);
        ControlMode mode = lockZone(farmId, zoneId);

        ControlChange change = switch (request.kind()) {
            case SETPOINT -> buildSetpointChange(farmId, zoneId, userId, mode.getMode(), request);
            case DEVICE -> buildDeviceChange(farmId, zoneId, userId, mode.getMode(), request);
        };

        long pendingCount =
                controlChangeRepository.countByZoneIdAndStatus(zoneId, ControlChangeStatus.PENDING);
        if (pendingCount >= MAX_PENDING_PER_ZONE) {
            throw new CustomException(ErrorCode.CT004);
        }
        return ControlChangeResponse.from(controlChangeRepository.save(change));
    }

    /** 개별 취소(contract §4.12) — 작성자 본인 또는 OWNER. 이미 종료된 항목은 큐에 없으므로 CT001. */
    @Transactional
    public void cancelChange(Long farmId, Long userId, Long zoneId, Long changeId) {
        demoAccountGuard.rejectDemoAccount(userId);
        FarmAccessGuard.FarmAccess access = farmAccessGuard.requireMember(farmId, userId);
        findZoneOrThrow(farmId, zoneId);
        lockZone(farmId, zoneId);

        ControlChange change = controlChangeRepository.findByIdAndFarmId(changeId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.CT001));
        // 다른 존의 항목을 이 존 경로에 끼워 넣는 경로도 차단한다(§4.10 소속 재확인 규약 — 미소속 404).
        if (!change.getZoneId().equals(zoneId) || change.getStatus() != ControlChangeStatus.PENDING) {
            throw new CustomException(ErrorCode.CT001);
        }
        boolean owner = access.membership().getRole() == FarmRole.OWNER;
        if (!owner && !change.getCreatedBy().equals(userId)) {
            throw new CustomException(ErrorCode.A005);
        }
        change.markDiscarded();
    }

    /** 전체 되돌리기(contract §4.12) — 존의 PENDING 전부 폐기. 멤버면 가능(자기 농장 큐 정리). */
    @Transactional
    public void cancelAllChanges(Long farmId, Long userId, Long zoneId) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireMember(farmId, userId);
        findZoneOrThrow(farmId, zoneId);
        lockZone(farmId, zoneId);

        controlChangeRepository.findByZoneIdAndStatusOrderByIdAsc(zoneId, ControlChangeStatus.PENDING)
                .forEach(ControlChange::markDiscarded);
    }

    // ------------------------------------------------------------------ 적용

    /**
     * 대기 큐 일괄 적용(contract §4.12 동시성 1·2) — 잠금 하에서 낙관적 검증 후 <b>단일 트랜잭션</b>으로
     * PENDING→APPLIED 전이 · 목표값/장비 상태 갱신 · 적용 이력 기록을 원자적으로 수행한다.
     *
     * <p>적용 순서는 id 오름차순(큐 적재 순서)이라, 같은 대상에 대한 중복 항목은 나중 것이 최종값이 된다.
     */
    @Transactional
    public ControlApplyResponse apply(Long farmId, Long userId, Long zoneId, ControlApplyRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireMember(farmId, userId);
        Zone zone = findZoneOrThrow(farmId, zoneId);
        ControlMode mode = lockZone(farmId, zoneId);

        List<ControlChange> pending =
                controlChangeRepository.findByZoneIdAndStatusOrderByIdAsc(zoneId, ControlChangeStatus.PENDING);
        verifyExpectedQueue(pending, request.expectedChangeIds());

        LocalDateTime appliedAt = LocalDateTime.now();
        int appliedSetpoints = 0;
        int appliedDevices = 0;
        int skipped = 0;
        for (ControlChange change : pending) {
            boolean applied = switch (change.getKind()) {
                case SETPOINT -> applySetpoint(farmId, zoneId, userId, mode.getMode(), change);
                case DEVICE -> applyDeviceChange(farmId, zoneId, mode.getMode(), change);
            };
            if (applied) {
                change.markApplied(userId, appliedAt);
                if (change.getKind() == ControlChangeKind.SETPOINT) {
                    appliedSetpoints++;
                } else {
                    appliedDevices++;
                }
            } else {
                // 대상이 사라진 고아 항목 — 캐스케이드가 정상 동작하면 도달하지 않는 방어 경로.
                change.markDiscarded();
                skipped++;
            }
        }

        int appliedCount = appliedSetpoints + appliedDevices;
        if (appliedCount > 0) {
            controlApplyLogRepository.save(ControlApplyLog.builder()
                    .farmId(farmId)
                    .zoneId(zoneId)
                    .summary(summaryOf(appliedSetpoints, appliedDevices))
                    .itemCount(appliedCount)
                    .appliedBy(userId)
                    .appliedAt(appliedAt)
                    .build());
        }
        return new ControlApplyResponse(zoneId, appliedCount, skipped, appliedAt, true, buildState(zone, mode));
    }

    /**
     * 비상 정지(contract §4.12 동시성 4, 농장 전체) — 전 존 장비 OFF + 모드 MANUAL + <b>모든 PENDING
     * 폐기</b>를 하나의 트랜잭션으로 수행한다. 정지 후 잔존 큐가 나중에 적용될 경로를 남기지 않는다.
     *
     * <p>잠금은 <b>zoneId 오름차순 고정</b>이고, "모든 존의 행 생성 → 모든 존 잠금 → 변경" 순서다
     * (행 생성 쿼리가 flush를 유발하므로, 변경을 시작하기 전에 생성을 모두 끝낸다).
     */
    @Transactional
    public EmergencyStopResponse emergencyStop(Long farmId, Long userId) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireOwner(farmId, userId);

        List<Long> zoneIds = zoneRepository.findByFarmIdOrderByDisplayOrderAscIdAsc(farmId).stream()
                .map(Zone::getId)
                .sorted()
                .toList();
        zoneIds.forEach(zoneId -> controlModeRepository.insertDefaultIfAbsent(farmId, zoneId));
        List<ControlMode> modes = new ArrayList<>();
        for (Long zoneId : zoneIds) {
            modes.add(lockExistingZone(farmId, zoneId));
        }
        modes.forEach(mode -> mode.changeMode(OperationMode.MANUAL, userId));

        // 이미 꺼졌거나(OFF) 통신 두절(OFFLINE)인 장비는 대상에서 뺀다 — OFFLINE을 OFF로 덮으면
        // "장애로 응답 없음"이라는 사실이 조작 결과에 묻힌다.
        List<Device> devices = deviceRepository.findByFarmIdAndStatusNotInOrderByIdAsc(
                farmId, List.of(DeviceStatus.OFFLINE, DeviceStatus.OFF));
        devices.forEach(device -> device.changeStatus(DeviceStatus.OFF));

        List<ControlChange> pending =
                controlChangeRepository.findByFarmIdAndStatusOrderByIdAsc(farmId, ControlChangeStatus.PENDING);
        pending.forEach(ControlChange::markDiscarded);

        LocalDateTime stoppedAt = LocalDateTime.now();
        writeEmergencyStopLogs(farmId, userId, zoneIds, devices, pending, stoppedAt);

        log.warn("비상 정지 — farmId={}, 존 {}개, 장비 {}대 OFF, 대기 항목 {}건 폐기, userId={}",
                farmId, zoneIds.size(), devices.size(), pending.size(), userId);
        return new EmergencyStopResponse(farmId, zoneIds.size(), devices.size(), pending.size(), stoppedAt, true);
    }

    // ------------------------------------------------------------------ 내부

    /**
     * 존 단위 직렬화 잠금(contract §4.12 동시성 3) — 잠금 지점인 {@code control_modes} 행을 지연
     * 생성한 뒤 {@code SELECT ... FOR UPDATE}로 잠근다. 생성이 {@code ON CONFLICT DO NOTHING}이라
     * 동시 생성 경합에서도 예외 없이 한쪽만 삽입되고 양쪽 모두 같은 행을 잠근다.
     */
    private ControlMode lockZone(Long farmId, Long zoneId) {
        controlModeRepository.insertDefaultIfAbsent(farmId, zoneId);
        return lockExistingZone(farmId, zoneId);
    }

    private ControlMode lockExistingZone(Long farmId, Long zoneId) {
        return controlModeRepository.findByZoneIdForUpdate(zoneId, farmId)
                // 직전에 존재를 보장했으므로 여기 도달하면 서버 결함이다(존은 이미 R001로 검증됨).
                .orElseThrow(() -> new CustomException(ErrorCode.C002));
    }

    /** 존이 해당 farm 소속인지 재확인(cross-tenant IDOR 차단) — 미소속·미존재는 동일하게 R001. */
    private Zone findZoneOrThrow(Long farmId, Long zoneId) {
        return zoneRepository.findByIdAndFarmId(zoneId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.R001));
    }

    private boolean isAllowedIn(OperationMode mode, ControlChangeKind kind) {
        return kind == ControlChangeKind.SETPOINT ? mode.allowsSetpointChange() : mode.allowsDeviceChange();
    }

    private ControlChange buildSetpointChange(Long farmId, Long zoneId, Long userId, OperationMode mode,
                                               ControlChangeRequest request) {
        if (!mode.allowsSetpointChange()) {
            throw new CustomException(ErrorCode.CT003);
        }
        SensorMetric metric = request.metric();
        if (metric == null || !metric.isControllable()) {
            throw new CustomException(ErrorCode.C001, "제어 가능한 지표(TEMPERATURE·HUMIDITY·CO2·PPFD)를 지정해야 합니다.");
        }
        Double target = request.targetValue();
        if (target == null || !Double.isFinite(target) || !TARGET_RANGES.get(metric).contains(target)) {
            throw new CustomException(ErrorCode.C001, "목표값이 허용 범위를 벗어났습니다: " + metric);
        }
        String fromValue = controlSetpointRepository.findByZoneIdAndMetric(zoneId, metric)
                .map(setpoint -> formatValue(setpoint.getTargetValue()))
                .orElse(null);
        return ControlChange.builder()
                .farmId(farmId)
                .zoneId(zoneId)
                .kind(ControlChangeKind.SETPOINT)
                .metric(metric)
                .fromValue(fromValue)
                .toValue(formatValue(target))
                .createdBy(userId)
                .build();
    }

    private ControlChange buildDeviceChange(Long farmId, Long zoneId, Long userId, OperationMode mode,
                                             ControlChangeRequest request) {
        if (!mode.allowsDeviceChange()) {
            throw new CustomException(ErrorCode.CT003);
        }
        if (request.deviceId() == null) {
            throw new CustomException(ErrorCode.C001, "장비 조작에는 deviceId가 필요합니다.");
        }
        DeviceStatus targetStatus = request.targetStatus();
        if (targetStatus == null || !TOGGLEABLE_STATUSES.contains(targetStatus)) {
            throw new CustomException(ErrorCode.C001, "장비 조작 대상 상태는 NORMAL(켜기) 또는 OFF(끄기)여야 합니다.");
        }
        Device device = findZoneDeviceOrThrow(farmId, zoneId, request.deviceId());
        if (device.getStatus() == DeviceStatus.OFFLINE) {
            // 적용 시점이 아니라 적재 시점에 막는다(contract §4.12 — 프리뷰가 즉시 안내한다).
            throw new CustomException(ErrorCode.CT002);
        }
        return ControlChange.builder()
                .farmId(farmId)
                .zoneId(zoneId)
                .kind(ControlChangeKind.DEVICE)
                .deviceId(device.getId())
                .fromValue(device.getStatus().name())
                .toValue(targetStatus.name())
                .createdBy(userId)
                .build();
    }

    /**
     * 장비가 이 농장·이 존 소속인지 재확인(contract §4.12 계층·캐스케이드) — §4.10 자동 채움으로
     * 저장된 삼중조가 항상 완전하므로 {@code device.zoneId == zoneId} 비교로 충분하다. 미소속은
     * 존재를 유추당하지 않도록 미존재와 동일하게 404(E001).
     */
    private Device findZoneDeviceOrThrow(Long farmId, Long zoneId, Long deviceId) {
        Device device = deviceRepository.findByIdAndFarmId(deviceId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.E001));
        if (!zoneId.equals(device.getZoneId())) {
            throw new CustomException(ErrorCode.E001);
        }
        return device;
    }

    /**
     * 낙관적 검증(contract §4.12 동시성 1) — 잠금 하에서 읽은 현재 PENDING 집합과 클라이언트가 보고
     * 있던 집합이 정확히 같아야 한다. 다르면 <b>최신 큐를 실어</b> CT005로 거부한다.
     */
    private void verifyExpectedQueue(List<ControlChange> pending, List<Long> expectedChangeIds) {
        // ⚠️ contains(null)을 쓰지 않는다 — 불변 리스트(List.of)는 contains(null)에서 NPE를 던진다
        // (사이클 2 DeviceService#validateMetrics의 Set.of 함정과 동일한 부류). 잘못된 입력에 500이
        // 나가면 실패 안전 위반이므로 null 검사 자체가 널 안전해야 한다.
        if (expectedChangeIds.stream().anyMatch(Objects::isNull)) {
            throw new CustomException(ErrorCode.C001, "적용 대상 대기 항목 id에 null을 포함할 수 없습니다.");
        }
        Set<Long> current = pending.stream().map(ControlChange::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> expected = new LinkedHashSet<>(expectedChangeIds);
        if (!current.equals(expected)) {
            throw new ControlQueueConflictException(ControlChangeResponse.from(pending));
        }
    }

    /**
     * 목표값 적용 — 존×지표 1행 upsert. 잠금 하에서만 호출되므로 "없으면 생성" 경합이 없다.
     *
     * <p>모드 재확인은 방어적이다(도달 불가) — 모드 변경이 허용되지 않는 PENDING을 함께 폐기하고,
     * 그 전 구간이 같은 락 안이라 apply 시점에 어긋난 항목이 남을 수 없다. 그럼에도 어긋나면
     * 부분 적용 없이 트랜잭션 전체를 CT003으로 되돌린다.
     */
    private boolean applySetpoint(Long farmId, Long zoneId, Long userId, OperationMode mode, ControlChange change) {
        if (!mode.allowsSetpointChange()) {
            throw new CustomException(ErrorCode.CT003);
        }
        double target = Double.parseDouble(change.getToValue());
        ControlSetpoint setpoint = controlSetpointRepository.findByZoneIdAndMetric(zoneId, change.getMetric())
                .orElse(null);
        if (setpoint == null) {
            controlSetpointRepository.save(ControlSetpoint.builder()
                    .farmId(farmId)
                    .zoneId(zoneId)
                    .metric(change.getMetric())
                    .targetValue(target)
                    .updatedBy(userId)
                    .build());
        } else {
            setpoint.changeTarget(target, userId);
        }
        return true;
    }

    /**
     * 장비 조작 적용 — 대상이 사라졌거나(삭제·이전) 적재 이후 통신이 끊긴 장비는 <b>적용하지 않고
     * false</b>를 돌려 호출측이 폐기(skipped)하게 한다. OFFLINE을 덮어쓰면 장애 사실이 조작 결과에
     * 묻히고, 없는 장비를 적용하면 고아 참조가 된다.
     */
    private boolean applyDeviceChange(Long farmId, Long zoneId, OperationMode mode, ControlChange change) {
        if (!mode.allowsDeviceChange()) {
            throw new CustomException(ErrorCode.CT003);
        }
        Device device = deviceRepository.findByIdAndFarmId(change.getDeviceId(), farmId).orElse(null);
        if (device == null || !zoneId.equals(device.getZoneId()) || device.getStatus() == DeviceStatus.OFFLINE) {
            log.warn("제어 적용 건너뜀 — 대상 장비가 없거나 존을 벗어났거나 통신 두절: changeId={}, deviceId={}",
                    change.getId(), change.getDeviceId());
            return false;
        }
        device.changeStatus(DeviceStatus.valueOf(change.getToValue()));
        return true;
    }

    private void writeEmergencyStopLogs(Long farmId, Long userId, List<Long> zoneIds, List<Device> devices,
                                         List<ControlChange> discarded, LocalDateTime stoppedAt) {
        Map<Long, Long> stoppedByZone = devices.stream()
                .filter(device -> device.getZoneId() != null)
                .collect(Collectors.groupingBy(Device::getZoneId, Collectors.counting()));
        Map<Long, Long> discardedByZone = discarded.stream()
                .collect(Collectors.groupingBy(ControlChange::getZoneId, Collectors.counting()));

        for (Long zoneId : zoneIds) {
            long stopped = stoppedByZone.getOrDefault(zoneId, 0L);
            long dropped = discardedByZone.getOrDefault(zoneId, 0L);
            if (stopped == 0 && dropped == 0) {
                continue; // 영향이 없던 존까지 이력을 남기면 "최근 적용"이 빈 이벤트로 채워진다
            }
            controlApplyLogRepository.save(ControlApplyLog.builder()
                    .farmId(farmId)
                    .zoneId(zoneId)
                    .summary("비상 정지 — 장비 " + stopped + "대 OFF, 대기 항목 " + dropped + "건 폐기")
                    .itemCount((int) (stopped + dropped))
                    .appliedBy(userId)
                    .appliedAt(stoppedAt)
                    .build());
        }
    }

    private String summaryOf(int setpointCount, int deviceCount) {
        List<String> parts = new ArrayList<>();
        if (setpointCount > 0) {
            parts.add("목표값 " + setpointCount + "건");
        }
        if (deviceCount > 0) {
            parts.add("장비 " + deviceCount + "건");
        }
        return String.join(", ", parts) + " 적용";
    }

    /**
     * 큐에 담는 숫자 표기 — {@code 22.0} 같은 부동소수 표기가 그대로 화면에 나가지 않도록
     * 불필요한 0을 제거한 평문으로 고정한다(적용 시 {@code Double.parseDouble}로 되돌린다).
     */
    private String formatValue(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private ControlStateResponse buildState(Zone zone, ControlMode mode) {
        Map<SensorMetric, ControlSetpoint> setpointsByMetric =
                controlSetpointRepository.findByZoneIdOrderByMetricAsc(zone.getId()).stream()
                        .collect(Collectors.toMap(ControlSetpoint::getMetric, Function.identity(),
                                (first, second) -> first, () -> new EnumMap<>(SensorMetric.class)));
        List<ControlSetpointResponse> setpoints = CONTROLLABLE_METRICS.stream()
                .map(metric -> setpointsByMetric.containsKey(metric)
                        ? ControlSetpointResponse.from(setpointsByMetric.get(metric))
                        : ControlSetpointResponse.unset(metric))
                .toList();

        List<Device> devices = deviceRepository.findByZoneIdOrderByIdAsc(zone.getId());
        List<ControlChange> pending = controlChangeRepository.findByZoneIdAndStatusOrderByIdAsc(
                zone.getId(), ControlChangeStatus.PENDING);

        return new ControlStateResponse(
                zone.getId(),
                zone.getName(),
                mode != null ? mode.getMode() : OperationMode.AUTO,
                mode != null ? mode.getUpdatedBy() : null,
                mode != null ? Objects.requireNonNullElse(mode.getUpdatedAt(), mode.getCreatedAt()) : null,
                // 실기기가 없어 제어는 §4.11 시뮬레이터에만 작용한다 — 실제 기기를 제어하는 척하지 않는다.
                true,
                setpoints,
                ControlDeviceResponse.from(devices),
                ControlChangeResponse.from(pending),
                ControlApplyLogResponse.from(
                        controlApplyLogRepository.findTop5ByZoneIdOrderByAppliedAtDescIdDesc(zone.getId())));
    }
}
