package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.FarmLogRequest;
import com.smartfarm.service.entity.FarmLogType;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class FarmLogApiIntegrationTest extends FarmTestSupport {

    // ── 생성 ──────────────────────────────────────────────

    @Test
    @DisplayName("정상 작성 시 201, 작성자·필드가 응답에 실린다")
    void createLogSuccess() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "일지 농장");
        long userId = myUserId(token);

        mockMvc.perform(post("/api/farms/" + farmId + "/logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmLogRequest(LocalDate.of(2026, 8, 20), FarmLogType.WATERING, "오전 관수"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.logDate").value("2026-08-20"))
                .andExpect(jsonPath("$.type").value("WATERING"))
                .andExpect(jsonPath("$.memo").value("오전 관수"))
                .andExpect(jsonPath("$.createdBy").value(userId))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("memo 없이도(선택 필드) 201로 생성된다")
    void createLogWithoutMemo() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "메모없음 농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmLogRequest(LocalDate.of(2026, 8, 20), FarmLogType.HARVEST, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memo").doesNotExist());
    }

    @Test
    @DisplayName("logDate·type 누락 시 400 C001을 반환한다")
    void createLogValidationFailure() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "검증 농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 일지 작성 시 403 F002를 반환한다")
    void createLogAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/logs")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmLogRequest(LocalDate.of(2026, 8, 20), FarmLogType.ETC, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("데모 계정도 일지 작성이 201로 성공한다(체험 핵심 — DemoAccountGuard 미적용)")
    void createLogAsDemoAccountAllowed() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/demo-login"))
                .andExpect(status().isOk())
                .andReturn();
        String demoToken = readJson(loginResult).get("accessToken").asText();
        MvcResult farmsResult = mockMvc.perform(get("/api/farms")
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isOk())
                .andReturn();
        long demoFarmId = readJson(farmsResult).get(0).get("id").asLong();

        mockMvc.perform(post("/api/farms/" + demoFarmId + "/logs")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmLogRequest(LocalDate.of(2026, 8, 20), FarmLogType.PRUNING, "데모 전정"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PRUNING"));
    }

    // ── 목록 조회(페이지네이션·정렬) ────────────────────────────

    @Test
    @DisplayName("목록은 logDate 내림차순이며 PageResponse 필드가 정확하다")
    void findLogsOrderedByLogDateDesc() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "정렬 농장");
        long oldLog = createLog(token, farmId, LocalDate.of(2026, 8, 1), FarmLogType.WATERING);
        long midLog = createLog(token, farmId, LocalDate.of(2026, 8, 15), FarmLogType.FERTILIZING);
        long newLog = createLog(token, farmId, LocalDate.of(2026, 8, 20), FarmLogType.HARVEST);

        mockMvc.perform(get("/api/farms/" + farmId + "/logs")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(newLog))
                .andExpect(jsonPath("$.content[1].id").value(midLog))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/farms/" + farmId + "/logs")
                        .param("page", "1")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(oldLog));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 일지 목록 조회 시 403 F002를 반환한다")
    void findLogsAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 농장 둘");

        mockMvc.perform(get("/api/farms/" + farmId + "/logs")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    // ── 수정(작성자 본인만) ────────────────────────────────────

    @Test
    @DisplayName("작성자 본인은 200으로 수정할 수 있다")
    void updateLogByAuthorSuccess() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "수정 농장");
        long logId = createLog(token, farmId, LocalDate.of(2026, 8, 10), FarmLogType.WATERING);

        mockMvc.perform(patch("/api/farms/" + farmId + "/logs/" + logId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmLogRequest(LocalDate.of(2026, 8, 11), FarmLogType.PEST_CONTROL, "수정된 메모"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logDate").value("2026-08-11"))
                .andExpect(jsonPath("$.type").value("PEST_CONTROL"))
                .andExpect(jsonPath("$.memo").value("수정된 메모"));
    }

    @Test
    @DisplayName("작성자가 아닌 멤버는(OWNER 포함되지 않은 일반 멤버) 403 L002를 반환한다")
    void updateLogByNonAuthorMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("주인장-수정");
        String memberToken = signupAndLogin("일꾼이-수정");
        long farmId = createFarm(ownerToken, "권한 농장");
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));
        long logId = createLog(memberToken, farmId, LocalDate.of(2026, 8, 10), FarmLogType.WATERING);

        // OWNER조차 작성자가 아니면 수정 불가(PATCH는 "작성자 본인만" — DELETE와 다른 규칙)
        mockMvc.perform(patch("/api/farms/" + farmId + "/logs/" + logId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmLogRequest(LocalDate.of(2026, 8, 11), FarmLogType.ETC, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("L002"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 수정 시 403 F002를 반환한다(L002보다 먼저 멤버십 검사)")
    void updateLogAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("주인장-수정2");
        String otherToken = signupAndLogin("남남남-수정2");
        long farmId = createFarm(ownerToken, "격리 수정 농장");
        long logId = createLog(ownerToken, farmId, LocalDate.of(2026, 8, 10), FarmLogType.WATERING);

        mockMvc.perform(patch("/api/farms/" + farmId + "/logs/" + logId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmLogRequest(LocalDate.of(2026, 8, 11), FarmLogType.ETC, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("존재하지 않는 logId 수정 시 404 L001을 반환한다")
    void updateLogNotFound() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "미존재 농장");

        mockMvc.perform(patch("/api/farms/" + farmId + "/logs/999999999")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmLogRequest(LocalDate.of(2026, 8, 11), FarmLogType.ETC, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("L001"));
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 logId로 수정 시 404 L001을 반환한다(농장 스코프)")
    void updateLogCrossTenantLogId() throws Exception {
        String ownerAToken = signupAndLogin("농장주A-일지");
        String ownerBToken = signupAndLogin("농장주B-일지");
        long farmA = createFarm(ownerAToken, "일지 농장 A");
        long farmB = createFarm(ownerBToken, "일지 농장 B");
        long logId = createLog(ownerAToken, farmA, LocalDate.of(2026, 8, 10), FarmLogType.WATERING);

        mockMvc.perform(patch("/api/farms/" + farmB + "/logs/" + logId)
                        .header("Authorization", "Bearer " + ownerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmLogRequest(LocalDate.of(2026, 8, 11), FarmLogType.ETC, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("L001"));
    }

    // ── 삭제(작성자 본인 또는 OWNER) ─────────────────────────────

    @Test
    @DisplayName("작성자 본인은 204로 삭제할 수 있다")
    void deleteLogByAuthorSuccess() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "삭제 농장");
        long logId = createLog(token, farmId, LocalDate.of(2026, 8, 10), FarmLogType.WATERING);

        mockMvc.perform(delete("/api/farms/" + farmId + "/logs/" + logId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/farms/" + farmId + "/logs/" + logId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmLogRequest(LocalDate.of(2026, 8, 11), FarmLogType.ETC, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("L001"));
    }

    @Test
    @DisplayName("작성자가 아니어도 OWNER는 204로 삭제할 수 있다")
    void deleteLogByOwnerNotAuthorSuccess() throws Exception {
        String ownerToken = signupAndLogin("주인장-삭제");
        String memberToken = signupAndLogin("일꾼이-삭제");
        long farmId = createFarm(ownerToken, "OWNER삭제 농장");
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));
        long logId = createLog(memberToken, farmId, LocalDate.of(2026, 8, 10), FarmLogType.WATERING);

        mockMvc.perform(delete("/api/farms/" + farmId + "/logs/" + logId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("작성자도 OWNER도 아닌 멤버는 403 L002를 반환한다")
    void deleteLogByNonAuthorNonOwnerForbidden() throws Exception {
        String ownerToken = signupAndLogin("주인장-삭제2");
        String authorToken = signupAndLogin("작성자-삭제2");
        String bystanderToken = signupAndLogin("구경꾼-삭제2");
        long farmId = createFarm(ownerToken, "제3자 농장");
        acceptInvitation(authorToken, createInvitationCode(ownerToken, farmId));
        acceptInvitation(bystanderToken, createInvitationCode(ownerToken, farmId));
        long logId = createLog(authorToken, farmId, LocalDate.of(2026, 8, 10), FarmLogType.WATERING);

        mockMvc.perform(delete("/api/farms/" + farmId + "/logs/" + logId)
                        .header("Authorization", "Bearer " + bystanderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("L002"));
    }

    @Test
    @DisplayName("존재하지 않는 logId 삭제 시 404 L001을 반환한다")
    void deleteLogNotFound() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "미존재 삭제 농장");

        mockMvc.perform(delete("/api/farms/" + farmId + "/logs/999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("L001"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 삭제 시 403 F002를 반환한다")
    void deleteLogAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("주인장-삭제3");
        String otherToken = signupAndLogin("남남남-삭제3");
        long farmId = createFarm(ownerToken, "격리 삭제 농장");
        long logId = createLog(ownerToken, farmId, LocalDate.of(2026, 8, 10), FarmLogType.WATERING);

        mockMvc.perform(delete("/api/farms/" + farmId + "/logs/" + logId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    private long createLog(String token, long farmId, LocalDate logDate, FarmLogType type) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/farms/" + farmId + "/logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FarmLogRequest(logDate, type, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }
}
