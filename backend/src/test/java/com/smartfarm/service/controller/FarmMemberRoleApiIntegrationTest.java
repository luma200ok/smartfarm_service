package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.MemberRoleUpdateRequest;
import com.smartfarm.service.entity.FarmRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 멤버 역할 변경 API(이슈 #122) — {@code PATCH /api/farms/{farmId}/members/{memberId}/role}.
 *
 * <p>핵심 불변식은 <b>"농장에는 항상 ADMIN이 최소 1명 있다"</b>이다. 이게 깨지면 농장은 구조 변경·
 * 멤버 관리·삭제가 영구 불가한 상태로 고착되고, 되돌릴 수 있는 사람이 아무도 남지 않는다.
 * 강등(PATCH)과 제거(DELETE) <b>양쪽</b>이 같은 불변식을 지켜야 하므로 둘 다 여기서 검증한다.
 * (동시 요청 race는 {@code FarmMemberRoleConcurrencyTest}가 잠금 메커니즘으로 검증한다.)
 */
class FarmMemberRoleApiIntegrationTest extends FarmTestSupport {

    private ResultActions changeRole(String token, long farmId, long memberId, FarmRole role)
            throws Exception {
        return mockMvc.perform(patch("/api/farms/" + farmId + "/members/" + memberId + "/role")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MemberRoleUpdateRequest(role))));
    }

    @Nested
    @DisplayName("승인 흐름 — 초대 수락자(PENDING)에게 역할을 부여한다")
    class Approval {

        @Test
        @DisplayName("ADMIN이 PENDING을 OPERATOR로 승인하면 200, 응답의 role=OPERATOR·pending=false")
        void approvePendingAsOperator() throws Exception {
            String adminToken = signupAndLogin("승인-관리자");
            String joinerToken = signupAndLogin("승인-신입");
            long farmId = createFarm(adminToken, "승인 농장");
            acceptInvitation(joinerToken, createInvitationCode(adminToken, farmId));
            long memberId = memberIdOfUser(adminToken, farmId, myUserId(joinerToken));

            changeRole(adminToken, farmId, memberId, FarmRole.OPERATOR)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.memberId").value(memberId))
                    .andExpect(jsonPath("$.nickname").value("승인-신입"))
                    .andExpect(jsonPath("$.role").value("OPERATOR"))
                    .andExpect(jsonPath("$.pending").value(false));

            // 승인 결과가 목록에도 반영된다
            mockMvc.perform(get("/api/farms/" + farmId + "/members")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[1].role").value("OPERATOR"))
                    .andExpect(jsonPath("$[1].pending").value(false));
        }

        @Test
        @DisplayName("ADMIN 역할도 부여할 수 있다 — 관리자 위임(마지막 ADMIN 제약을 푸는 정상 경로)")
        void promoteToAdmin() throws Exception {
            String adminToken = signupAndLogin("위임-관리자");
            String secondToken = signupAndLogin("위임-후임");
            long farmId = createFarm(adminToken, "위임 농장");
            long memberId = joinFarmAs(adminToken, farmId, secondToken, FarmRole.VIEWER);

            changeRole(adminToken, farmId, memberId, FarmRole.ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ADMIN"));

            // 위임받은 쪽도 즉시 관리자 권한을 행사할 수 있다
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/farms/" + farmId + "/invitations")
                            .header("Authorization", "Bearer " + secondToken))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("마지막 ADMIN 보호 (F006) — 강등·제거 양쪽")
    class LastAdminProtection {

        @Test
        @DisplayName("유일한 ADMIN이 자기 자신을 강등하려 하면 400 F006")
        void soleAdminCannotDemoteSelf() throws Exception {
            String adminToken = signupAndLogin("독박-관리자");
            long farmId = createFarm(adminToken, "독박 농장");
            long myMemberId = memberIdOfUser(adminToken, farmId, myUserId(adminToken));

            changeRole(adminToken, farmId, myMemberId, FarmRole.OPERATOR)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("F006"));

            // 강등이 실제로 일어나지 않았다 — 여전히 관리자다
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/farms/" + farmId + "/invitations")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("유일한 ADMIN을 PENDING으로 되돌리는 것도 400 F006 (강등의 다른 형태)")
        void soleAdminCannotBeSetBackToPending() throws Exception {
            String adminToken = signupAndLogin("보류-관리자");
            long farmId = createFarm(adminToken, "보류 농장");
            long myMemberId = memberIdOfUser(adminToken, farmId, myUserId(adminToken));

            changeRole(adminToken, farmId, myMemberId, FarmRole.PENDING)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("F006"));
        }

        @Test
        @DisplayName("ADMIN이 2명이면 한 명은 강등할 수 있고, 남은 마지막 한 명은 다시 F006이다")
        void demotionAllowedUntilLastAdminRemains() throws Exception {
            String adminA = signupAndLogin("교대-관리자갑");
            String adminB = signupAndLogin("교대-관리자을");
            long farmId = createFarm(adminA, "교대 농장");
            long memberB = joinFarmAs(adminA, farmId, adminB, FarmRole.ADMIN);
            long memberA = memberIdOfUser(adminA, farmId, myUserId(adminA));

            // 관리자 2명 → 갑이 자기 자신을 내려놓는다(을에게 위임 완료)
            changeRole(adminA, farmId, memberA, FarmRole.OPERATOR)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("OPERATOR"));

            // 이제 을이 유일한 관리자 → 자기 강등 불가
            changeRole(adminB, farmId, memberB, FarmRole.VIEWER)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("F006"));
        }

        @Test
        @DisplayName("ADMIN이 2명이면 한 명이 다른 ADMIN을 강등할 수 있다")
        void adminCanDemoteAnotherAdmin() throws Exception {
            String adminA = signupAndLogin("정리-관리자갑");
            String adminB = signupAndLogin("정리-관리자을");
            long farmId = createFarm(adminA, "정리 농장");
            long memberB = joinFarmAs(adminA, farmId, adminB, FarmRole.ADMIN);

            changeRole(adminA, farmId, memberB, FarmRole.VIEWER)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("VIEWER"));
        }

        @Test
        @DisplayName("마지막 ADMIN 제거(본인 탈퇴)는 400 F006 — 강등과 같은 불변식")
        void soleAdminCannotBeRemoved() throws Exception {
            String adminToken = signupAndLogin("퇴장-관리자");
            long farmId = createFarm(adminToken, "퇴장 농장");
            long myMemberId = memberIdOfUser(adminToken, farmId, myUserId(adminToken));

            mockMvc.perform(delete("/api/farms/" + farmId + "/members/" + myMemberId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("F006"));
        }

        @Test
        @DisplayName("ADMIN이 2명이면 ADMIN도 농장을 나갈 수 있다 — 구 계약(OWNER 탈퇴 불가)의 완화")
        void adminCanLeaveWhenAnotherAdminRemains() throws Exception {
            String adminA = signupAndLogin("이탈-관리자갑");
            String adminB = signupAndLogin("이탈-관리자을");
            long farmId = createFarm(adminA, "이탈 농장");
            joinFarmAs(adminA, farmId, adminB, FarmRole.ADMIN);
            long memberA = memberIdOfUser(adminA, farmId, myUserId(adminA));

            mockMvc.perform(delete("/api/farms/" + farmId + "/members/" + memberA)
                            .header("Authorization", "Bearer " + adminA))
                    .andExpect(status().isNoContent());

            // 남은 관리자는 그대로 관리자다
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/farms/" + farmId + "/invitations")
                            .header("Authorization", "Bearer " + adminB))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("ADMIN이 다른 ADMIN을 제거할 때도 마지막 1명이면 F006 — 자기 자신이 아니어도 같다")
        void removingTheOnlyAdminByAnotherRequesterIsAlsoBlocked() throws Exception {
            // 갑(ADMIN) → 을을 ADMIN으로 올렸다가 갑이 먼저 나간다 → 을이 유일한 관리자
            String adminA = signupAndLogin("연쇄-관리자갑");
            String adminB = signupAndLogin("연쇄-관리자을");
            long farmId = createFarm(adminA, "연쇄 농장");
            long memberB = joinFarmAs(adminA, farmId, adminB, FarmRole.ADMIN);
            long memberA = memberIdOfUser(adminA, farmId, myUserId(adminA));
            mockMvc.perform(delete("/api/farms/" + farmId + "/members/" + memberA)
                            .header("Authorization", "Bearer " + adminA))
                    .andExpect(status().isNoContent());

            // 을이 자기 자신을 제거하려 하면 마지막 관리자라 F006
            mockMvc.perform(delete("/api/farms/" + farmId + "/members/" + memberB)
                            .header("Authorization", "Bearer " + adminB))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("F006"));
        }
    }

    @Nested
    @DisplayName("입력·격리 검증")
    class ValidationAndIsolation {

        @Test
        @DisplayName("농장에 없는 memberId는 404 F009")
        void unknownMemberId() throws Exception {
            String adminToken = signupAndLogin("미상-관리자");
            long farmId = createFarm(adminToken, "미상 농장");

            changeRole(adminToken, farmId, 999_999_999L, FarmRole.OPERATOR)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("F009"));
        }

        @Test
        @DisplayName("cross-tenant: 타 농장 memberId를 내 farmId 경로에 끼워 넣으면 404 F009 "
                + "(farm 스코프 조회 — 타 농장 멤버십은 건드릴 수 없고 존재도 유추할 수 없다)")
        void crossTenantMemberIdIsScopedOut() throws Exception {
            String adminA = signupAndLogin("격리-관리자갑");
            String adminB = signupAndLogin("격리-관리자을");
            String memberToken = signupAndLogin("격리-일꾼");
            long farmA = createFarm(adminA, "격리 갑농장");
            long farmB = createFarm(adminB, "격리 을농장");
            long farmBMemberId = joinFarmAs(adminB, farmB, memberToken, FarmRole.VIEWER);

            changeRole(adminA, farmA, farmBMemberId, FarmRole.ADMIN)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("F009"));

            // 타 농장 멤버십은 그대로 VIEWER다
            mockMvc.perform(get("/api/farms/" + farmB + "/members")
                            .header("Authorization", "Bearer " + adminB))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[1].role").value("VIEWER"));
        }

        @Test
        @DisplayName("미멤버가 역할을 바꾸려 하면 403 F002 (농장 존재 유추 차단)")
        void nonMemberIsRejected() throws Exception {
            String adminToken = signupAndLogin("무단-관리자");
            String outsiderToken = signupAndLogin("무단-외부인");
            long farmId = createFarm(adminToken, "무단 농장");
            long memberId = memberIdOfUser(adminToken, farmId, myUserId(adminToken));

            changeRole(outsiderToken, farmId, memberId, FarmRole.VIEWER)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("F002"));
        }

        @Test
        @DisplayName("알 수 없는 역할 문자열·role 누락은 400 C001")
        void invalidRoleValue() throws Exception {
            String adminToken = signupAndLogin("오타-관리자");
            long farmId = createFarm(adminToken, "오타 농장");
            long memberId = memberIdOfUser(adminToken, farmId, myUserId(adminToken));
            String path = "/api/farms/" + farmId + "/members/" + memberId + "/role";

            mockMvc.perform(patch(path)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"SUPERUSER\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));

            mockMvc.perform(patch(path)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("데모 계정은 역할 변경이 403 A007로 차단된다 (공유 계정의 권한 구성 변조 차단)")
        void demoAccountIsBlocked() throws Exception {
            String demoToken = demoAccountLogin();
            long demoFarmId = demoAccountFarmId(demoToken);
            long demoMemberId = memberIdOfUser(demoToken, demoFarmId, myUserId(demoToken));

            changeRole(demoToken, demoFarmId, demoMemberId, FarmRole.VIEWER)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("A007"));
        }
    }
}
