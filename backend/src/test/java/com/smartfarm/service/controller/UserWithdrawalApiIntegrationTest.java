package com.smartfarm.service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.AcceptInvitationRequest;
import com.smartfarm.service.dto.LoginRequest;
import com.smartfarm.service.dto.RefreshRequest;
import com.smartfarm.service.dto.SignupRequest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 회원 탈퇴(DELETE /api/users/me) 통합 테스트 — contract §3 탈퇴 절·§5 A006.
 * 전부 공개 API 경유(FarmTestSupport 패턴), Testcontainers PostgreSQL.
 */
class UserWithdrawalApiIntegrationTest extends FarmTestSupport {

    /** access·refresh 둘 다 필요한 시나리오용 — 이메일 지정 가입+로그인. */
    private JsonNode signupAndLoginTokens(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(email, "password123", nickname))))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    @DisplayName("OWNER 농장 보유 시 409 A006, 농장 삭제 후 탈퇴는 204")
    void withdrawBlockedWhileOwningFarmThenAllowedAfterFarmDeletion() throws Exception {
        String owner = signupAndLogin("탈퇴오너");
        long farmId = createFarm(owner, "탈퇴검증농장");

        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("A006"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(delete("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());

        // farm soft delete는 farm_members를 지우지 않음 — 잔존 OWNER 멤버십이 탈퇴를 막지 않아야 한다
        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("탈퇴 후 기존 refresh 토큰은 A004, 기존 access 토큰의 me 조회도 A004")
    void withdrawInvalidatesAllTokens() throws Exception {
        JsonNode tokens = signupAndLoginTokens(uniqueEmail("withdraw-tokens"), "탈퇴토큰유저");
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();

        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // refresh — revokeAllByUserId + soft delete 유저 차단(#10) 이중 차단
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A004"));

        // access는 만료 전까지 서명은 유효하지만(수용된 트레이드오프) 유저 조회는 즉시 A004
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A004"));
    }

    @Test
    @DisplayName("다수 농장 MEMBER가 탈퇴하면 전 농장 멤버십 삭제·접근 F002·목록 제외·구 초대코드 F004")
    void withdrawRemovesAllMembershipsAndRevokesInvitations() throws Exception {
        String ownerA = signupAndLogin("오너A");
        long farmA = createFarm(ownerA, "농장A");
        String codeA = createInvitationCode(ownerA, farmA);
        String ownerB = signupAndLogin("오너B");
        long farmB = createFarm(ownerB, "농장B");
        String codeB = createInvitationCode(ownerB, farmB);

        String member = signupAndLogin("다중멤버");
        acceptInvitation(member, codeA);
        acceptInvitation(member, codeB);

        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isNoContent());

        // 멤버십 삭제 → 잔존 access 토큰으로도 전 농장 즉시 F002
        mockMvc.perform(get("/api/farms/" + farmA)
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
        mockMvc.perform(get("/api/farms/" + farmB)
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));

        // 멤버 목록에서 제외 (오너만 남음)
        MvcResult membersResult = mockMvc.perform(get("/api/farms/" + farmA + "/members")
                        .header("Authorization", "Bearer " + ownerA))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode members = readJson(membersResult);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).get("nickname").asText()).isEqualTo("오너A");

        // 소속했던 각 농장의 활성 초대코드 무효화 — 제3자가 구 코드로 합류 불가
        String outsider = signupAndLogin("제3자");
        for (String code : new String[] {codeA, codeB}) {
            mockMvc.perform(post("/api/invitations/accept")
                            .header("Authorization", "Bearer " + outsider)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AcceptInvitationRequest(code))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("F004"));
        }
    }

    @Test
    @DisplayName("탈퇴 후 동일 이메일 재가입 성공 — 새 계정이며 구 데이터와 연결되지 않는다")
    void withdrawnEmailCanSignupAgainAsFreshAccount() throws Exception {
        String email = uniqueEmail("withdraw-rejoin");
        JsonNode firstTokens = signupAndLoginTokens(email, "재가입유저");
        String firstAccess = firstTokens.get("accessToken").asText();
        long firstUserId = myUserId(firstAccess);

        // 구 계정을 농장 MEMBER로 소속시켜 "구 데이터 미연결"을 검증할 대상 생성
        String owner = signupAndLogin("재가입농장주");
        long farmId = createFarm(owner, "재가입검증농장");
        acceptInvitation(firstAccess, createInvitationCode(owner, farmId));

        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + firstAccess))
                .andExpect(status().isNoContent());

        // V1 partial unique index(WHERE deleted_at IS NULL)가 동일 이메일 재가입을 허용
        JsonNode secondTokens = signupAndLoginTokens(email, "재가입유저2");
        String secondAccess = secondTokens.get("accessToken").asText();
        long secondUserId = myUserId(secondAccess);

        assertThat(secondUserId).isNotEqualTo(firstUserId);

        // 새 계정은 구 계정의 농장 소속을 물려받지 않는다
        MvcResult farmsResult = mockMvc.perform(get("/api/farms")
                        .header("Authorization", "Bearer " + secondAccess))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readJson(farmsResult)).isEmpty();
        mockMvc.perform(get("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + secondAccess))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }
}
