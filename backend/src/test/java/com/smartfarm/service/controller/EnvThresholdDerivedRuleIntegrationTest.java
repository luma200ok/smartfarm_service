package com.smartfarm.service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.AlarmComparator;
import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventStatus;
import com.smartfarm.service.entity.AlarmRule;
import com.smartfarm.service.entity.AlarmRuleSource;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.repository.AlarmEventRepository;
import com.smartfarm.service.repository.AlarmRuleRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * §4.6 {@code PUT /env-thresholds} ↔ {@code alarm_rules} 파생 규칙 동기화 통합 테스트(이슈 #118).
 *
 * <p>#118은 평가 엔진의 데이터 소스를 {@code farm_env_thresholds}에서 {@code alarm_rules}로 옮겼다.
 * V20이 <b>기존 설정 행을 규칙으로 이관</b>하고, 그 뒤로는 이 API가 저장될 때마다 파생 규칙을
 * 동기화한다. 이 테스트는 그 동기화가 <b>V20 이관 SQL과 같은 결과</b>(ENV_SNAPSHOT · FARM 스코프 ·
 * duration 120초 · WARNING · 하한 LT/상한 GT)를 만드는지, 그리고 <b>기존 API 응답 스펙이 그대로인지</b>
 * 를 함께 검증한다. (V20의 INSERT ... SELECT 자체는 마이그레이션이 빈 DB에서 도는 테스트 환경에서는
 * 대상 행이 없어 직접 관측할 수 없으므로, 같은 명세를 구현한 이 살아 있는 경로로 검증한다.)
 */
class EnvThresholdDerivedRuleIntegrationTest extends FarmTestSupport {

    @Autowired
    private AlarmRuleRepository alarmRuleRepository;

    @Autowired
    private AlarmEventRepository alarmEventRepository;

    private static final String ALL_BOUNDS = """
            {"enabled":true,"indoorTempMin":18.0,"indoorTempMax":28.0,
             "indoorHumidityMin":40.0,"indoorHumidityMax":80.0}
            """;

    private void putThresholds(String token, long farmId, String body) throws Exception {
        mockMvc.perform(put("/api/farms/" + farmId + "/env-thresholds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private List<AlarmRule> rulesOf(long farmId) {
        return alarmRuleRepository.findByFarmIdOrderByIdAsc(farmId);
    }

    @Test
    @DisplayName("임계치를 저장하면 대응 파생 규칙 4개가 만들어진다(V20 이관 명세와 동일: "
            + "ENV_SNAPSHOT·FARM·60초·WARNING, 하한=LT/상한=GT)")
    void savingThresholdsCreatesFourDerivedRules() throws Exception {
        String token = signupAndLogin("파생주인1");
        long farmId = createFarm(token, "파생농장1");

        putThresholds(token, farmId, ALL_BOUNDS);

        List<AlarmRule> rules = rulesOf(farmId);
        assertThat(rules).hasSize(4);
        assertThat(rules).allSatisfy(rule -> {
            assertThat(rule.getSource()).isEqualTo(AlarmRuleSource.ENV_SNAPSHOT);
            assertThat(rule.getScopeType()).isEqualTo(AlarmScopeType.FARM);
            assertThat(rule.getScopeId()).isNull();
            // 60 = 기존 "연속 2틱"의 초 환산(틱 간격 1회분) — V20 이관 DML과 같은 값
            assertThat(rule.getDurationSeconds()).isEqualTo(60);
            assertThat(rule.getSeverity()).isEqualTo(AlarmSeverity.WARNING);
            assertThat(rule.isEnabled()).isTrue();
            assertThat(rule.getThresholdId()).isNotNull();
        });
        assertThat(rules)
                .extracting(AlarmRule::getMetric, AlarmRule::getComparator, AlarmRule::getThresholdValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("INDOOR_TEMP", AlarmComparator.LT, 18.0),
                        org.assertj.core.groups.Tuple.tuple("INDOOR_TEMP", AlarmComparator.GT, 28.0),
                        org.assertj.core.groups.Tuple.tuple("INDOOR_HUMIDITY", AlarmComparator.LT, 40.0),
                        org.assertj.core.groups.Tuple.tuple("INDOOR_HUMIDITY", AlarmComparator.GT, 80.0));
    }

    @Test
    @DisplayName("기존 GET/PUT /env-thresholds 응답 스펙은 그대로다(#118 회귀 없음)")
    void envThresholdsApiContractIsUnchanged() throws Exception {
        String token = signupAndLogin("파생주인2");
        long farmId = createFarm(token, "파생농장2");

        putThresholds(token, farmId, ALL_BOUNDS);

        mockMvc.perform(get("/api/farms/" + farmId + "/env-thresholds")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.indoorTempMin").value(18.0))
                .andExpect(jsonPath("$.indoorTempMax").value(28.0))
                .andExpect(jsonPath("$.indoorHumidityMin").value(40.0))
                .andExpect(jsonPath("$.indoorHumidityMax").value(80.0))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("⚠️ 재저장은 파생 규칙을 제자리 갱신한다 — 규칙 id가 보존돼야 열려 있던 알람의 "
            + "멱등성 키(RULE_{id})가 유지되고 자동 해소가 계속 동작한다(삭제-후-재생성 금지)")
    void resavingUpdatesDerivedRulesInPlace() throws Exception {
        String token = signupAndLogin("파생주인3");
        long farmId = createFarm(token, "파생농장3");
        putThresholds(token, farmId, ALL_BOUNDS);
        List<Long> idsBefore = rulesOf(farmId).stream().map(AlarmRule::getId).sorted().toList();

        putThresholds(token, farmId, """
                {"enabled":true,"indoorTempMin":15.0,"indoorTempMax":32.0,
                 "indoorHumidityMin":40.0,"indoorHumidityMax":80.0}
                """);

        List<AlarmRule> after = rulesOf(farmId);
        assertThat(after.stream().map(AlarmRule::getId).sorted().toList()).isEqualTo(idsBefore);
        assertThat(after.stream()
                .filter(r -> r.getMetric().equals("INDOOR_TEMP") && r.getComparator() == AlarmComparator.GT)
                .findFirst().orElseThrow().getThresholdValue()).isEqualTo(32.0);
    }

    @Test
    @DisplayName("경계값을 비우면 그 방향 규칙만 비활성화된다(행은 남는다 — id 보존이 목적)")
    void clearingABoundDisablesOnlyThatRule() throws Exception {
        String token = signupAndLogin("파생주인4");
        long farmId = createFarm(token, "파생농장4");
        putThresholds(token, farmId, ALL_BOUNDS);

        putThresholds(token, farmId, """
                {"enabled":true,"indoorTempMin":18.0,"indoorTempMax":28.0}
                """);

        List<AlarmRule> rules = rulesOf(farmId);
        assertThat(rules).hasSize(4);
        assertThat(rules).filteredOn(r -> r.getMetric().equals("INDOOR_TEMP"))
                .allMatch(AlarmRule::isEnabled);
        assertThat(rules).filteredOn(r -> r.getMetric().equals("INDOOR_HUMIDITY"))
                .noneMatch(AlarmRule::isEnabled);
    }

    @Test
    @DisplayName("⚠️ enabled=false로 저장하면 파생 규칙이 전부 비활성화되고, 그 규칙으로 열려 있던 "
            + "알람은 즉시 자동 해소된다(비활성 규칙은 평가되지 않아 자동 해소가 영영 돌지 않는다)")
    void disablingThresholdsAutoResolvesOpenAlarms() throws Exception {
        String token = signupAndLogin("파생주인5");
        long farmId = createFarm(token, "파생농장5");
        putThresholds(token, farmId, ALL_BOUNDS);

        AlarmRule tempMax = rulesOf(farmId).stream()
                .filter(r -> r.getMetric().equals("INDOOR_TEMP") && r.getComparator() == AlarmComparator.GT)
                .findFirst().orElseThrow();
        alarmEventRepository.save(AlarmEvent.builder()
                .farmId(farmId)
                .severity(AlarmSeverity.WARNING)
                .sourceType(AlarmSourceType.ENV_THRESHOLD)
                .metricKey(tempMax.metricKey())
                .message("실내 온도 상한 초과")
                .occurredAt(LocalDateTime.now())
                .ruleId(tempMax.getId())
                .scopeType(AlarmScopeType.FARM)
                .build());

        putThresholds(token, farmId, """
                {"enabled":false,"indoorTempMin":18.0,"indoorTempMax":28.0,
                 "indoorHumidityMin":40.0,"indoorHumidityMax":80.0}
                """);

        assertThat(rulesOf(farmId)).noneMatch(AlarmRule::isEnabled);
        AlarmEvent event = alarmEventRepository.findByFarmIdAndOccurredAtAfter(farmId,
                        LocalDateTime.now().minusDays(1)).stream()
                .max(Comparator.comparing(AlarmEvent::getId)).orElseThrow();
        assertThat(event.getStatus()).isEqualTo(AlarmEventStatus.RESOLVED);
        assertThat(event.getResolvedBy()).isNull(); // 시스템 자동 해소
    }

    @Test
    @DisplayName("파생 규칙은 alarm-rules 목록에 derived=true로 보이지만 PATCH·DELETE는 409 ALR004다"
            + "(두 API가 같은 행을 서로 다른 진실로 덮어쓰는 것을 막는다)")
    void derivedRulesAreReadOnlyThroughAlarmRuleApi() throws Exception {
        String token = signupAndLogin("파생주인6");
        long farmId = createFarm(token, "파생농장6");
        putThresholds(token, farmId, ALL_BOUNDS);
        long derivedId = rulesOf(farmId).get(0).getId();

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].derived").value(true));

        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-rules/" + derivedId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thresholdValue\":99.0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALR004"));

        mockMvc.perform(delete("/api/farms/" + farmId + "/alarm-rules/" + derivedId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALR004"));
    }
}
