package com.smartfarm.service.controller;

import com.smartfarm.service.entity.FarmRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.ControlApplyRequest;
import com.smartfarm.service.dto.ControlChangeRequest;
import com.smartfarm.service.dto.ControlModeRequest;
import com.smartfarm.service.dto.DeviceRequest;
import com.smartfarm.service.entity.ControlChangeKind;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import com.smartfarm.service.entity.OperationMode;
import com.smartfarm.service.entity.SensorMetric;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 제어 도메인 API 통합 테스트(contract §4.12, 이슈 #100) — 모드별 허용 조작(CT003), 큐 적재·취소·
 * 일괄 적용, 낙관적 검증(CT005), 상한(CT004), 통신두절(CT002), 테넌트 격리(404), 캐스케이드까지
 * 전부 공개 API 경유로 검증한다. 스레드 경합 시나리오는 {@code ControlConcurrencyTest}가 담당한다.
 */
class ControlApiIntegrationTest extends FarmTestSupport {

    // ── 조회 · 모드 ────────────────────────────────────────

    @Test
    @DisplayName("제어 상태 조회: 모드 행이 없어도 AUTO로 성립하고 목표값 4종이 미설정으로 실린다")
    void findControlStateDefaultsToAuto() throws Exception {
        String token = signupAndLogin("제어-기본");
        long farmId = createFarm(token, "제어 농장");
        long zoneId = createZone(token, farmId, "A동");

        mockMvc.perform(get(controlPath(farmId, zoneId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneId").value(zoneId))
                .andExpect(jsonPath("$.mode").value("AUTO"))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.setpoints.length()").value(4))
                .andExpect(jsonPath("$.setpoints[0].targetValue").doesNotExist())
                .andExpect(jsonPath("$.pendingChanges").isEmpty())
                .andExpect(jsonPath("$.recentApplyLogs").isEmpty());
    }

    @Test
    @DisplayName("모드 변경: MANUAL로 바꾸면 새 모드에서 허용되지 않는 PENDING(목표값)은 함께 폐기된다")
    void changeModeDiscardsDisallowedPendingChanges() throws Exception {
        String token = signupAndLogin("제어-모드전환");
        long farmId = createFarm(token, "모드전환 농장");
        long zoneId = createZone(token, farmId, "A동");
        enqueueSetpoint(token, farmId, zoneId, SensorMetric.TEMPERATURE, 24.5);

        mockMvc.perform(put(controlPath(farmId, zoneId) + "/mode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlModeRequest(OperationMode.MANUAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MANUAL"))
                .andExpect(jsonPath("$.pendingChanges").isEmpty());
    }

    // ── 모드별 허용 조작(CT003) ─────────────────────────────

    @Test
    @DisplayName("AUTO에서 장비 직접 토글은 CT003으로 거부된다")
    void deviceToggleRejectedInAutoMode() throws Exception {
        String token = signupAndLogin("제어-자동토글");
        long farmId = createFarm(token, "자동토글 농장");
        long zoneId = createZone(token, farmId, "A동");
        long deviceId = createController(token, farmId, zoneId, "순환팬1");

        mockMvc.perform(post(controlPath(farmId, zoneId) + "/changes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlChangeRequest(
                                ControlChangeKind.DEVICE, null, null, deviceId, DeviceStatus.OFF))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CT003"));
    }

    @Test
    @DisplayName("MANUAL에서 목표값 편집은 CT003으로 거부된다")
    void setpointRejectedInManualMode() throws Exception {
        String token = signupAndLogin("제어-수동목표값");
        long farmId = createFarm(token, "수동목표값 농장");
        long zoneId = createZone(token, farmId, "A동");
        changeMode(token, farmId, zoneId, OperationMode.MANUAL);

        mockMvc.perform(post(controlPath(farmId, zoneId) + "/changes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlChangeRequest(
                                ControlChangeKind.SETPOINT, SensorMetric.TEMPERATURE, 24.0, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CT003"));
    }

    // ── 큐 적재 검증 ───────────────────────────────────────

    @Test
    @DisplayName("큐 적재는 장비에 즉시 반영하지 않는다 — 적용 전 장비 상태는 그대로다")
    void enqueueDoesNotApplyImmediately() throws Exception {
        String token = signupAndLogin("제어-지연반영");
        long farmId = createFarm(token, "지연반영 농장");
        long zoneId = createZone(token, farmId, "A동");
        long deviceId = createController(token, farmId, zoneId, "순환팬1");
        changeMode(token, farmId, zoneId, OperationMode.MANUAL);

        long changeId = enqueueDeviceToggle(token, farmId, zoneId, deviceId, DeviceStatus.OFF);

        JsonNode state = readJson(getControlState(token, farmId, zoneId));
        assertThat(state.get("pendingChanges").get(0).get("id").asLong()).isEqualTo(changeId);
        assertThat(state.get("pendingChanges").get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(deviceStatusOf(state, deviceId)).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("제어 불가 지표(EC)의 목표값은 C001로 거부된다")
    void uncontrollableMetricRejected() throws Exception {
        String token = signupAndLogin("제어-불가지표");
        long farmId = createFarm(token, "불가지표 농장");
        long zoneId = createZone(token, farmId, "A동");

        mockMvc.perform(post(controlPath(farmId, zoneId) + "/changes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlChangeRequest(
                                ControlChangeKind.SETPOINT, SensorMetric.EC, 2.0, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("허용 범위를 벗어난 목표값은 C001로 거부된다")
    void outOfRangeTargetRejected() throws Exception {
        String token = signupAndLogin("제어-범위초과");
        long farmId = createFarm(token, "범위초과 농장");
        long zoneId = createZone(token, farmId, "A동");

        mockMvc.perform(post(controlPath(farmId, zoneId) + "/changes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlChangeRequest(
                                ControlChangeKind.SETPOINT, SensorMetric.HUMIDITY, 500.0, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("통신 두절(OFFLINE) 장비 조작은 적재 시점에 CT002로 거부된다")
    void offlineDeviceRejectedAtEnqueue() throws Exception {
        String token = signupAndLogin("제어-두절장비");
        long farmId = createFarm(token, "두절장비 농장");
        long zoneId = createZone(token, farmId, "A동");
        long deviceId = createController(token, farmId, zoneId, "순환팬1");
        setDeviceStatus(token, farmId, deviceId, DeviceStatus.OFFLINE);
        changeMode(token, farmId, zoneId, OperationMode.MANUAL);

        mockMvc.perform(post(controlPath(farmId, zoneId) + "/changes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlChangeRequest(
                                ControlChangeKind.DEVICE, null, null, deviceId, DeviceStatus.OFF))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CT002"));
    }

    @Test
    @DisplayName("다른 존 소속 장비를 이 존 경로로 조작하면 404 E001(미소속은 미존재와 동일)")
    void deviceFromAnotherZoneRejected() throws Exception {
        String token = signupAndLogin("제어-타존장비");
        long farmId = createFarm(token, "타존장비 농장");
        long zoneA = createZone(token, farmId, "A동");
        long zoneB = createZone(token, farmId, "B동");
        long deviceInB = createController(token, farmId, zoneB, "B동 순환팬");
        changeMode(token, farmId, zoneA, OperationMode.MANUAL);

        mockMvc.perform(post(controlPath(farmId, zoneA) + "/changes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlChangeRequest(
                                ControlChangeKind.DEVICE, null, null, deviceInB, DeviceStatus.OFF))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("E001"));
    }

    @Test
    @DisplayName("큐 상한(존당 50건)을 넘기면 CT004로 거부된다")
    void queueCapExceeded() throws Exception {
        String token = signupAndLogin("제어-큐상한");
        long farmId = createFarm(token, "큐상한 농장");
        long zoneId = createZone(token, farmId, "A동");
        for (int i = 0; i < 50; i++) {
            enqueueSetpoint(token, farmId, zoneId, SensorMetric.TEMPERATURE, 20.0 + i * 0.1);
        }

        mockMvc.perform(post(controlPath(farmId, zoneId) + "/changes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlChangeRequest(
                                ControlChangeKind.SETPOINT, SensorMetric.CO2, 800.0, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CT004"));
    }

    // ── 취소 ──────────────────────────────────────────────

    @Test
    @DisplayName("개별 취소: 작성자 본인이 아니고 ADMIN도 아니면 403 A005")
    void cancelByOtherMemberRejected() throws Exception {
        String ownerToken = signupAndLogin("제어-주인장");
        String memberA = signupAndLogin("제어-멤버A");
        String memberB = signupAndLogin("제어-멤버B");
        long farmId = createFarm(ownerToken, "취소권한 농장");
        long zoneId = createZone(ownerToken, farmId, "A동");
        joinFarmAs(ownerToken, farmId, memberA, FarmRole.OPERATOR);
        joinFarmAs(ownerToken, farmId, memberB, FarmRole.OPERATOR);

        long changeId = enqueueSetpoint(memberA, farmId, zoneId, SensorMetric.TEMPERATURE, 23.0);

        mockMvc.perform(delete(controlPath(farmId, zoneId) + "/changes/" + changeId)
                        .header("Authorization", "Bearer " + memberB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A005"));

        // ADMIN은 남의 항목도 취소할 수 있다(contract §4.12 권한 표).
        mockMvc.perform(delete(controlPath(farmId, zoneId) + "/changes/" + changeId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("개별 취소: 이미 폐기된 항목을 다시 취소하면 404 CT001")
    void cancelAlreadyDiscardedChange() throws Exception {
        String token = signupAndLogin("제어-중복취소");
        long farmId = createFarm(token, "중복취소 농장");
        long zoneId = createZone(token, farmId, "A동");
        long changeId = enqueueSetpoint(token, farmId, zoneId, SensorMetric.TEMPERATURE, 23.0);

        mockMvc.perform(delete(controlPath(farmId, zoneId) + "/changes/" + changeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(controlPath(farmId, zoneId) + "/changes/" + changeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CT001"));
    }

    @Test
    @DisplayName("전체 되돌리기: 존의 PENDING이 모두 비워진다")
    void cancelAllChanges() throws Exception {
        String token = signupAndLogin("제어-전체취소");
        long farmId = createFarm(token, "전체취소 농장");
        long zoneId = createZone(token, farmId, "A동");
        enqueueSetpoint(token, farmId, zoneId, SensorMetric.TEMPERATURE, 23.0);
        enqueueSetpoint(token, farmId, zoneId, SensorMetric.CO2, 800.0);

        mockMvc.perform(delete(controlPath(farmId, zoneId) + "/changes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(controlPath(farmId, zoneId)).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.pendingChanges").isEmpty());
    }

    // ── 적용 ──────────────────────────────────────────────

    @Test
    @DisplayName("적용: 목표값·장비 조작이 한 번에 반영되고 적용 이력이 남는다")
    void applyAppliesQueueAndWritesLog() throws Exception {
        String token = signupAndLogin("제어-적용");
        long farmId = createFarm(token, "적용 농장");
        long zoneId = createZone(token, farmId, "A동");
        long changeId = enqueueSetpoint(token, farmId, zoneId, SensorMetric.TEMPERATURE, 24.5);

        mockMvc.perform(post(controlPath(farmId, zoneId) + "/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlApplyRequest(List.of(changeId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.state.pendingChanges").isEmpty())
                .andExpect(jsonPath("$.state.recentApplyLogs.length()").value(1))
                .andExpect(jsonPath("$.state.recentApplyLogs[0].itemCount").value(1));

        JsonNode state = readJson(getControlState(token, farmId, zoneId));
        assertThat(targetValueOf(state, SensorMetric.TEMPERATURE)).isEqualTo(24.5);
    }

    @Test
    @DisplayName("적용: MANUAL 모드의 장비 끄기가 장비 상태 OFF로 반영된다")
    void applyDeviceToggleTurnsDeviceOff() throws Exception {
        String token = signupAndLogin("제어-장비끄기");
        long farmId = createFarm(token, "장비끄기 농장");
        long zoneId = createZone(token, farmId, "A동");
        long deviceId = createController(token, farmId, zoneId, "순환팬1");
        changeMode(token, farmId, zoneId, OperationMode.MANUAL);
        long changeId = enqueueDeviceToggle(token, farmId, zoneId, deviceId, DeviceStatus.OFF);

        mockMvc.perform(post(controlPath(farmId, zoneId) + "/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlApplyRequest(List.of(changeId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(1));

        assertThat(deviceStatusOf(readJson(getControlState(token, farmId, zoneId)), deviceId)).isEqualTo("OFF");
    }

    @Test
    @DisplayName("적용: expectedChangeIds가 현재 큐와 다르면 CT005 + 최신 큐를 응답에 싣는다")
    void applyRejectsStaleExpectedIds() throws Exception {
        String token = signupAndLogin("제어-낙관검증");
        long farmId = createFarm(token, "낙관검증 농장");
        long zoneId = createZone(token, farmId, "A동");
        long first = enqueueSetpoint(token, farmId, zoneId, SensorMetric.TEMPERATURE, 24.0);
        long second = enqueueSetpoint(token, farmId, zoneId, SensorMetric.CO2, 800.0);

        // 클라이언트는 first만 보고 있었는데 그 사이 second가 추가된 상황.
        mockMvc.perform(post(controlPath(farmId, zoneId) + "/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlApplyRequest(List.of(first)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CT005"))
                .andExpect(jsonPath("$.pendingChanges.length()").value(2))
                .andExpect(jsonPath("$.pendingChanges[1].id").value(second));

        // 거부됐으므로 아무것도 적용되지 않았다(부분 적용 금지).
        JsonNode state = readJson(getControlState(token, farmId, zoneId));
        assertThat(state.get("pendingChanges")).hasSize(2);
        assertThat(targetValueOf(state, SensorMetric.TEMPERATURE)).isNull();
    }

    @Test
    @DisplayName("적용: expectedChangeIds가 큐 상한(50)을 넘으면 400 C001 — 대용량 배열로 힙을 밀어넣을 수 없다")
    void applyRejectsOversizedExpectedIds() throws Exception {
        String token = signupAndLogin("제어-대용량ids");
        long farmId = createFarm(token, "대용량ids 농장");
        long zoneId = createZone(token, farmId, "A동");
        List<Long> oversized = java.util.stream.LongStream.rangeClosed(1, 51).boxed().toList();

        mockMvc.perform(post(controlPath(farmId, zoneId) + "/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlApplyRequest(oversized))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("적용: 빈 큐를 빈 expectedChangeIds로 적용하면 0건 성공이고 이력은 남지 않는다")
    void applyEmptyQueue() throws Exception {
        String token = signupAndLogin("제어-빈큐적용");
        long farmId = createFarm(token, "빈큐 농장");
        long zoneId = createZone(token, farmId, "A동");

        mockMvc.perform(post(controlPath(farmId, zoneId) + "/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlApplyRequest(List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(0))
                .andExpect(jsonPath("$.state.recentApplyLogs").isEmpty());
    }

    // ── 비상 정지 ─────────────────────────────────────────

    @Test
    @DisplayName("비상 정지: 전 존 MANUAL + 장비 OFF + 대기 큐 전량 폐기")
    void emergencyStopStopsEverything() throws Exception {
        String token = signupAndLogin("제어-비상정지");
        long farmId = createFarm(token, "비상정지 농장");
        long zoneA = createZone(token, farmId, "A동");
        long zoneB = createZone(token, farmId, "B동");
        long deviceA = createController(token, farmId, zoneA, "A동 순환팬");
        long deviceB = createController(token, farmId, zoneB, "B동 순환팬");
        enqueueSetpoint(token, farmId, zoneA, SensorMetric.TEMPERATURE, 24.0);
        enqueueSetpoint(token, farmId, zoneB, SensorMetric.CO2, 800.0);

        mockMvc.perform(post("/api/farms/" + farmId + "/control/emergency-stop")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneCount").value(2))
                .andExpect(jsonPath("$.stoppedDeviceCount").value(2))
                .andExpect(jsonPath("$.discardedChangeCount").value(2))
                .andExpect(jsonPath("$.simulated").value(true));

        for (long zoneId : List.of(zoneA, zoneB)) {
            JsonNode state = readJson(getControlState(token, farmId, zoneId));
            assertThat(state.get("mode").asText()).isEqualTo("MANUAL");
            assertThat(state.get("pendingChanges")).isEmpty();
            assertThat(state.get("recentApplyLogs")).hasSize(1);
        }
        JsonNode stateA = readJson(getControlState(token, farmId, zoneA));
        assertThat(deviceStatusOf(stateA, deviceA)).isEqualTo("OFF");
        assertThat(deviceStatusOf(readJson(getControlState(token, farmId, zoneB)), deviceB)).isEqualTo("OFF");
    }

    @Test
    @DisplayName("비상 정지는 제어기(kind=CONTROLLER)만 끈다 — 센서·게이트웨이는 그대로 측정·통신한다")
    void emergencyStopOnlyStopsControllers() throws Exception {
        String token = signupAndLogin("제어-제어기만");
        long farmId = createFarm(token, "제어기만 농장");
        long zoneId = createZone(token, farmId, "A동");
        long controllerId = createController(token, farmId, zoneId, "순환팬");
        long sensorId = createDevice(token, farmId, zoneId, null, null, "온도센서");
        long gatewayId = createGateway(token, farmId, zoneId, "게이트웨이");

        mockMvc.perform(post("/api/farms/" + farmId + "/control/emergency-stop")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedDeviceCount").value(1));

        JsonNode state = readJson(getControlState(token, farmId, zoneId));
        assertThat(deviceStatusOf(state, controllerId)).isEqualTo("OFF");
        assertThat(deviceStatusOf(state, sensorId)).isEqualTo("NORMAL");
        assertThat(deviceStatusOf(state, gatewayId)).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("장비 요약: 비상 정지 후 off 대수가 KPI에 잡힌다 — total = normal+warning+faultOrOffline+off")
    void deviceSummaryReportsOffCount() throws Exception {
        String token = signupAndLogin("제어-요약off");
        long farmId = createFarm(token, "요약off 농장");
        long zoneId = createZone(token, farmId, "A동");
        createController(token, farmId, zoneId, "순환팬1");
        createController(token, farmId, zoneId, "순환팬2");
        createDevice(token, farmId, zoneId, null, null, "온도센서");

        mockMvc.perform(post("/api/farms/" + farmId + "/control/emergency-stop")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/farms/" + farmId + "/devices/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.normal").value(1))
                .andExpect(jsonPath("$.warning").value(0))
                .andExpect(jsonPath("$.faultOrOffline").value(0))
                // off가 없으면 "전 제어기 정지"가 화면에서 "이상 없음"으로 읽힌다.
                .andExpect(jsonPath("$.off").value(2));
    }

    @Test
    @DisplayName("비상 정지: 정지 대상이 하나도 없어도 감사 이력 1행을 남긴다 — 호출 사실이 사라지면 안 된다")
    void emergencyStopWithNoEffectStillWritesAuditLog() throws Exception {
        String token = signupAndLogin("제어-무영향정지");
        long farmId = createFarm(token, "무영향정지 농장");
        long zoneId = createZone(token, farmId, "A동");
        // 제어기도 대기 항목도 없다 — 정지 대상 0.

        mockMvc.perform(post("/api/farms/" + farmId + "/control/emergency-stop")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedDeviceCount").value(0))
                .andExpect(jsonPath("$.discardedChangeCount").value(0));

        JsonNode state = readJson(getControlState(token, farmId, zoneId));
        assertThat(state.get("recentApplyLogs"))
                .as("영향 0이라고 기록을 생략하면 '정지를 눌렀는가'를 사후에 확인할 수 없다")
                .hasSize(1);
        assertThat(state.get("recentApplyLogs").get(0).get("itemCount").asInt()).isZero();
        assertThat(state.get("recentApplyLogs").get(0).get("summary").asText()).contains("비상 정지");
    }

    @Test
    @DisplayName("비상 정지는 OPERATOR 이상 — OPERATOR는 성공하고 VIEWER는 403 F007 (#122 결정 ⓐ)")
    void emergencyStopRequiresOperator() throws Exception {
        // ⚠️ 구 계약은 OWNER 전용이었다. 이슈 #122에서 "장비를 켜고 끄는 건 되면서 비상 정지만
        // 막히는" 불일치를 해소하려고 OPERATOR 이상으로 완화했다 — 구 MEMBER(→OPERATOR)는
        // 권한을 잃지 않고 오히려 획득한다(기능 회귀 아님).
        String ownerToken = signupAndLogin("제어-정지주인");
        String operatorToken = signupAndLogin("제어-정지멤버");
        String viewerToken = signupAndLogin("제어-정지조회");
        long farmId = createFarm(ownerToken, "정지권한 농장");
        joinFarmAs(ownerToken, farmId, operatorToken, FarmRole.OPERATOR);
        joinFarmAs(ownerToken, farmId, viewerToken, FarmRole.VIEWER);

        mockMvc.perform(post("/api/farms/" + farmId + "/control/emergency-stop")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/farms/" + farmId + "/control/emergency-stop")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F007"));
    }

    // ── 테넌트 격리 · 데모 차단 ─────────────────────────────

    @Test
    @DisplayName("다른 농장의 zoneId를 내 farmId 경로에 끼워 넣으면 404 R001(존재 유추 차단)")
    void crossTenantZoneRejected() throws Exception {
        String tokenA = signupAndLogin("제어-테넌트A");
        String tokenB = signupAndLogin("제어-테넌트B");
        long farmA = createFarm(tokenA, "테넌트A 농장");
        long farmB = createFarm(tokenB, "테넌트B 농장");
        long zoneB = createZone(tokenB, farmB, "B동");

        mockMvc.perform(get(controlPath(farmA, zoneB)).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));

        mockMvc.perform(post(controlPath(farmA, zoneB) + "/changes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlChangeRequest(
                                ControlChangeKind.SETPOINT, SensorMetric.TEMPERATURE, 24.0, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    @Test
    @DisplayName("다른 농장의 changeId를 내 경로로 취소하면 404 CT001")
    void crossTenantChangeRejected() throws Exception {
        String tokenA = signupAndLogin("제어-큐테넌트A");
        String tokenB = signupAndLogin("제어-큐테넌트B");
        long farmA = createFarm(tokenA, "큐테넌트A 농장");
        long zoneA = createZone(tokenA, farmA, "A동");
        long farmB = createFarm(tokenB, "큐테넌트B 농장");
        long zoneB = createZone(tokenB, farmB, "B동");
        long changeInB = enqueueSetpoint(tokenB, farmB, zoneB, SensorMetric.TEMPERATURE, 24.0);

        mockMvc.perform(delete(controlPath(farmA, zoneA) + "/changes/" + changeInB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CT001"));
    }

    @Test
    @DisplayName("데모 계정은 제어 쓰기 작업이 전부 403 A007(조회는 허용)")
    void demoAccountBlockedForControlWrites() throws Exception {
        String demoToken = demoAccountLogin();
        long demoFarmId = demoAccountFarmId(demoToken);

        mockMvc.perform(put("/api/farms/" + demoFarmId + "/zones/1/control/mode")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlModeRequest(OperationMode.MANUAL))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"));

        mockMvc.perform(post("/api/farms/" + demoFarmId + "/control/emergency-stop")
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"));
    }

    // ── 캐스케이드 ─────────────────────────────────────────

    @Test
    @DisplayName("캐스케이드: 존을 삭제하면 그 존의 PENDING 큐가 폐기되고 목표값도 함께 사라진다")
    void zoneDeletionDiscardsPendingQueue() throws Exception {
        String token = signupAndLogin("제어-존삭제");
        long farmId = createFarm(token, "존삭제 농장");
        long zoneId = createZone(token, farmId, "A동");
        long applied = enqueueSetpoint(token, farmId, zoneId, SensorMetric.TEMPERATURE, 24.0);
        applyQueue(token, farmId, zoneId, List.of(applied));
        enqueueSetpoint(token, farmId, zoneId, SensorMetric.CO2, 800.0);

        mockMvc.perform(delete("/api/farms/" + farmId + "/zones/" + zoneId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 존이 사라졌으니 제어 표면 자체가 R001 — 큐·목표값이 되살아날 경로가 없다.
        mockMvc.perform(get(controlPath(farmId, zoneId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));

        // 같은 이름의 존을 새로 만들어도 이전 목표값이 따라오지 않는다(목표값이 존과 함께 soft delete).
        long newZoneId = createZone(token, farmId, "A동");
        JsonNode state = readJson(getControlState(token, farmId, newZoneId));
        assertThat(targetValueOf(state, SensorMetric.TEMPERATURE)).isNull();
    }

    @Test
    @DisplayName("캐스케이드: 장비를 삭제하면 그 장비를 참조하는 PENDING 큐가 폐기된다")
    void deviceDeletionDiscardsPendingQueue() throws Exception {
        String token = signupAndLogin("제어-장비삭제");
        long farmId = createFarm(token, "장비삭제 농장");
        long zoneId = createZone(token, farmId, "A동");
        long deviceId = createController(token, farmId, zoneId, "순환팬1");
        changeMode(token, farmId, zoneId, OperationMode.MANUAL);
        enqueueDeviceToggle(token, farmId, zoneId, deviceId, DeviceStatus.OFF);

        mockMvc.perform(delete("/api/farms/" + farmId + "/devices/" + deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(controlPath(farmId, zoneId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingChanges").isEmpty());
    }

    // ── 헬퍼 ──────────────────────────────────────────────

    private String controlPath(long farmId, long zoneId) {
        return "/api/farms/" + farmId + "/zones/" + zoneId + "/control";
    }

    private MvcResult getControlState(String token, long farmId, long zoneId) throws Exception {
        return mockMvc.perform(get(controlPath(farmId, zoneId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void changeMode(String token, long farmId, long zoneId, OperationMode mode) throws Exception {
        mockMvc.perform(put(controlPath(farmId, zoneId) + "/mode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlModeRequest(mode))))
                .andExpect(status().isOk());
    }

    private long enqueueSetpoint(String token, long farmId, long zoneId, SensorMetric metric, double target)
            throws Exception {
        MvcResult result = mockMvc.perform(post(controlPath(farmId, zoneId) + "/changes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlChangeRequest(
                                ControlChangeKind.SETPOINT, metric, target, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private long enqueueDeviceToggle(String token, long farmId, long zoneId, long deviceId, DeviceStatus target)
            throws Exception {
        MvcResult result = mockMvc.perform(post(controlPath(farmId, zoneId) + "/changes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlChangeRequest(
                                ControlChangeKind.DEVICE, null, null, deviceId, target))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private void applyQueue(String token, long farmId, long zoneId, List<Long> expectedIds) throws Exception {
        mockMvc.perform(post(controlPath(farmId, zoneId) + "/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlApplyRequest(expectedIds))))
                .andExpect(status().isOk());
    }

    private long createController(String token, long farmId, long zoneId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneId, null, null, name, DeviceKind.CONTROLLER,
                                null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private long createGateway(String token, long farmId, long zoneId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneId, null, null, name, DeviceKind.GATEWAY,
                                null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private void setDeviceStatus(String token, long farmId, long deviceId, DeviceStatus status) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/farms/" + farmId + "/devices/" + deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                null, null, null, null, null, null, null, status, null, null, null))))
                .andExpect(status().isOk());
    }

    private String deviceStatusOf(JsonNode state, long deviceId) {
        for (JsonNode device : state.get("devices")) {
            if (device.get("id").asLong() == deviceId) {
                return device.get("status").asText();
            }
        }
        throw new IllegalStateException("장비를 찾을 수 없음: " + deviceId);
    }

    private Double targetValueOf(JsonNode state, SensorMetric metric) {
        for (JsonNode setpoint : state.get("setpoints")) {
            if (metric.name().equals(setpoint.get("metric").asText())) {
                JsonNode value = setpoint.get("targetValue");
                return value == null || value.isNull() ? null : value.asDouble();
            }
        }
        throw new IllegalStateException("목표값 항목을 찾을 수 없음: " + metric);
    }
}
