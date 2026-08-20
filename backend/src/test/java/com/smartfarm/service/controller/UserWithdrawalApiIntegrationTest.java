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
import com.smartfarm.service.dto.FarmRequest;
import com.smartfarm.service.dto.LoginRequest;
import com.smartfarm.service.dto.RefreshRequest;
import com.smartfarm.service.dto.SignupRequest;
import com.smartfarm.service.dto.WithdrawRequest;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.repository.FarmMemberRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 회원 탈퇴(DELETE /api/users/me) 통합 테스트 — contract §3 탈퇴 절(비밀번호 재확인·즉시
 * 익명화·탈퇴 봉쇄 ①~④)·§5 A006. 가능한 한 공개 API 경유(FarmTestSupport 패턴),
 * 잔존 행 시나리오만 repository 직접 구성. Testcontainers PostgreSQL.
 */
class UserWithdrawalApiIntegrationTest extends FarmTestSupport {

    private static final String PASSWORD = "password123";

    @Autowired
    FarmMemberRepository farmMemberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /** access·refresh 둘 다 필요한 시나리오용 — 이메일 지정 가입+로그인. */
    private JsonNode signupAndLoginTokens(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(email, PASSWORD, nickname))))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }

    private ResultActions performWithdraw(String accessToken, String password) throws Exception {
        return mockMvc.perform(delete("/api/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new WithdrawRequest(password))));
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    @DisplayName("OWNER 농장 보유 시 409 A006, 농장 삭제 후 탈퇴는 204")
    void withdrawBlockedWhileOwningFarmThenAllowedAfterFarmDeletion() throws Exception {
        String owner = signupAndLogin("탈퇴오너");
        long ownerUserId = myUserId(owner);
        long farmId = createFarm(owner, "탈퇴검증농장");

        performWithdraw(owner, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("A006"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(delete("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());

        // farm soft delete는 farm_members를 지우지 않음 — 잔존 OWNER 멤버십이 탈퇴를 막지 않아야 한다
        performWithdraw(owner, PASSWORD).andExpect(status().isNoContent());

        // soft delete된 농장의 잔존 멤버십 행도 탈퇴가 함께 정리한다
        assertThat(farmMemberRepository.findAllByUserId(ownerUserId)).isEmpty();
    }

    @Test
    @DisplayName("비밀번호 불일치 시 401 A002 — 계정은 보존된다(토큰 탈취 단독 탈퇴 차단)")
    void withdrawWithWrongPasswordReturnsA002AndKeepsAccount() throws Exception {
        String accessToken = signupAndLogin("비번검증유저");

        performWithdraw(accessToken, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A002"));

        // 계정 무손상 — me 정상 조회
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("탈퇴 후 기존 refresh 토큰은 A004, 기존 access 토큰의 me 조회도 A004")
    void withdrawInvalidatesAllTokens() throws Exception {
        JsonNode tokens = signupAndLoginTokens(uniqueEmail("withdraw-tokens"), "탈퇴토큰유저");
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();

        performWithdraw(accessToken, PASSWORD).andExpect(status().isNoContent());

        // refresh — revokeAllByUserId + soft delete 유저 차단(#10) 이중 차단
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A004"));

        // access는 만료 전까지 서명은 유효하지만 유저 표면은 즉시 A004
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A004"));
    }

    @Test
    @DisplayName("탈퇴 후 잔존 access 토큰으로 농장 생성·초대 수락 불가 — 401 A004 (탈퇴 봉쇄 ①)")
    void residualAccessTokenCannotCreateFarmOrAcceptInvitation() throws Exception {
        // 유효한 초대코드(살아있는 제3 농장) 준비
        String liveOwner = signupAndLogin("생존오너");
        long liveFarmId = createFarm(liveOwner, "생존농장");
        String liveCode = createInvitationCode(liveOwner, liveFarmId);

        String withdrawn = signupAndLogin("탈퇴재진입유저");
        performWithdraw(withdrawn, PASSWORD).andExpect(status().isNoContent());

        // 유령 OWNER 농장 생성 차단
        mockMvc.perform(post("/api/farms")
                        .header("Authorization", "Bearer " + withdrawn)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmRequest("유령농장", CropType.TOMATO, null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A004"));

        // 멤버십 재획득(초대 수락) 차단
        mockMvc.perform(post("/api/invitations/accept")
                        .header("Authorization", "Bearer " + withdrawn)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcceptInvitationRequest(liveCode))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A004"));
    }

    @Test
    @DisplayName("soft delete 유저의 잔존 멤버십 행이 있어도 농장 접근 F002·멤버 수 미포함 (가드 JOIN User)")
    void residualMembershipRowOfWithdrawnUserIsInert() throws Exception {
        String owner = signupAndLogin("잔존행오너");
        long farmId = createFarm(owner, "잔존행농장");

        String withdrawn = signupAndLogin("잔존행유저");
        long withdrawnUserId = myUserId(withdrawn);
        performWithdraw(withdrawn, PASSWORD).andExpect(status().isNoContent());

        // 탈퇴↔멤버십 생성 race로 남을 수 있는 잔존 행을 직접 재현(공개 API로는 이제 생성 불가)
        farmMemberRepository.save(FarmMember.builder()
                .farmId(farmId)
                .userId(withdrawnUserId)
                .role(FarmRole.MEMBER)
                .build());

        // 잔존 행이 있어도 가드의 User join(@SQLRestriction)이 차단 → F002
        mockMvc.perform(get("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + withdrawn))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));

        // 가드 밖 목록(findMyFarms)도 User join으로 농장 요약 비노출 → 빈 목록
        MvcResult myFarmsResult = mockMvc.perform(get("/api/farms")
                        .header("Authorization", "Bearer " + withdrawn))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readJson(myFarmsResult)).isEmpty();

        // 유령 멤버 정합 — 목록·memberCount 모두 생존 유저 기준 1(오너뿐)
        MvcResult farmResult = mockMvc.perform(get("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readJson(farmResult).get("memberCount").asInt()).isEqualTo(1);
        MvcResult membersResult = mockMvc.perform(get("/api/farms/" + farmId + "/members")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readJson(membersResult)).hasSize(1);
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
        long memberUserId = myUserId(member);
        acceptInvitation(member, codeA);
        acceptInvitation(member, codeB);

        performWithdraw(member, PASSWORD).andExpect(status().isNoContent());

        // 멤버십 벌크 삭제 — 잔존 행 0건
        assertThat(farmMemberRepository.findAllByUserId(memberUserId)).isEmpty();

        // 잔존 access 토큰으로도 전 농장 즉시 F002
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
    @DisplayName("탈퇴 시 PII 즉시 익명화되고, 동일 이메일 재가입은 새 계정으로 성공한다")
    void withdrawAnonymizesPiiAndAllowsRejoinWithSameEmail() throws Exception {
        String email = uniqueEmail("withdraw-rejoin");
        JsonNode firstTokens = signupAndLoginTokens(email, "재가입유저");
        String firstAccess = firstTokens.get("accessToken").asText();
        long firstUserId = myUserId(firstAccess);

        // 구 계정을 농장 MEMBER로 소속시켜 "구 데이터 미연결"을 검증할 대상 생성
        String owner = signupAndLogin("재가입농장주");
        long farmId = createFarm(owner, "재가입검증농장");
        acceptInvitation(firstAccess, createInvitationCode(owner, farmId));

        performWithdraw(firstAccess, PASSWORD).andExpect(status().isNoContent());

        // PII 즉시 익명화 — email·nickname 대체, password는 BCrypt가 될 수 없는 무효값
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT email, nickname, password, deleted_at FROM users WHERE id = ?", firstUserId);
        assertThat(row.get("email")).isEqualTo("withdrawn-" + firstUserId + "@invalid");
        assertThat(row.get("nickname")).isEqualTo("탈퇴회원");
        assertThat((String) row.get("password")).doesNotStartWith("$2");
        assertThat(row.get("deleted_at")).isNotNull();

        // V1 partial unique index(WHERE deleted_at IS NULL) + 익명화 이후에도 재가입 허용
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
