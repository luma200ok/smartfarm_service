package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code /api/farms/{farmId}/alarm-rules} 통합 테스트(이슈 #118) — CRUD·권한·데모 차단·상한·
 * 필드 정합성(ALR003)·<b>cross-tenant 스코프 격리(§4.10 미소속 404)</b>를 검증한다.
 */
class AlarmRuleApiIntegrationTest extends FarmTestSupport {

    private static final String SENSOR_EC_RULE = """
            {"name":"급액 EC 경보","source":"SENSOR_READING","metric":"EC","comparator":"GT",
             "thresholdValue":2.8,"durationSeconds":300,"severity":"CRITICAL","scopeType":"FARM"}
            """;

    private MvcResult createRule(String token, long farmId, String body) throws Exception {
        return mockMvc.perform(post("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }

    // ── CRUD ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OWNER는 센서 지표 규칙을 만들고 목록·단건 조회로 확인할 수 있다(프리뷰 '급액 EC > 2.8 · 5분')")
    void ownerCanCreateAndReadSensorRule() throws Exception {
        String token = signupAndLogin("규칙주인1");
        long farmId = createFarm(token, "규칙농장1");

        long ruleId = readJson(createRule(token, farmId, SENSOR_EC_RULE)).get("id").asLong();

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].metric").value("EC"))
                .andExpect(jsonPath("$[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$[0].durationSeconds").value(300))
                .andExpect(jsonPath("$[0].derived").value(false));

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-rules/" + ruleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("PATCH로 임계값·등급·활성 여부를 부분 수정할 수 있다")
    void ownerCanPatchRule() throws Exception {
        String token = signupAndLogin("규칙주인2");
        long farmId = createFarm(token, "규칙농장2");
        long ruleId = readJson(createRule(token, farmId, SENSOR_EC_RULE)).get("id").asLong();

        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-rules/" + ruleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"thresholdValue":3.2,"severity":"WARNING","enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thresholdValue").value(3.2))
                .andExpect(jsonPath("$.severity").value("WARNING"))
                .andExpect(jsonPath("$.enabled").value(false))
                // 미전송 필드는 그대로여야 한다(부분 수정).
                .andExpect(jsonPath("$.durationSeconds").value(300));
    }

    @Test
    @DisplayName("DELETE 후에는 단건 조회가 404 ALR001이다")
    void deletedRuleIsGone() throws Exception {
        String token = signupAndLogin("규칙주인3");
        long farmId = createFarm(token, "규칙농장3");
        long ruleId = readJson(createRule(token, farmId, SENSOR_EC_RULE)).get("id").asLong();

        mockMvc.perform(delete("/api/farms/" + farmId + "/alarm-rules/" + ruleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-rules/" + ruleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALR001"));
    }

    // ── 권한 ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("일반 멤버는 조회만 가능하고 생성은 403 F003이다(쓰기는 OWNER 전용 — env-thresholds와 일관)")
    void memberCanReadButNotWrite() throws Exception {
        String ownerToken = signupAndLogin("규칙주인4");
        long farmId = createFarm(ownerToken, "규칙농장4");
        String memberToken = signupAndLogin("규칙멤버4");
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SENSOR_EC_RULE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    @Test
    @DisplayName("데모 계정은 규칙 생성이 403 A007로 차단된다(파괴적 작업 차단 목록)")
    void demoAccountCannotCreateRule() throws Exception {
        String demoToken = demoAccountLogin();
        long demoFarmId = demoAccountFarmId(demoToken);

        mockMvc.perform(post("/api/farms/" + demoFarmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SENSOR_EC_RULE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"));
    }

    @Test
    @DisplayName("다른 농장의 규칙 id로 접근하면 404 ALR001이다(cross-tenant IDOR — 존재를 유추당하지 않는다)")
    void crossTenantRuleIdIsNotFound() throws Exception {
        String ownerA = signupAndLogin("규칙주인A");
        long farmA = createFarm(ownerA, "A농장");
        long ruleInA = readJson(createRule(ownerA, farmA, SENSOR_EC_RULE)).get("id").asLong();

        String ownerB = signupAndLogin("규칙주인B");
        long farmB = createFarm(ownerB, "B농장");

        mockMvc.perform(get("/api/farms/" + farmB + "/alarm-rules/" + ruleInA)
                        .header("Authorization", "Bearer " + ownerB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALR001"));
    }

    // ── 스코프 격리(§4.10) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("자기 농장의 랙을 스코프로 지정하면 생성된다(랙 단위 스코프 — 프리뷰 'B3랙')")
    void rackScopedRuleCanBeCreated() throws Exception {
        String token = signupAndLogin("스코프주인1");
        long farmId = createFarm(token, "스코프농장1");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "B3", 5);

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"B3랙 EC 경보","source":"SENSOR_READING","metric":"EC",
                                 "comparator":"GT","thresholdValue":2.8,"durationSeconds":300,
                                 "severity":"CRITICAL","scopeType":"RACK","scopeId":%d}
                                """.formatted(rackId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scopeType").value("RACK"))
                .andExpect(jsonPath("$.scopeId").value(rackId));
    }

    @Test
    @DisplayName("타 농장의 랙을 scopeId로 지정하면 404 R002다(cross-tenant IDOR 차단)")
    void otherFarmRackAsScopeIsNotFound() throws Exception {
        String ownerA = signupAndLogin("스코프주인A");
        long farmA = createFarm(ownerA, "스코프A농장");
        long zoneA = createZone(ownerA, farmA, "A동");
        long rackInA = createRack(ownerA, farmA, zoneA, "B3", 5);

        String ownerB = signupAndLogin("스코프주인B");
        long farmB = createFarm(ownerB, "스코프B농장");

        mockMvc.perform(post("/api/farms/" + farmB + "/alarm-rules")
                        .header("Authorization", "Bearer " + ownerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"남의 랙 감시","source":"SENSOR_READING","metric":"EC",
                                 "comparator":"GT","thresholdValue":2.8,"durationSeconds":300,
                                 "severity":"WARNING","scopeType":"RACK","scopeId":%d}
                                """.formatted(rackInA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R002"));
    }

    @Test
    @DisplayName("타 농장의 존을 scopeId로 지정하면 404 R001, 타 농장의 층은 404 R003이다")
    void otherFarmZoneAndLevelAsScopeAreNotFound() throws Exception {
        String ownerA = signupAndLogin("스코프주인C");
        long farmA = createFarm(ownerA, "스코프C농장");
        long zoneInA = createZone(ownerA, farmA, "A동");
        long rackInA = createRack(ownerA, farmA, zoneInA, "B4", 3);
        MvcResult tree = mockMvc.perform(get("/api/farms/" + farmA + "/zones")
                        .header("Authorization", "Bearer " + ownerA))
                .andExpect(status().isOk())
                .andReturn();
        long levelInA = readJson(tree).get("zones").get(0).get("racks").get(0).get("levels").get(0)
                .get("id").asLong();

        String ownerB = signupAndLogin("스코프주인D");
        long farmB = createFarm(ownerB, "스코프D농장");

        mockMvc.perform(post("/api/farms/" + farmB + "/alarm-rules")
                        .header("Authorization", "Bearer " + ownerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scopedRuleBody("ZONE", zoneInA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));

        mockMvc.perform(post("/api/farms/" + farmB + "/alarm-rules")
                        .header("Authorization", "Bearer " + ownerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scopedRuleBody("LEVEL", levelInA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R003"));

        // 랙 id는 위 두 케이스와 같은 픽스처에서 재사용 — 세 계층 전부 막히는지 확인.
        mockMvc.perform(post("/api/farms/" + farmB + "/alarm-rules")
                        .header("Authorization", "Bearer " + ownerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scopedRuleBody("RACK", rackInA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R002"));
    }

    private String scopedRuleBody(String scopeType, long scopeId) {
        return """
                {"name":"남의 구조 감시","source":"SENSOR_READING","metric":"EC","comparator":"GT",
                 "thresholdValue":2.8,"durationSeconds":300,"severity":"WARNING",
                 "scopeType":"%s","scopeId":%d}
                """.formatted(scopeType, scopeId);
    }

    // ── 필드 정합성(ALR003) ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GT인데 thresholdValue가 없으면 400 ALR003이다")
    void missingThresholdValueIsRejected() throws Exception {
        String token = signupAndLogin("정합성주인1");
        long farmId = createFarm(token, "정합성농장1");

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"값 없는 규칙","source":"SENSOR_READING","metric":"EC",
                                 "comparator":"GT","durationSeconds":300,"severity":"WARNING",
                                 "scopeType":"FARM"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALR003"));
    }

    @Test
    @DisplayName("OUTSIDE_RANGE의 min >= max면 400 ALR003이다")
    void invertedRangeIsRejected() throws Exception {
        String token = signupAndLogin("정합성주인2");
        long farmId = createFarm(token, "정합성농장2");

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"뒤집힌 범위","source":"SENSOR_READING","metric":"PH",
                                 "comparator":"OUTSIDE_RANGE","thresholdMin":7.0,"thresholdMax":5.5,
                                 "durationSeconds":300,"severity":"WARNING","scopeType":"FARM"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALR003"));
    }

    @Test
    @DisplayName("환경 스냅샷 규칙에 하위 스코프를 지정하면 400 ALR003이다"
            + "(env_snapshots는 farmId조차 없는 전역 단일 스트림이라 하위 스코프가 성립하지 않는다)")
    void envSnapshotRuleWithSubScopeIsRejected() throws Exception {
        String token = signupAndLogin("정합성주인3");
        long farmId = createFarm(token, "정합성농장3");
        long zoneId = createZone(token, farmId, "A동");

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"존 단위 실내온도","source":"ENV_SNAPSHOT","metric":"INDOOR_TEMP",
                                 "comparator":"GT","thresholdValue":30.0,"durationSeconds":120,
                                 "severity":"WARNING","scopeType":"ZONE","scopeId":%d}
                                """.formatted(zoneId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALR003"));
    }

    @Test
    @DisplayName("소스가 지원하지 않는 지표(ENV_SNAPSHOT + EC)는 400 ALR003이다")
    void unsupportedMetricForSourceIsRejected() throws Exception {
        String token = signupAndLogin("정합성주인4");
        long farmId = createFarm(token, "정합성농장4");

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"잘못된 지표","source":"ENV_SNAPSHOT","metric":"EC",
                                 "comparator":"GT","thresholdValue":2.8,"durationSeconds":120,
                                 "severity":"WARNING","scopeType":"FARM"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALR003"));
    }

    @Test
    @DisplayName("장비 통신 두절 규칙은 지표 없이 ABSENT로만 만들 수 있다(지표를 함께 주면 400 ALR003)")
    void heartbeatRuleRules() throws Exception {
        String token = signupAndLogin("정합성주인5");
        long farmId = createFarm(token, "정합성농장5");

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"게이트웨이 무응답","source":"DEVICE_HEARTBEAT","comparator":"ABSENT",
                                 "durationSeconds":180,"severity":"CRITICAL","scopeType":"FARM"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comparator").value("ABSENT"))
                .andExpect(jsonPath("$.metric").doesNotExist());

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"지표 붙은 무응답","source":"DEVICE_HEARTBEAT","metric":"EC",
                                 "comparator":"ABSENT","durationSeconds":180,"severity":"CRITICAL",
                                 "scopeType":"FARM"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALR003"));
    }

    @Test
    @DisplayName("보안 P3-1: PATCH로 이름을 공백으로 만들 수 없다(400 ALR003) — 규칙 이름은 알람 "
            + "메시지·웹훅 본문 앞머리에 그대로 실린다. 단 이름 미전송 부분 수정은 그대로 허용된다")
    void blankNameIsRejectedButOmittedNameIsFine() throws Exception {
        String token = signupAndLogin("이름주인");
        long farmId = createFarm(token, "이름농장");
        long ruleId = readJson(createRule(token, farmId, SENSOR_EC_RULE)).get("id").asLong();

        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-rules/" + ruleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALR003"));

        // 이름을 아예 보내지 않는 부분 수정은 정상이어야 한다(@NotBlank를 DTO에 달면 이것도 400이 된다).
        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-rules/" + ruleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thresholdValue\":3.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("급액 EC 경보"));
    }

    @Test
    @DisplayName("규칙 이름은 앞뒤 공백을 제거하고 저장한다")
    void ruleNameIsTrimmed() throws Exception {
        String token = signupAndLogin("트림주인");
        long farmId = createFarm(token, "트림농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  급액 EC 경보  ","source":"SENSOR_READING","metric":"EC",
                                 "comparator":"GT","thresholdValue":2.8,"durationSeconds":300,
                                 "severity":"CRITICAL","scopeType":"FARM"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("급액 EC 경보"));
    }

    @Test
    @DisplayName("데모 계정은 규칙 수정(PATCH)·삭제(DELETE)도 403 A007로 차단된다")
    void demoAccountCannotModifyOrDeleteRule() throws Exception {
        String demoToken = demoAccountLogin();
        long demoFarmId = demoAccountFarmId(demoToken);

        mockMvc.perform(patch("/api/farms/" + demoFarmId + "/alarm-rules/1")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thresholdValue\":3.0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"));

        mockMvc.perform(delete("/api/farms/" + demoFarmId + "/alarm-rules/1")
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"));
    }

    @Test
    @DisplayName("농장당 규칙 상한(50건)을 넘기면 409 ALR002다(#91 리소스 생성 상한 정책)")
    void ruleCountLimitIsEnforced() throws Exception {
        String token = signupAndLogin("상한주인");
        long farmId = createFarm(token, "상한농장");
        for (int i = 0; i < 50; i++) {
            createRule(token, farmId, """
                    {"name":"규칙%d","source":"SENSOR_READING","metric":"EC","comparator":"GT",
                     "thresholdValue":2.8,"durationSeconds":300,"severity":"WARNING","scopeType":"FARM"}
                    """.formatted(i));
        }

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SENSOR_EC_RULE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALR002"));
    }

    @Test
    @DisplayName("PATCH도 병합된 최종 상태로 검증한다 — min만 max보다 크게 바꾸면 400 ALR003")
    void patchValidatesMergedState() throws Exception {
        String token = signupAndLogin("정합성주인6");
        long farmId = createFarm(token, "정합성농장6");
        long ruleId = readJson(createRule(token, farmId, """
                {"name":"pH 범위","source":"SENSOR_READING","metric":"PH","comparator":"OUTSIDE_RANGE",
                 "thresholdMin":5.5,"thresholdMax":6.5,"durationSeconds":300,"severity":"WARNING",
                 "scopeType":"FARM"}
                """)).get("id").asLong();

        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-rules/" + ruleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thresholdMin\":9.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALR003"));
    }
}
