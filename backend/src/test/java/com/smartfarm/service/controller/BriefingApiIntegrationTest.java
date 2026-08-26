package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.DeviceRequest;
import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.repository.AlarmEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * 홈 화면 브리핑 API 통합 테스트(이슈 #129-B) — 응답값이 기존 조회(미확인 알람 건수·보정 임박 장비
 * 수)와 <b>정확히 일치</b>하는지, farm 스코프가 지켜지는지, {@code harvestDueSoon}이 응답에 없는지를
 * 검증한다.
 */
class BriefingApiIntegrationTest extends FarmTestSupport {

    @Autowired
    private AlarmEventRepository alarmEventRepository;

    @Test
    @DisplayName("actionRequiredCount는 미확인 알람 건수와 정확히 일치한다")
    void actionRequiredCountMatchesUnacknowledgedAlarms() throws Exception {
        String token = signupAndLogin("브리핑농부-알람");
        long farmId = createFarm(token, "브리핑알람농장");
        saveAlarmEvent(farmId, "INDOOR_TEMP_HIGH");
        saveAlarmEvent(farmId, "INDOOR_TEMP_LOW");

        // 하나는 확인 처리 — actionRequiredCount에서 빠져야 한다.
        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long ackTargetId = saveAlarmEvent(farmId, "INDOOR_HUMIDITY_HIGH").getId();
        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-events/" + ackTargetId + "/acknowledge")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/unacknowledged-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(get("/api/farms/" + farmId + "/briefing")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionRequiredCount").value(2));
    }

    @Test
    @DisplayName("calibrationDueSoonCount는 장비 요약의 calibrationDueSoon과 정확히 일치한다")
    void calibrationDueSoonCountMatchesDeviceSummary() throws Exception {
        String token = signupAndLogin("브리핑농부-보정");
        long farmId = createFarm(token, "브리핑보정농장");
        long zoneId = createZone(token, farmId, "A동");

        createDeviceWithCalibration(token, farmId, zoneId, "센서-임박", LocalDateTime.now().plusDays(10));
        createDeviceWithCalibration(token, farmId, zoneId, "센서-여유", LocalDateTime.now().plusDays(90));

        mockMvc.perform(get("/api/farms/" + farmId + "/devices/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calibrationDueSoon").value(1));

        mockMvc.perform(get("/api/farms/" + farmId + "/briefing")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calibrationDueSoonCount").value(1));
    }

    @Test
    @DisplayName("응답에 harvestDueSoon 필드는 존재하지 않는다(랙별 재배 사이클 도메인 없음 — #130)")
    void responseDoesNotContainHarvestDueSoon() throws Exception {
        String token = signupAndLogin("브리핑농부-수확");
        long farmId = createFarm(token, "브리핑수확농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/briefing")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.harvestDueSoon").doesNotExist())
                .andExpect(jsonPath("$.harvestDueSoonCount").doesNotExist());
    }

    @Test
    @DisplayName("농장 멤버가 아니면 403 F002다")
    void nonMemberCannotViewBriefing() throws Exception {
        String ownerToken = signupAndLogin("브리핑농부-주인");
        long farmId = createFarm(ownerToken, "브리핑권한농장");
        String strangerToken = signupAndLogin("브리핑농부-이방인");

        mockMvc.perform(get("/api/farms/" + farmId + "/briefing")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("다른 농장의 값은 섞이지 않는다 — farm 스코프 격리")
    void briefingIsFarmScoped() throws Exception {
        String token = signupAndLogin("브리핑농부-격리");
        long farmA = createFarm(token, "브리핑격리농장A");
        long farmB = createFarm(token, "브리핑격리농장B");
        saveAlarmEvent(farmA, "INDOOR_TEMP_HIGH");

        mockMvc.perform(get("/api/farms/" + farmB + "/briefing")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionRequiredCount").value(0));
    }

    private AlarmEvent saveAlarmEvent(long farmId, String metricKey) {
        return alarmEventRepository.save(AlarmEvent.builder()
                .farmId(farmId)
                .severity(AlarmSeverity.WARNING)
                .sourceType(AlarmSourceType.ENV_THRESHOLD)
                .metricKey(metricKey)
                .message(metricKey + " 이탈")
                .occurredAt(LocalDateTime.now())
                .build());
    }

    private void createDeviceWithCalibration(String token, long farmId, long zoneId, String name,
                                              LocalDateTime calibrationDueAt) throws Exception {
        mockMvc.perform(post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneId, null, null, name, DeviceKind.SENSOR, null, null, null,
                                calibrationDueAt, null,
                                List.of(SensorMetric.TEMPERATURE)))))
                .andExpect(status().isCreated());
    }
}
