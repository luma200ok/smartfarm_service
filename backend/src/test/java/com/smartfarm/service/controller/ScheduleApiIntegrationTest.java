package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.FarmRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 스케줄 API 통합 테스트(이슈 #129-C) — 데이터모델·CRUD 골격만(실행 경로 없음). cron 형식 검증
 * (SCH003)·농장당 상한(SCH002)·zoneId cross-tenant 404(R001)·RBAC(ADMIN 쓰기·멤버 조회)를 검증한다.
 */
class ScheduleApiIntegrationTest extends FarmTestSupport {

    private static final String VALID_REQUEST = """
            {"name":"야간 조명 점등","cronExpression":"0 0 20 * * *","actionType":"DEVICE_ON",
             "actionPayload":{"deviceId":1}}
            """;

    private long createSchedule(String token, long farmId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/farms/" + farmId + "/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    // ── 기본 CRUD(ADMIN) ──────────────────────────────────

    @Test
    @DisplayName("ADMIN은 스케줄을 생성·조회·수정·삭제할 수 있다 — 저장만 하고 실행 필드는 없다")
    void adminCanCrudSchedule() throws Exception {
        String token = signupAndLogin("스케줄농부-CRUD");
        long farmId = createFarm(token, "스케줄CRUD농장");

        long scheduleId = createSchedule(token, farmId, VALID_REQUEST);

        mockMvc.perform(get("/api/farms/" + farmId + "/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("야간 조명 점등"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.cronExpression").value("0 0 20 * * *"))
                .andExpect(jsonPath("$.actionType").value("DEVICE_ON"))
                .andExpect(jsonPath("$.actionPayload.deviceId").value(1))
                .andExpect(jsonPath("$.zoneId").doesNotExist());

        mockMvc.perform(get("/api/farms/" + farmId + "/schedules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(patch("/api/farms/" + farmId + "/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.name").value("야간 조명 점등")); // 미전송 필드는 그대로 유지

        mockMvc.perform(delete("/api/farms/" + farmId + "/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/farms/" + farmId + "/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCH001"));
    }

    @Test
    @DisplayName("zoneId를 지정하면 그 존 소속으로 저장되고, 지정하지 않으면 농장 전체 대상이다")
    void zoneScopedScheduleIsSaved() throws Exception {
        String token = signupAndLogin("스케줄농부-존");
        long farmId = createFarm(token, "스케줄존농장");
        long zoneId = createZone(token, farmId, "A동");

        long scheduleId = createSchedule(token, farmId, """
                {"zoneId":%d,"name":"A동 급수","cronExpression":"0 0 6 * * *","actionType":"DEVICE_ON"}
                """.formatted(zoneId));

        mockMvc.perform(get("/api/farms/" + farmId + "/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneId").value(zoneId));
    }

    // ── cron 형식 검증(SCH003) ────────────────────────────

    @Test
    @DisplayName("잘못된 cron 표현식은 400 SCH003으로 거부된다")
    void invalidCronExpressionIsRejected() throws Exception {
        String token = signupAndLogin("스케줄농부-잘못된cron");
        long farmId = createFarm(token, "스케줄cron농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"고장난 스케줄","cronExpression":"이건 cron이 아니다","actionType":"DEVICE_ON"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCH003"));
    }

    @Test
    @DisplayName("PATCH로 잘못된 cron을 보내도 400 SCH003으로 거부되고 기존 값은 유지된다")
    void invalidCronOnPatchIsRejectedAndOriginalIsUnchanged() throws Exception {
        String token = signupAndLogin("스케줄농부-패치cron");
        long farmId = createFarm(token, "스케줄패치cron농장");
        long scheduleId = createSchedule(token, farmId, VALID_REQUEST);

        mockMvc.perform(patch("/api/farms/" + farmId + "/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cronExpression\":\"not-a-cron\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCH003"));

        mockMvc.perform(get("/api/farms/" + farmId + "/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cronExpression").value("0 0 20 * * *"));
    }

    // ── zoneId cross-tenant 404(R001) ─────────────────────

    @Test
    @DisplayName("다른 농장 소속 zoneId는 404 R001로 거부된다(cross-tenant IDOR 차단)")
    void crossTenantZoneIdIsRejected() throws Exception {
        String token = signupAndLogin("스케줄농부-크로스존");
        long farmA = createFarm(token, "스케줄크로스A");
        long farmB = createFarm(token, "스케줄크로스B");
        long zoneOfFarmB = createZone(token, farmB, "B동");

        mockMvc.perform(post("/api/farms/" + farmA + "/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zoneId":%d,"name":"크로스 스케줄","cronExpression":"0 0 6 * * *","actionType":"DEVICE_ON"}
                                """.formatted(zoneOfFarmB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    @Test
    @DisplayName("존재하지 않는 zoneId는 404 R001로 거부된다")
    void nonExistentZoneIdIsRejected() throws Exception {
        String token = signupAndLogin("스케줄농부-없는존");
        long farmId = createFarm(token, "스케줄없는존농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zoneId":999999,"name":"없는 존 스케줄","cronExpression":"0 0 6 * * *",
                                 "actionType":"DEVICE_ON"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    // ── RBAC ───────────────────────────────────────────────

    @Test
    @DisplayName("VIEWER는 조회는 되지만 생성·수정·삭제는 403 F003이다")
    void viewerCanReadButNotWrite() throws Exception {
        String adminToken = signupAndLogin("스케줄농부-관리자");
        long farmId = createFarm(adminToken, "스케줄RBAC농장");
        long scheduleId = createSchedule(adminToken, farmId, VALID_REQUEST);

        String viewerToken = signupAndLogin("스케줄농부-뷰어");
        joinFarmAs(adminToken, farmId, viewerToken, FarmRole.VIEWER);

        mockMvc.perform(get("/api/farms/" + farmId + "/schedules")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/farms/" + farmId + "/schedules")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));

        mockMvc.perform(patch("/api/farms/" + farmId + "/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));

        mockMvc.perform(delete("/api/farms/" + farmId + "/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    @Test
    @DisplayName("OPERATOR도 구조 변경 성격이라 생성은 403 F003이다(ADMIN만 가능)")
    void operatorCannotCreateSchedule() throws Exception {
        String adminToken = signupAndLogin("스케줄농부-관리자2");
        long farmId = createFarm(adminToken, "스케줄오퍼레이터농장");
        String operatorToken = signupAndLogin("스케줄농부-오퍼레이터");
        joinFarmAs(adminToken, farmId, operatorToken, FarmRole.OPERATOR);

        mockMvc.perform(post("/api/farms/" + farmId + "/schedules")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    @Test
    @DisplayName("데모 계정은 스케줄을 생성·수정·삭제할 수 없다(403 A007)")
    void demoAccountCannotModifySchedule() throws Exception {
        String demoToken = demoAccountLogin();
        long demoFarmId = demoAccountFarmId(demoToken);

        mockMvc.perform(post("/api/farms/" + demoFarmId + "/schedules")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"));
    }

    // ── 농장당 상한(SCH002) — 뮤테이션 검증 ───────────────

    @Test
    @DisplayName("농장당 스케줄 상한(50건)을 넘기면 409 SCH002다(#91 리소스 생성 상한 정책)")
    void scheduleCountLimitIsEnforced() throws Exception {
        String token = signupAndLogin("스케줄농부-상한");
        long farmId = createFarm(token, "스케줄상한농장");
        for (int i = 0; i < 50; i++) {
            createSchedule(token, farmId, """
                    {"name":"스케줄%d","cronExpression":"0 0 %d * * *","actionType":"DEVICE_ON"}
                    """.formatted(i, i % 24));
        }

        mockMvc.perform(post("/api/farms/" + farmId + "/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCH002"));

        mockMvc.perform(get("/api/farms/" + farmId + "/schedules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50));
    }

    // ── 필수값 검증 ─────────────────────────────────────────

    @Test
    @DisplayName("name·cronExpression·actionType 미지정은 400 C001이다")
    void missingRequiredFieldsAreRejected() throws Exception {
        String token = signupAndLogin("스케줄농부-필수값");
        long farmId = createFarm(token, "스케줄필수값농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("PATCH는 zoneId·actionType을 바꾸지 않는다 — 요청 바디에 있어도 무시된다")
    void patchCannotChangeZoneIdOrActionType() throws Exception {
        String token = signupAndLogin("스케줄농부-불변필드");
        long farmId = createFarm(token, "스케줄불변필드농장");
        long scheduleId = createSchedule(token, farmId, VALID_REQUEST);

        // ScheduleUpdateRequest에는 zoneId/actionType 필드 자체가 없다 — 보내도 역직렬화에서 무시됨.
        mockMvc.perform(patch("/api/farms/" + farmId + "/schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneId\":999,\"actionType\":\"DEVICE_OFF\",\"name\":\"이름만 변경\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("이름만 변경"))
                .andExpect(jsonPath("$.actionType").value("DEVICE_ON"))
                .andExpect(jsonPath("$.zoneId").doesNotExist());
    }
}
