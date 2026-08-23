package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.dto.ControlApplyRequest;
import com.smartfarm.service.dto.ControlApplyResponse;
import com.smartfarm.service.dto.ControlChangeRequest;
import com.smartfarm.service.dto.ControlChangeResponse;
import com.smartfarm.service.dto.ControlModeRequest;
import com.smartfarm.service.dto.ControlStateResponse;
import com.smartfarm.service.dto.DeviceRequest;
import com.smartfarm.service.dto.FarmRequest;
import com.smartfarm.service.dto.SignupRequest;
import com.smartfarm.service.dto.ZoneRequest;
import com.smartfarm.service.entity.ControlChangeKind;
import com.smartfarm.service.entity.ControlChangeStatus;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.entity.OperationMode;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.exception.ControlQueueConflictException;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.ControlChangeRepository;
import com.smartfarm.service.repository.DeviceRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 제어 도메인 동시성 경계 검증(contract §4.12 동시성 1~5, 이슈 #100 Critical) — 서비스 계층을 직접
 * 호출해 <b>스레드별 개별 트랜잭션</b>으로 경합시킨다({@code FarmInvitationConcurrencyTest} 선례.
 * MockMvc 경유로는 한 스레드 안에서 순차 실행돼 락 경합이 재현되지 않는다).
 *
 * <p>검증하는 불변식:
 * <ol>
 *   <li>같은 존 동시 apply — 정확히 1건만 성공하고 나머지는 CT005(중복 적용·부분 적용 없음)</li>
 *   <li>apply 직전 큐 변경 — CT005로 거부되고 아무것도 적용되지 않는다</li>
 *   <li>비상 정지와 apply 경합 — 어느 순서로 끝나든 PENDING 0건 · 장비 OFF · MANUAL로 수렴한다
 *       (정지 후 잔존 큐가 나중에 적용되는 경로가 없다)</li>
 *   <li>모드 변경과 큐 적재 경합 — MANUAL 확정 후 SETPOINT PENDING이 남지 않는다(CT003 불변식)</li>
 *   <li>큐 상한(CT004) 경합 — 상한 판정이 락 안쪽이라 49→51로 새지 않는다</li>
 * </ol>
 */
class ControlConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private AuthService authService;

    @Autowired
    private FarmService farmService;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private ControlService controlService;

    @Autowired
    private ControlChangeRepository controlChangeRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    private record ConcurrentResult<T>(List<T> successes, List<Throwable> failures) {
    }

    private <T> ConcurrentResult<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(tasks.size());
        List<T> successes = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        for (Callable<T> task : tasks) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    successes.add(task.call());
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        assertThat(doneLatch.await(60, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();
        return new ConcurrentResult<>(successes, failures);
    }

    // ── ① 같은 존 동시 apply ────────────────────────────────

    @Test
    @DisplayName("같은 존 동시 apply — 1건만 성공하고 나머지는 CT005, 큐는 정확히 한 번만 적용된다")
    void concurrentApplyOnSameZoneAppliesExactlyOnce() throws Exception {
        Fixture fixture = fixture("동시적용");
        long changeId = enqueueSetpoint(fixture, SensorMetric.TEMPERATURE, 24.5);
        ControlApplyRequest request = new ControlApplyRequest(List.of(changeId));

        ConcurrentResult<ControlApplyResponse> result = runConcurrently(List.of(
                () -> controlService.apply(fixture.farmId(), fixture.ownerId(), fixture.zoneId(), request),
                () -> controlService.apply(fixture.farmId(), fixture.ownerId(), fixture.zoneId(), request)));

        assertThat(result.successes()).hasSize(1);
        assertThat(result.successes().get(0).appliedCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0)).isInstanceOf(ControlQueueConflictException.class);

        ControlStateResponse state = state(fixture);
        assertThat(state.pendingChanges()).isEmpty();
        // 적용 이력이 1건 = 실제로 한 번만 적용됐다(중복 적용이면 2건이 남는다).
        assertThat(state.recentApplyLogs()).hasSize(1);
        assertThat(targetOf(state, SensorMetric.TEMPERATURE)).isEqualTo(24.5);
    }

    // ── ② apply 직전 큐 변경 ────────────────────────────────

    @Test
    @DisplayName("apply 직전 다른 사용자가 큐에 항목을 추가하면 CT005 — 최신 큐가 예외에 실리고 아무것도 적용되지 않는다")
    void applyRejectedWhenQueueChangedBeforeApply() throws Exception {
        Fixture fixture = fixture("큐변경");
        long first = enqueueSetpoint(fixture, SensorMetric.TEMPERATURE, 24.5);
        List<Long> staleExpectation = List.of(first);
        long second = enqueueSetpoint(fixture, SensorMetric.CO2, 800.0);

        ControlQueueConflictException conflict = null;
        try {
            controlService.apply(fixture.farmId(), fixture.ownerId(), fixture.zoneId(),
                    new ControlApplyRequest(staleExpectation));
        } catch (ControlQueueConflictException e) {
            conflict = e;
        }

        assertThat(conflict).isNotNull();
        assertThat(conflict.getErrorCode()).isEqualTo(ErrorCode.CT005);
        assertThat(conflict.getPendingChanges()).extracting(ControlChangeResponse::id)
                .containsExactly(first, second);

        ControlStateResponse state = state(fixture);
        assertThat(state.pendingChanges()).hasSize(2);
        assertThat(targetOf(state, SensorMetric.TEMPERATURE)).isNull();
    }

    // ── ③ 비상 정지 vs apply ────────────────────────────────

    @Test
    @DisplayName("비상 정지와 apply 경합 — 어느 순서로 끝나든 PENDING 0건·장비 OFF·MANUAL로 수렴한다")
    void emergencyStopRacingWithApplyConvergesToStoppedState() throws Exception {
        Fixture fixture = fixture("비상경합");
        long deviceId = createController(fixture, "순환팬1");
        controlService.changeMode(fixture.farmId(), fixture.ownerId(), fixture.zoneId(),
                new ControlModeRequest(OperationMode.MANUAL));
        long changeId = enqueueDeviceToggle(fixture, deviceId, DeviceStatus.NORMAL);
        ControlApplyRequest request = new ControlApplyRequest(List.of(changeId));

        ConcurrentResult<Object> result = runConcurrently(List.of(
                () -> controlService.apply(fixture.farmId(), fixture.ownerId(), fixture.zoneId(), request),
                () -> controlService.emergencyStop(fixture.farmId(), fixture.ownerId())));

        // 비상 정지는 항상 성공한다(apply만 CT005로 밀릴 수 있다 — 정지가 먼저 큐를 비운 경우).
        assertThat(result.failures()).allSatisfy(failure ->
                assertThat(failure).isInstanceOf(ControlQueueConflictException.class));
        assertThat(result.successes()).isNotEmpty();

        ControlStateResponse state = state(fixture);
        assertThat(state.mode()).isEqualTo(OperationMode.MANUAL);
        assertThat(state.pendingChanges()).isEmpty();
        assertThat(controlChangeRepository.findByFarmIdAndStatusOrderByIdAsc(
                fixture.farmId(), ControlChangeStatus.PENDING)).isEmpty();
        // 핵심 불변식: 정지 이후에 "켜기" 항목이 살아남아 장비를 되살리는 경로가 없어야 한다.
        assertThat(deviceRepository.findById(deviceId).orElseThrow().getStatus()).isEqualTo(DeviceStatus.OFF);
    }

    // ── ④ 모드 변경 vs 큐 적재 ──────────────────────────────

    @Test
    @DisplayName("모드 변경(MANUAL)과 목표값 적재 경합 — MANUAL 확정 후 SETPOINT PENDING이 남지 않는다")
    void modeChangeRacingWithSetpointEnqueueKeepsInvariant() throws Exception {
        Fixture fixture = fixture("모드경합");
        ControlChangeRequest setpointRequest = new ControlChangeRequest(
                ControlChangeKind.SETPOINT, SensorMetric.TEMPERATURE, 24.0, null, null);

        ConcurrentResult<Object> result = runConcurrently(List.of(
                () -> controlService.enqueueChange(fixture.farmId(), fixture.ownerId(), fixture.zoneId(),
                        setpointRequest),
                () -> controlService.changeMode(fixture.farmId(), fixture.ownerId(), fixture.zoneId(),
                        new ControlModeRequest(OperationMode.MANUAL))));

        // 적재는 성공(모드 변경 전)하거나 CT003(모드 변경 후)이다 — 그 외 실패는 없어야 한다.
        assertThat(result.failures()).allSatisfy(failure -> {
            assertThat(failure).isInstanceOf(CustomException.class);
            assertThat(((CustomException) failure).getErrorCode()).isEqualTo(ErrorCode.CT003);
        });

        ControlStateResponse state = state(fixture);
        assertThat(state.mode()).isEqualTo(OperationMode.MANUAL);
        assertThat(state.pendingChanges())
                .as("MANUAL에서 적용될 수 없는 SETPOINT 항목이 큐에 남으면 안 된다(영구 교착)")
                .noneSatisfy(change -> assertThat(change.kind()).isEqualTo(ControlChangeKind.SETPOINT));
    }

    // ── ⑤ 큐 상한 경합 ─────────────────────────────────────

    @Test
    @DisplayName("큐 상한 직전 동시 적재 — 정확히 1건만 성공하고 나머지는 CT004(상한 판정이 락 안쪽)")
    void concurrentEnqueueAtCapAllowsExactlyOne() throws Exception {
        Fixture fixture = fixture("상한경합");
        for (int i = 0; i < ControlService.MAX_PENDING_PER_ZONE - 1; i++) {
            enqueueSetpoint(fixture, SensorMetric.TEMPERATURE, 20.0 + i * 0.1);
        }
        ControlChangeRequest request = new ControlChangeRequest(
                ControlChangeKind.SETPOINT, SensorMetric.CO2, 800.0, null, null);

        ConcurrentResult<ControlChangeResponse> result = runConcurrently(List.of(
                () -> controlService.enqueueChange(fixture.farmId(), fixture.ownerId(), fixture.zoneId(), request),
                () -> controlService.enqueueChange(fixture.farmId(), fixture.ownerId(), fixture.zoneId(), request)));

        assertThat(result.successes()).hasSize(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(((CustomException) result.failures().get(0)).getErrorCode()).isEqualTo(ErrorCode.CT004);
        assertThat(controlChangeRepository.countByZoneIdAndStatus(fixture.zoneId(), ControlChangeStatus.PENDING))
                .isEqualTo(ControlService.MAX_PENDING_PER_ZONE);
    }

    // ── 픽스처 ─────────────────────────────────────────────

    private record Fixture(Long ownerId, Long farmId, Long zoneId) {
    }

    private Fixture fixture(String label) {
        Long ownerId = authService.signup(new SignupRequest(
                "control-conc-" + UUID.randomUUID() + "@example.com", "password123", label)).id();
        Long farmId = farmService.createFarm(ownerId,
                new FarmRequest(label + " 농장", CropType.TOMATO, null)).id();
        Long zoneId = zoneService.createZone(farmId, ownerId, new ZoneRequest("A동", null)).id();
        return new Fixture(ownerId, farmId, zoneId);
    }

    private long createController(Fixture fixture, String name) {
        return deviceService.createDevice(fixture.farmId(), fixture.ownerId(), new DeviceRequest(
                fixture.zoneId(), null, null, name, DeviceKind.CONTROLLER,
                null, null, null, null, null, null)).id();
    }

    private long enqueueSetpoint(Fixture fixture, SensorMetric metric, double target) {
        return controlService.enqueueChange(fixture.farmId(), fixture.ownerId(), fixture.zoneId(),
                new ControlChangeRequest(ControlChangeKind.SETPOINT, metric, target, null, null)).id();
    }

    private long enqueueDeviceToggle(Fixture fixture, long deviceId, DeviceStatus target) {
        return controlService.enqueueChange(fixture.farmId(), fixture.ownerId(), fixture.zoneId(),
                new ControlChangeRequest(ControlChangeKind.DEVICE, null, null, deviceId, target)).id();
    }

    private ControlStateResponse state(Fixture fixture) {
        return controlService.findControlState(fixture.farmId(), fixture.ownerId(), fixture.zoneId());
    }

    private Double targetOf(ControlStateResponse state, SensorMetric metric) {
        return state.setpoints().stream()
                .filter(setpoint -> setpoint.metric() == metric)
                .findFirst()
                .orElseThrow()
                .targetValue();
    }
}
