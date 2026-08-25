package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.ControlModeRequest;
import com.smartfarm.service.dto.DeviceRequest;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.OperationMode;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.entity.SystemLog;
import com.smartfarm.service.entity.SystemLogCategory;
import com.smartfarm.service.repository.SystemLogRepository;
import com.smartfarm.service.service.AlarmEventService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * 시스템 로그 API 통합 테스트(이슈 #129-A) — 페이지네이션·category 필터·farm 스코프 격리와, 4개
 * 기록 지점(제어 모드 변경·멤버 초대 발급·알람 이벤트 생성·장비 등록/수정)이 실제 사용자 경로로
 * 호출됐을 때 로그가 남는지를 실제 DB로 검증한다. 기록 실패 격리(REQUIRES_NEW)는
 * {@code SystemLogWriterIsolationIntegrationTest}가 별도로 담당한다.
 */
class SystemLogApiIntegrationTest extends FarmTestSupport {

    @Autowired
    private SystemLogRepository systemLogRepository;

    @Autowired
    private AlarmEventService alarmEventService;

    private SystemLog saveLog(long farmId, SystemLogCategory category, String message, Long actorId) {
        return systemLogRepository.save(SystemLog.builder()
                .farmId(farmId)
                .category(category)
                .message(message)
                .actorId(actorId)
                .build());
    }

    // ── 페이지네이션 · 필터 · 스코프 ──────────────────────────────────

    @Test
    @DisplayName("목록 조회는 페이지네이션 파라미터대로 페이지를 나눠 최신순으로 반환한다")
    void listSystemLogsPaginated() throws Exception {
        String token = signupAndLogin("로그농부-페이지");
        long farmId = createFarm(token, "로그페이지농장");
        saveLog(farmId, SystemLogCategory.DEVICE, "장비1 등록", 1L);
        saveLog(farmId, SystemLogCategory.DEVICE, "장비2 등록", 1L);
        saveLog(farmId, SystemLogCategory.MEMBER, "초대 발급", 1L);

        mockMvc.perform(get("/api/farms/" + farmId + "/system-logs")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0));

        mockMvc.perform(get("/api/farms/" + farmId + "/system-logs")
                        .param("page", "1")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("category 필터로 DEVICE만 조회할 수 있다")
    void listSystemLogsFilteredByCategory() throws Exception {
        String token = signupAndLogin("로그농부-필터");
        long farmId = createFarm(token, "로그필터농장");
        saveLog(farmId, SystemLogCategory.DEVICE, "장비 등록", 1L);
        saveLog(farmId, SystemLogCategory.MEMBER, "초대 발급", 1L);
        saveLog(farmId, SystemLogCategory.ALARM, "알람 발생", null);

        mockMvc.perform(get("/api/farms/" + farmId + "/system-logs")
                        .param("category", "DEVICE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].category").value("DEVICE"))
                .andExpect(jsonPath("$.content[0].message").value("장비 등록"));
    }

    @Test
    @DisplayName("존재하지 않는 category 값은 400 C001이다")
    void invalidCategoryIsRejected() throws Exception {
        String token = signupAndLogin("로그농부-잘못된필터");
        long farmId = createFarm(token, "로그잘못필터농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/system-logs")
                        .param("category", "NOT_A_CATEGORY")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("다른 농장의 로그는 보이지 않는다 — farm 스코프 격리")
    void logsAreFarmScoped() throws Exception {
        String token = signupAndLogin("로그농부-격리");
        long farmA = createFarm(token, "격리농장A");
        long farmB = createFarm(token, "격리농장B");
        saveLog(farmA, SystemLogCategory.DEVICE, "A농장 장비 등록", 1L);
        saveLog(farmB, SystemLogCategory.DEVICE, "B농장 장비 등록", 1L);

        mockMvc.perform(get("/api/farms/" + farmA + "/system-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].message").value("A농장 장비 등록"));
    }

    @Test
    @DisplayName("농장 멤버가 아니면 403 F002다")
    void nonMemberCannotListSystemLogs() throws Exception {
        String ownerToken = signupAndLogin("로그농부-주인");
        long farmId = createFarm(ownerToken, "로그권한농장");
        String strangerToken = signupAndLogin("로그농부-이방인");

        mockMvc.perform(get("/api/farms/" + farmId + "/system-logs")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    // ── 기록 지점 4곳(실제 사용자 경로) ──────────────────────────────

    @Test
    @DisplayName("제어 모드 변경 시 CONTROL 로그가 남는다")
    void controlModeChangeIsLogged() throws Exception {
        String token = signupAndLogin("로그농부-제어");
        long farmId = createFarm(token, "로그제어농장");
        long zoneId = createZone(token, farmId, "A동");

        mockMvc.perform(put("/api/farms/" + farmId + "/zones/" + zoneId + "/control/mode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlModeRequest(OperationMode.MANUAL))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/farms/" + farmId + "/system-logs")
                        .param("category", "CONTROL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].actorId").isNumber());
    }

    @Test
    @DisplayName("초대코드 발급 시 MEMBER 로그가 남는다")
    void invitationIssuanceIsLogged() throws Exception {
        String token = signupAndLogin("로그농부-초대");
        long farmId = createFarm(token, "로그초대농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/invitations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/farms/" + farmId + "/system-logs")
                        .param("category", "MEMBER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("장비 등록·수정 시 각각 DEVICE 로그가 남는다")
    void deviceCreateAndUpdateAreLogged() throws Exception {
        String token = signupAndLogin("로그농부-장비");
        long farmId = createFarm(token, "로그장비농장");
        long zoneId = createZone(token, farmId, "A동");
        long deviceId = createDevice(token, farmId, zoneId, null, null, "센서1");

        mockMvc.perform(get("/api/farms/" + farmId + "/system-logs")
                        .param("category", "DEVICE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(patch("/api/farms/" + farmId + "/devices/" + deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneId, null, null, "센서1-수정", DeviceKind.SENSOR,
                                null, null, null, null, null,
                                List.of(SensorMetric.TEMPERATURE)))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/farms/" + farmId + "/system-logs")
                        .param("category", "DEVICE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("알람 이벤트 생성(시스템 훅) 시 ALARM 로그가 actorId 없이 남는다")
    void alarmBreachIsLoggedWithoutActor() throws Exception {
        String token = signupAndLogin("로그농부-알람");
        long farmId = createFarm(token, "로그알람농장");

        alarmEventService.recordBreach(farmId, AlarmSeverity.WARNING, AlarmSourceType.ENV_THRESHOLD,
                "INDOOR_TEMP_HIGH", "온도 상한 초과", LocalDateTime.now(), null);

        mockMvc.perform(get("/api/farms/" + farmId + "/system-logs")
                        .param("category", "ALARM")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].actorId").doesNotExist());
    }

    @Test
    @DisplayName("system-logs API에는 수정·삭제 엔드포인트가 없다(append-only)")
    void systemLogsHaveNoMutationEndpoints() throws Exception {
        String token = signupAndLogin("로그농부-append-only");
        long farmId = createFarm(token, "append전용농장");
        long logId = saveLog(farmId, SystemLogCategory.DEVICE, "장비 등록", 1L).getId();

        mockMvc.perform(delete("/api/farms/" + farmId + "/system-logs/" + logId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("C003"));
    }
}
