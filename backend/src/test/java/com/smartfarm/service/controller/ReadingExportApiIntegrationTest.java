package com.smartfarm.service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.entity.SensorReading;
import com.smartfarm.service.entity.SensorSource;
import com.smartfarm.service.repository.SensorReadingRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code GET /readings/export.csv} 통합 테스트(이슈 #126) — series와 동일 파라미터·다운샘플을
 * CSV로 내려주는지, 헤더·인코딩(BOM)·Content-Disposition·farm 스코프 격리를 검증한다.
 */
class ReadingExportApiIntegrationTest extends FarmTestSupport {

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private void save(long farmId, long deviceId, Long zoneId, Long rackId, Long rackLevelId,
                       SensorMetric metric, double value, LocalDateTime measuredAt) {
        sensorReadingRepository.save(SensorReading.builder()
                .farmId(farmId).deviceId(deviceId).zoneId(zoneId).rackId(rackId).rackLevelId(rackLevelId)
                .metric(metric).value(value).measuredAt(measuredAt)
                .source(SensorSource.SIMULATED)
                .build());
    }

    @Test
    @DisplayName("정상 내보내기 — text/csv, BOM 포함, 헤더+데이터 행, Content-Disposition attachment")
    void exportCsvHappyPath() throws Exception {
        String token = signupAndLogin("내보내기농부1");
        long farmId = createFarm(token, "내보내기농장1");
        long zoneId = createZone(token, farmId, "A동");
        long deviceId = createDevice(token, farmId, zoneId, null, null, "센서1");
        save(farmId, deviceId, zoneId, null, null, SensorMetric.TEMPERATURE, 22.5,
                LocalDateTime.now().minusHours(1).withNano(0));

        MvcResult result = mockMvc.perform(get("/api/farms/" + farmId + "/readings/export.csv")
                        .param("metrics", "TEMPERATURE")
                        .param("range", "24h")
                        .param("scope", "farm")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", Matchers.startsWith("text/csv")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).startsWith(UTF8_BOM);
        String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        String[] lines = text.split("\r\n");
        assertThat(lines[0]).isEqualTo("measuredAt,metric,unit,value");
        assertThat(lines[1]).contains("TEMPERATURE").contains("°C").contains("22.5");

        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(disposition).contains("attachment").contains(".csv");
        // 파일명에 사용자 입력(농장명 등)을 넣지 않는다 — farmId·range·scope 토큰만으로 구성.
        assertThat(disposition).contains("farm" + farmId).contains("24h");
    }

    @Test
    @DisplayName("VIEWER도 내보내기가 가능하다(§2 VIEWER=조회전용 — series와 동일 데이터라 requireMember)")
    void viewerCanExportCsv() throws Exception {
        String adminToken = signupAndLogin("내보내기관리자1");
        long farmId = createFarm(adminToken, "내보내기농장2");
        String viewerToken = signupAndLogin("내보내기뷰어1");
        joinFarmAs(adminToken, farmId, viewerToken, FarmRole.VIEWER);

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/export.csv")
                        .param("metrics", "TEMPERATURE")
                        .param("range", "24h")
                        .param("scope", "farm")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("미멤버는 403 F002 — series와 동일한 requireMember 경로를 그대로 탄다")
    void nonMemberCannotExport() throws Exception {
        String ownerToken = signupAndLogin("내보내기농부2");
        String otherToken = signupAndLogin("내보내기농부2-other");
        long farmId = createFarm(ownerToken, "내보내기농장3");

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/export.csv")
                        .param("metrics", "TEMPERATURE")
                        .param("scope", "farm")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("scope=zone:{타 농장 zoneId}는 404 R001이다(cross-tenant 소속 검증 — series와 동일 로직 재사용, "
            + "타 농장 데이터가 한 행도 섞이지 않는다)")
    void crossTenantZoneScopeReturnsR001() throws Exception {
        String ownerToken = signupAndLogin("내보내기농부3");
        long farmId = createFarm(ownerToken, "내보내기농장4");
        String otherOwnerToken = signupAndLogin("내보내기농부3-other");
        long otherFarmId = createFarm(otherOwnerToken, "내보내기농장4-타농장");
        long otherZoneId = createZone(otherOwnerToken, otherFarmId, "타농장 A동");

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/export.csv")
                        .param("metrics", "TEMPERATURE")
                        .param("scope", "zone:" + otherZoneId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    @Test
    @DisplayName("farm 스코프 내보내기는 자기 농장 데이터만 담고 타 농장 값은 섞이지 않는다")
    void exportOnlyContainsOwnFarmData() throws Exception {
        String tokenA = signupAndLogin("내보내기농부4A");
        long farmA = createFarm(tokenA, "내보내기농장5A");
        long zoneA = createZone(tokenA, farmA, "A동");
        long deviceA = createDevice(tokenA, farmA, zoneA, null, null, "센서A");
        save(farmA, deviceA, zoneA, null, null, SensorMetric.TEMPERATURE, 11.0,
                LocalDateTime.now().minusHours(1).withNano(0));

        String tokenB = signupAndLogin("내보내기농부4B");
        long farmB = createFarm(tokenB, "내보내기농장5B");
        long zoneB = createZone(tokenB, farmB, "B동");
        long deviceB = createDevice(tokenB, farmB, zoneB, null, null, "센서B");
        save(farmB, deviceB, zoneB, null, null, SensorMetric.TEMPERATURE, 999.0,
                LocalDateTime.now().minusHours(1).withNano(0));

        MvcResult result = mockMvc.perform(get("/api/farms/" + farmA + "/readings/export.csv")
                        .param("metrics", "TEMPERATURE")
                        .param("range", "24h")
                        .param("scope", "farm")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        String text = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text).contains("11.0").doesNotContain("999.0");
    }

    @Test
    @DisplayName("metrics 4개 초과는 series와 동일하게 400 C001이다")
    void tooManyMetricsReturnsC001() throws Exception {
        String token = signupAndLogin("내보내기농부5");
        long farmId = createFarm(token, "내보내기농장6");

        mockMvc.perform(get("/api/farms/" + farmId + "/readings/export.csv")
                        .param("metrics", "TEMPERATURE,HUMIDITY,CO2,EC,PH")
                        .param("scope", "farm")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }
}
