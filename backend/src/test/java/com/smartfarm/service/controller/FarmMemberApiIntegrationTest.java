package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.AcceptInvitationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class FarmMemberApiIntegrationTest extends FarmTestSupport {

    // ── 목록 ──────────────────────────────────────────────

    @Test
    @DisplayName("멤버는 멤버 목록을 조회한다 — memberId/userId/nickname/role/joinedAt, 합류순 정렬")
    void findMembersAsMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String memberToken = signupAndLogin("일꾼이");
        long farmId = createFarm(ownerToken, "명단 농장");
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));

        mockMvc.perform(get("/api/farms/" + farmId + "/members")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nickname").value("주인장"))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].memberId").isNumber())
                .andExpect(jsonPath("$[0].userId").isNumber())
                .andExpect(jsonPath("$[0].joinedAt").exists())
                .andExpect(jsonPath("$[1].nickname").value("일꾼이"))
                .andExpect(jsonPath("$[1].role").value("MEMBER"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 타 농장 멤버 목록 조회 시 403 F002를 반환한다")
    void findMembersAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 명단 농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/members")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    // ── 제거/탈퇴 ─────────────────────────────────────────

    @Test
    @DisplayName("OWNER가 멤버를 제거하면 204, 제거된 멤버는 F002가 되고 기존 활성 코드도 무효화된다")
    void ownerRemovesMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String memberToken = signupAndLogin("일꾼이");
        String lateJoinerToken = signupAndLogin("뒷북이");
        long farmId = createFarm(ownerToken, "해고 농장");
        String code = createInvitationCode(ownerToken, farmId);
        acceptInvitation(memberToken, code);
        long memberId = memberIdOf(ownerToken, farmId, "일꾼이");

        mockMvc.perform(delete("/api/farms/" + farmId + "/members/" + memberId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));

        mockMvc.perform(get("/api/farms/" + farmId + "/members")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // 멤버 제거 시 해당 농장 활성 코드 전부 무효화 — 구 코드로 신규 합류 불가
        mockMvc.perform(post("/api/invitations/accept")
                        .header("Authorization", "Bearer " + lateJoinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcceptInvitationRequest(code))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("F004"));
    }

    @Test
    @DisplayName("MEMBER 본인 탈퇴 시 기존 코드는 무효화(F004)되고, OWNER가 재발급한 새 코드로만 재합류할 수 있다")
    void memberLeavesAndRejoins() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String memberToken = signupAndLogin("일꾼이");
        long farmId = createFarm(ownerToken, "이직 농장");
        String code = createInvitationCode(ownerToken, farmId);
        acceptInvitation(memberToken, code);
        long myMemberId = memberIdOf(memberToken, farmId, "일꾼이");

        mockMvc.perform(delete("/api/farms/" + farmId + "/members/" + myMemberId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));

        // 탈퇴로 활성 코드가 무효화됨 — 탈퇴자가 보유한 구 코드로 재합류 불가
        mockMvc.perform(post("/api/invitations/accept")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcceptInvitationRequest(code))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("F004"));

        // OWNER가 재발급한 새 코드로는 재합류 가능
        String newCode = createInvitationCode(ownerToken, farmId);
        acceptInvitation(memberToken, newCode);
        mockMvc.perform(get("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"));
    }

    @Test
    @DisplayName("MEMBER가 다른 멤버를 제거하려 하면 403 F003을 반환한다")
    void memberRemovesOtherMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String member1Token = signupAndLogin("일꾼일");
        String member2Token = signupAndLogin("일꾼이");
        long farmId = createFarm(ownerToken, "내분 농장");
        String code = createInvitationCode(ownerToken, farmId);
        acceptInvitation(member1Token, code);
        acceptInvitation(member2Token, code);
        long member2Id = memberIdOf(member1Token, farmId, "일꾼이");

        mockMvc.perform(delete("/api/farms/" + farmId + "/members/" + member2Id)
                        .header("Authorization", "Bearer " + member1Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    @Test
    @DisplayName("OWNER 본인 탈퇴 시도는 400 F006을 반환한다 (농장 삭제로만 가능)")
    void ownerCannotLeave() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmId = createFarm(ownerToken, "독박 농장");
        long ownerMemberId = memberIdOf(ownerToken, farmId, "주인장");

        mockMvc.perform(delete("/api/farms/" + farmId + "/members/" + ownerMemberId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("F006"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 타 농장 멤버 제거 시 403 F002를 반환한다")
    void removeMemberAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 제거 농장");
        long ownerMemberId = memberIdOf(ownerToken, farmId, "주인장");

        mockMvc.perform(delete("/api/farms/" + farmId + "/members/" + ownerMemberId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("OWNER가 존재하지 않는 memberId 제거 시 204 no-op이다 (멱등 DELETE)")
    void ownerRemovesNonexistentMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmId = createFarm(ownerToken, "멱등 농장");

        mockMvc.perform(delete("/api/farms/" + farmId + "/members/999999999")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("soft delete된 농장의 멤버 목록 조회는 404 F001을 반환한다")
    void findMembersOnDeletedFarm() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmId = createFarm(ownerToken, "폐업 명단 농장");
        mockMvc.perform(delete("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/farms/" + farmId + "/members")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("F001"));
    }

    @Test
    @DisplayName("soft delete된 농장의 멤버 제거는 404 F001을 반환한다")
    void removeMemberOnDeletedFarm() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmId = createFarm(ownerToken, "폐업 제거 농장");
        long ownerMemberId = memberIdOf(ownerToken, farmId, "주인장");
        mockMvc.perform(delete("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/farms/" + farmId + "/members/" + ownerMemberId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("F001"));
    }

    @Test
    @DisplayName("cross-farm memberId는 farm 스코프 조회로 걸러져 204 no-op이고 타 농장 멤버십은 유지된다")
    void crossFarmMemberIdIsScopedOut() throws Exception {
        // 농장1(주인장A) / 농장2(주인장B + 일꾼이)
        String ownerAToken = signupAndLogin("주인장갑");
        String ownerBToken = signupAndLogin("주인장을");
        String memberToken = signupAndLogin("일꾼이");
        long farm1 = createFarm(ownerAToken, "갑의 농장");
        long farm2 = createFarm(ownerBToken, "을의 농장");
        acceptInvitation(memberToken, createInvitationCode(ownerBToken, farm2));
        long farm2MemberId = memberIdOf(ownerBToken, farm2, "일꾼이");

        // 농장1 OWNER가 자기 farmId + 타 농장 memberId로 삭제 시도 → 스코프 밖이라 no-op
        mockMvc.perform(delete("/api/farms/" + farm1 + "/members/" + farm2MemberId)
                        .header("Authorization", "Bearer " + ownerAToken))
                .andExpect(status().isNoContent());

        // 농장2 멤버십은 그대로 살아 있다
        mockMvc.perform(get("/api/farms/" + farm2)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2));
    }
}
