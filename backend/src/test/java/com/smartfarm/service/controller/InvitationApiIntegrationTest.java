package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.AcceptInvitationRequest;
import com.smartfarm.service.entity.Invitation;
import com.smartfarm.service.repository.InvitationRepository;
import com.smartfarm.service.service.TokenHasher;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class InvitationApiIntegrationTest extends FarmTestSupport {

    @Autowired
    InvitationRepository invitationRepository;

    // ── 발급 ──────────────────────────────────────────────

    @Test
    @DisplayName("OWNER는 초대코드를 발급받는다 — URL-safe 43자 코드와 expiresAt 반환")
    void createInvitationAsOwner() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmId = createFarm(ownerToken, "초대 농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("^[A-Za-z0-9_-]{43}$")))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    @DisplayName("MEMBER가 초대코드 발급 시 403 F003을 반환한다")
    void createInvitationAsMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String memberToken = signupAndLogin("일꾼이");
        long farmId = createFarm(ownerToken, "초대 권한 농장");
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));

        mockMvc.perform(post("/api/farms/" + farmId + "/invitations")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 타 농장 초대코드 발급 시 403 F002를 반환한다")
    void createInvitationAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 초대 농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/invitations")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    // ── 수락 ──────────────────────────────────────────────

    @Test
    @DisplayName("초대 수락 시 200 FarmResponse(MEMBER)를 반환하고 멤버로 합류한다")
    void acceptInvitationSuccess() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String joinerToken = signupAndLogin("신입이");
        long farmId = createFarm(ownerToken, "합류 농장");
        String code = createInvitationCode(ownerToken, farmId);

        mockMvc.perform(post("/api/invitations/accept")
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcceptInvitationRequest(code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(farmId))
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    @DisplayName("무효한 초대코드는 400 F004를 반환한다")
    void acceptInvalidCode() throws Exception {
        String token = signupAndLogin("신입이");

        mockMvc.perform(post("/api/invitations/accept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AcceptInvitationRequest("no-such-code-" + UUID.randomUUID()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("F004"));
    }

    @Test
    @DisplayName("만료된 초대코드는 400 F004를 반환한다")
    void acceptExpiredCode() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String joinerToken = signupAndLogin("신입이");
        long farmId = createFarm(ownerToken, "만료 농장");
        String expiredCode = "expired-" + UUID.randomUUID();
        invitationRepository.save(Invitation.builder()
                .farmId(farmId)
                .codeHash(TokenHasher.sha256(expiredCode))
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build());

        mockMvc.perform(post("/api/invitations/accept")
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcceptInvitationRequest(expiredCode))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("F004"));
    }

    @Test
    @DisplayName("이미 멤버가 다시 수락하면 409 F005를 반환한다")
    void acceptTwiceReturnsF005() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String joinerToken = signupAndLogin("신입이");
        long farmId = createFarm(ownerToken, "중복 수락 농장");
        String code = createInvitationCode(ownerToken, farmId);
        acceptInvitation(joinerToken, code);

        mockMvc.perform(post("/api/invitations/accept")
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcceptInvitationRequest(code))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("F005"));
    }

    @Test
    @DisplayName("OWNER가 코드를 수락하면 이미 멤버이므로 409 F005를 반환한다")
    void acceptOwnRoleReturnsF005() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmId = createFarm(ownerToken, "셀프 수락 농장");
        String code = createInvitationCode(ownerToken, farmId);

        mockMvc.perform(post("/api/invitations/accept")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcceptInvitationRequest(code))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("F005"));
    }

    @Test
    @DisplayName("초대코드는 만료 전까지 재사용 가능하다 — 서로 다른 두 유저가 같은 코드로 합류")
    void acceptSameCodeByTwoUsers() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String joiner1 = signupAndLogin("일꾼일");
        String joiner2 = signupAndLogin("일꾼이");
        long farmId = createFarm(ownerToken, "재사용 농장");
        String code = createInvitationCode(ownerToken, farmId);

        acceptInvitation(joiner1, code);
        acceptInvitation(joiner2, code);

        mockMvc.perform(get("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(3));
    }

    @Test
    @DisplayName("재발급 시 기존 활성 코드는 무효화된다 — 구 코드 F004, 신 코드 정상 (농장당 활성 1건)")
    void reissueRevokesOldCode() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String joinerToken = signupAndLogin("신입이");
        long farmId = createFarm(ownerToken, "재발급 농장");
        String oldCode = createInvitationCode(ownerToken, farmId);
        String newCode = createInvitationCode(ownerToken, farmId);

        mockMvc.perform(post("/api/invitations/accept")
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcceptInvitationRequest(oldCode))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("F004"));

        acceptInvitation(joinerToken, newCode);
        mockMvc.perform(get("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"));
    }

    @Test
    @DisplayName("soft delete된 농장에 초대코드 발급 시 404 F001을 반환한다")
    void createInvitationOnDeletedFarm() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmId = createFarm(ownerToken, "폐업 발급 농장");
        mockMvc.perform(delete("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/farms/" + farmId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("F001"));
    }

    @Test
    @DisplayName("soft delete된 농장의 초대코드 수락은 400 F004를 반환한다")
    void acceptCodeOfDeletedFarm() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String joinerToken = signupAndLogin("신입이");
        long farmId = createFarm(ownerToken, "폐업 예정 농장");
        String code = createInvitationCode(ownerToken, farmId);

        mockMvc.perform(delete("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/invitations/accept")
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcceptInvitationRequest(code))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("F004"));
    }

    @Test
    @DisplayName("무인증으로 초대 수락 시 401을 반환한다")
    void acceptWithoutToken() throws Exception {
        mockMvc.perform(post("/api/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcceptInvitationRequest("any-code"))))
                .andExpect(status().isUnauthorized());
    }
}
