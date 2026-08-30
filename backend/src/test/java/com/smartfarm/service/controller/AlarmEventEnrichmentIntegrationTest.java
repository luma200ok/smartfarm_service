package com.smartfarm.service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.AlarmRuleRequest;
import com.smartfarm.service.dto.WithdrawRequest;
import com.smartfarm.service.entity.AlarmComparator;
import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmRuleSource;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.repository.AlarmEventRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 알람 이벤트 표시용 부가 필드(scopeLabel·ruleSummary·acknowledgedByName·resolvedByName) 통합
 * 테스트(이슈 #135) — FE가 zone 트리·규칙·유저 목록을 따로 받아 직접 조합하던 것(#136)을 서버가
 * 배치 조회로 대신 조립한다. 넷 다 "없으면 null"(스코프 소멸·규칙 없음·유저 탈퇴) 원칙과, 목록 크기와
 * 무관한 쿼리 개수(N+1 부재)를 검증한다.
 */
class AlarmEventEnrichmentIntegrationTest extends FarmTestSupport {

    private static final AtomicLong COUNTER = new AtomicLong();

    @Autowired
    private AlarmEventRepository alarmEventRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    /**
     * 테스트 픽스처 — 실제 평가 경로(recordBreach)를 거치지 않고 직접 행을 만든다(다른 통합 테스트의
     * 선례와 동일). metricKey는 항상 유니크하게 둔다 — ruleId가 같아도 metricKey를 "RULE_{ruleId}"로
     * 맞추면 같은 규칙으로 이벤트를 두 개 이상 만들 때 partial unique index
     * {@code (farm_id, metric_key) WHERE status<>'RESOLVED'}에 걸린다(이 테스트의 관심사는 멱등성이
     * 아니라 enrichment이므로 그 제약을 우회한다).
     */
    private AlarmEvent saveEvent(long farmId, AlarmScopeType scopeType, Long scopeId, Long ruleId) {
        return alarmEventRepository.save(AlarmEvent.builder()
                .farmId(farmId)
                .severity(AlarmSeverity.WARNING)
                .sourceType(AlarmSourceType.SENSOR_THRESHOLD)
                .metricKey("MANUAL_" + System.nanoTime() + "_" + COUNTER.incrementAndGet())
                .message("EC 상한 초과")
                .occurredAt(LocalDateTime.now())
                .ruleId(ruleId)
                .scopeType(scopeType)
                .scopeId(scopeId)
                .build());
    }

    private long levelIdOfByNo(String token, long farmId, long zoneId, long rackId, int levelNo) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = readJson(result);
        for (JsonNode zone : json.get("zones")) {
            if (zone.get("id").asLong() != zoneId) {
                continue;
            }
            for (JsonNode rack : zone.get("racks")) {
                if (rack.get("id").asLong() != rackId) {
                    continue;
                }
                for (JsonNode level : rack.get("levels")) {
                    if (level.get("levelNo").asInt() == levelNo) {
                        return level.get("id").asLong();
                    }
                }
            }
        }
        throw new IllegalStateException("층을 찾을 수 없음: levelNo=" + levelNo);
    }

    /** SENSOR_READING·EC·GT 규칙을 지정한 스코프로 생성(ADMIN=농장 소유주 토큰). */
    private long createRule(String ownerToken, long farmId, AlarmScopeType scopeType, Long scopeId)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AlarmRuleRequest(
                                "EC 상한", true, AlarmRuleSource.SENSOR_READING, "EC", AlarmComparator.GT,
                                3.2, null, null, 300, AlarmSeverity.CRITICAL, scopeType, scopeId))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    // ── scopeLabel ────────────────────────────────────────────────

    @Test
    @DisplayName("scopeType이 null(#118 이전 레거시 이벤트)이거나 FARM이면 scopeLabel은 농장명이다")
    void scopeLabelFallsBackToFarmNameForNullOrFarmScope() throws Exception {
        String token = signupAndLogin("스코프농부-농장");
        long farmId = createFarm(token, "군산 제1식물공장");
        long legacyEventId = saveEvent(farmId, null, null, null).getId();
        long farmScopeEventId = saveEvent(farmId, AlarmScopeType.FARM, null, null).getId();

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + legacyEventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.scopeLabel").value("군산 제1식물공장"));

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + farmScopeEventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.scopeLabel").value("군산 제1식물공장"));
    }

    @Test
    @DisplayName("ZONE/RACK/LEVEL 스코프는 존·랙·층 이름을 \" · \"로 이어붙인 위치 표기가 된다")
    void scopeLabelJoinsZoneRackLevelNames() throws Exception {
        String token = signupAndLogin("스코프농부-계층");
        long farmId = createFarm(token, "계층농장");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 2);
        long levelId = levelIdOfByNo(token, farmId, zoneId, rackId, 1);

        long zoneEventId = saveEvent(farmId, AlarmScopeType.ZONE, zoneId, null).getId();
        long rackEventId = saveEvent(farmId, AlarmScopeType.RACK, rackId, null).getId();
        long levelEventId = saveEvent(farmId, AlarmScopeType.LEVEL, levelId, null).getId();

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + zoneEventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.event.scopeLabel").value("A동"));

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + rackEventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.event.scopeLabel").value("A동 · R1"));

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + levelEventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.event.scopeLabel").value("A동 · R1 · 1층"));
    }

    @Test
    @DisplayName("스코프 소멸(랙 삭제)이면 scopeLabel은 null이다 — FARM으로 승격하거나 "
            + "농장명으로 대체하지 않는다(#118 원칙)")
    void scopeLabelIsNullWhenScopeTargetIsGone() throws Exception {
        String token = signupAndLogin("스코프농부-소멸");
        long farmId = createFarm(token, "소멸농장");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 1);
        long levelId = levelIdOfByNo(token, farmId, zoneId, rackId, 1);
        long levelEventId = saveEvent(farmId, AlarmScopeType.LEVEL, levelId, null).getId();
        long rackEventId = saveEvent(farmId, AlarmScopeType.RACK, rackId, null).getId();

        mockMvc.perform(delete("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + levelEventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.event.scopeLabel").doesNotExist());

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + rackEventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.event.scopeLabel").doesNotExist());
    }

    @Test
    @DisplayName("cross-tenant 방어: 다른 농장 소속 rack/level id가 scopeId에 심겨도(정상 경로로는 "
            + "나올 수 없는 상태를 강제 재현) scopeLabel이 그 농장의 이름을 노출하지 않는다"
            + "(code-reviewer P3 — enrich()의 cross-tenant 안전성은 평상시 AlarmRule 생성 시의 "
            + "AlarmScopeResolver 검증·zone/rack/level farmId 불변에 기대는데, 그 전제가 깨졌을 때"
            + "(예: 향후 랙/존 농장 이관 기능) 조용히 남의 농장 이름이 새지 않도록 enrich() 안에 둔 "
            + "방어적 farmId 필터를 이 테스트로 고정한다)")
    void scopeLabelDoesNotLeakOtherFarmNameWhenScopeIdIsForeign() throws Exception {
        String otherOwnerToken = signupAndLogin("타농장주-크로스테넌트");
        long otherFarmId = createFarm(otherOwnerToken, "타농장");
        long otherZoneId = createZone(otherOwnerToken, otherFarmId, "타존");
        long otherRackId = createRack(otherOwnerToken, otherFarmId, otherZoneId, "타랙", 1);
        long otherLevelId = levelIdOfByNo(otherOwnerToken, otherFarmId, otherZoneId, otherRackId, 1);

        String token = signupAndLogin("내농장주-크로스테넌트");
        long farmId = createFarm(token, "내농장");
        // AlarmRule 생성 경로였다면 AlarmScopeResolver#requireExists가 이 조합(farmId가 다른
        // scopeId)을 404로 막는다 — 여기서는 그 불변식이 깨졌다고 가정하고 이벤트를 직접 fixture로
        // 만들어 강제 재현한다(다른 통합 테스트들과 동일하게 recordBreach 평가 경로를 거치지 않음).
        long rackScopeEventId = saveEvent(farmId, AlarmScopeType.RACK, otherRackId, null).getId();
        long levelScopeEventId = saveEvent(farmId, AlarmScopeType.LEVEL, otherLevelId, null).getId();

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + rackScopeEventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.event.scopeLabel").doesNotExist());

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + levelScopeEventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.event.scopeLabel").doesNotExist());
    }

    // ── ruleSummary ───────────────────────────────────────────────

    @Test
    @DisplayName("ruleSummary는 지표·비교연산자·임계값·지속시간을 규칙 필드 그대로 조합한다")
    void ruleSummaryComposesFromRuleFields() throws Exception {
        String token = signupAndLogin("규칙농부-요약");
        long farmId = createFarm(token, "규칙요약농장");
        long ruleId = createRule(token, farmId, AlarmScopeType.FARM, null);
        long eventId = saveEvent(farmId, AlarmScopeType.FARM, null, ruleId).getId();

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.ruleSummary").value("EC 상한 초과 > 3.2 · 5분 지속"));
    }

    @Test
    @DisplayName("ruleId가 없으면(수동/시스템 발생) ruleSummary는 null이다")
    void ruleSummaryIsNullWhenNoRule() throws Exception {
        String token = signupAndLogin("규칙농부-없음");
        long farmId = createFarm(token, "규칙없음농장");
        long eventId = saveEvent(farmId, AlarmScopeType.FARM, null, null).getId();

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.event.ruleSummary").doesNotExist());
    }

    @Test
    @DisplayName("규칙이 삭제되면 rule_id가 ON DELETE SET NULL로 비워지고(alarm_events FK, V20) "
            + "ruleId·ruleSummary 모두 null이 된다 — \"알 수 없음\" 같은 문구를 지어내지 않는다")
    void ruleSummaryIsNullWhenRuleDeleted() throws Exception {
        String token = signupAndLogin("규칙농부-삭제");
        long farmId = createFarm(token, "규칙삭제농장");
        long ruleId = createRule(token, farmId, AlarmScopeType.FARM, null);
        // metricKey를 규칙 키와 다르게 둬 삭제 시 autoResolveIfOpen이 이 이벤트를 건드리지 않게 한다
        // (검증 대상은 "규칙이 사라진 뒤 rule_id가 비워진 이벤트"이지, 삭제의 자동 해소 부작용이 아니다).
        long eventId = alarmEventRepository.save(AlarmEvent.builder()
                .farmId(farmId)
                .severity(AlarmSeverity.WARNING)
                .sourceType(AlarmSourceType.SENSOR_THRESHOLD)
                .metricKey("MANUAL_KEEP_OPEN_" + System.nanoTime())
                .message("EC 상한 초과")
                .occurredAt(LocalDateTime.now())
                .ruleId(ruleId)
                .scopeType(AlarmScopeType.FARM)
                .build()).getId();

        mockMvc.perform(delete("/api/farms/" + farmId + "/alarm-rules/" + ruleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.event.ruleId").doesNotExist())
                .andExpect(jsonPath("$.event.ruleSummary").doesNotExist());
    }

    // ── acknowledgedByName / resolvedByName ──────────────────────────

    @Test
    @DisplayName("확인/처리 전에는 acknowledgedByName·resolvedByName이 null이고, "
            + "처리 후에는 처리자 닉네임이 채워진다")
    void acknowledgedAndResolvedNameFilledAfterProcessing() throws Exception {
        String token = signupAndLogin("처리자농부");
        long farmId = createFarm(token, "처리자농장");
        long eventId = saveEvent(farmId, AlarmScopeType.FARM, null, null).getId();

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.event.acknowledgedByName").doesNotExist())
                .andExpect(jsonPath("$.event.resolvedByName").doesNotExist());

        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-events/" + eventId + "/acknowledge")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledgedByName").value("처리자농부"))
                .andExpect(jsonPath("$.resolvedByName").doesNotExist());

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-events/" + eventId + "/resolve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledgedByName").value("처리자농부"))
                .andExpect(jsonPath("$.resolvedByName").value("처리자농부"));
    }

    @Test
    @DisplayName("처리자가 탈퇴하면 acknowledgedByName은 null로 떨어진다(raw acknowledgedBy는 유지)")
    void acknowledgedByNameIsNullWhenUserWithdrawn() throws Exception {
        String adminToken = signupAndLogin("탈퇴농부-관리자");
        String operatorToken = signupAndLogin("탈퇴농부-처리자");
        long farmId = createFarm(adminToken, "탈퇴처리자농장");
        joinFarmAs(adminToken, farmId, operatorToken, FarmRole.OPERATOR);
        long eventId = saveEvent(farmId, AlarmScopeType.FARM, null, null).getId();

        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-events/" + eventId + "/acknowledge")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledgedByName").value("탈퇴농부-처리자"));

        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WithdrawRequest("password123"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + eventId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.event.acknowledgedBy").isNumber())
                .andExpect(jsonPath("$.event.acknowledgedByName").doesNotExist());
    }

    // ── N+1 부재 ─────────────────────────────────────────────────

    @Test
    @DisplayName("스코프·규칙·처리자가 섞여 있어도 목록 조회 쿼리 개수는 이벤트 건수와 무관하게 상수다")
    void listQueryCountStaysConstantRegardlessOfEnrichmentVariety() throws Exception {
        String token = signupAndLogin("N+1농부");
        long farmId = createFarm(token, "N+1농장");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 1);
        long levelId = levelIdOfByNo(token, farmId, zoneId, rackId, 1);
        long ruleId = createRule(token, farmId, AlarmScopeType.LEVEL, levelId);

        long e1 = saveEvent(farmId, AlarmScopeType.LEVEL, levelId, ruleId).getId();
        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-events/" + e1 + "/acknowledge")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        saveEvent(farmId, AlarmScopeType.RACK, rackId, null);
        saveEvent(farmId, AlarmScopeType.FARM, null, null);

        statistics().clear();
        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));
        long queryCountFor3Events = statistics().getPrepareStatementCount();

        saveEvent(farmId, AlarmScopeType.ZONE, zoneId, null);
        saveEvent(farmId, AlarmScopeType.LEVEL, levelId, ruleId);
        saveEvent(farmId, AlarmScopeType.FARM, null, null);

        statistics().clear();
        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events")
                        .param("size", "50")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(6));
        long queryCountFor6Events = statistics().getPrepareStatementCount();

        assertThat(queryCountFor6Events)
                .as("이벤트 3건 대비 6건일 때 실행 SQL 개수(배치 조회라 건수와 무관해야 함)")
                .isEqualTo(queryCountFor3Events);
    }
}
