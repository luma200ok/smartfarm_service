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
import com.smartfarm.service.dto.AcceptInvitationRequest;
import com.smartfarm.service.dto.FarmRequest;
import com.smartfarm.service.dto.FarmUpdateRequest;
import com.smartfarm.service.dto.RefreshRequest;
import com.smartfarm.service.dto.WithdrawRequest;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.entity.User;
import com.smartfarm.service.init.DemoAccountInitializer;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 데모 계정 통합 테스트 (이슈 #49, contract §4.5) — demo-login 발급·시드 idempotency·
 * 파괴적 작업 전수 차단(403 A007)·일반 계정 회귀.
 *
 * <p>시드는 컨텍스트 기동 시(SmartLifecycle) 1회 실행된 상태를 전제한다. 싱글턴 컨테이너를
 * 전 테스트가 공유하지만 데모 유저는 전역 1건 고정이라 클래스 간 간섭이 없다.
 */
class DemoAccountApiIntegrationTest extends FarmTestSupport {

    private static final String DEMO_EMAIL = "demo@smartfarm.local";

    @Autowired
    DemoAccountInitializer demoAccountInitializer;

    @Autowired
    UserRepository userRepository;

    @Autowired
    FarmMemberRepository farmMemberRepository;

    // --- 헬퍼 ---

    JsonNode demoLogin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/demo-login"))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }

    String demoAccessToken() throws Exception {
        return demoLogin().get("accessToken").asText();
    }

    /** 시드된 데모 농장 id — 데모 계정 내 농장 목록의 첫(유일) 농장. */
    long demoFarmId(String demoToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/farms")
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode farms = readJson(result);
        assertThat(farms.size()).isGreaterThanOrEqualTo(1);
        return farms.get(0).get("id").asLong();
    }

    ResultActions expectA007(ResultActions actions) throws Exception {
        return actions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"))
                .andExpect(jsonPath("$.message").value("데모 계정에서는 이 작업을 수행할 수 없습니다."));
    }

    @Nested
    @DisplayName("demo-login")
    class DemoLogin {

        @Test
        @DisplayName("자격증명 없이 200 TokenResponse를 반환하고, access 토큰으로 데모 유저 조회가 된다")
        void demoLoginIssuesTokens() throws Exception {
            JsonNode tokens = demoLogin();
            assertThat(tokens.get("accessToken").asText()).isNotBlank();
            assertThat(tokens.get("refreshToken").asText()).isNotBlank();

            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(DEMO_EMAIL))
                    .andExpect(jsonPath("$.nickname").value("데모 계정"));
        }

        @Test
        @DisplayName("발급된 refresh 토큰으로 로테이션 재발급이 동작한다")
        void demoRefreshRotates() throws Exception {
            JsonNode tokens = demoLogin();
            MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RefreshRequest(tokens.get("refreshToken").asText()))))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode rotated = readJson(refreshed);
            assertThat(rotated.get("accessToken").asText()).isNotBlank();
            assertThat(rotated.get("refreshToken").asText())
                    .isNotEqualTo(tokens.get("refreshToken").asText());
        }
    }

    @Nested
    @DisplayName("시드")
    class Seed {

        @Test
        @DisplayName("기동 시드 완료 상태에서 seed() 재실행해도 유저·농장이 늘지 않는다(idempotent)")
        void seedIsIdempotent() throws Exception {
            // 기동 시 이미 1회 실행된 상태 — 데모 유저·데모 농장 존재
            User demoUser = userRepository.findByEmail(DEMO_EMAIL).orElseThrow();
            assertThat(demoUser.isDemo()).isTrue();
            long membershipsBefore = farmMemberRepository.findAllByUserId(demoUser.getId()).size();
            assertThat(membershipsBefore).isEqualTo(1);

            demoAccountInitializer.seed();
            demoAccountInitializer.seed();

            assertThat(userRepository.findByEmail(DEMO_EMAIL).orElseThrow().getId())
                    .isEqualTo(demoUser.getId());
            assertThat(farmMemberRepository.findAllByUserId(demoUser.getId()))
                    .hasSize(1)
                    .allSatisfy(member -> assertThat(member.getRole()).isEqualTo(FarmRole.OWNER));
        }

        @Test
        @DisplayName("시드된 데모 농장은 토마토 농장 1개(OWNER)다")
        void seededDemoFarm() throws Exception {
            String token = demoAccessToken();
            MvcResult result = mockMvc.perform(get("/api/farms")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode farms = readJson(result);
            assertThat(farms).hasSize(1);
            assertThat(farms.get(0).get("name").asText()).isEqualTo("데모 토마토 농장");
            assertThat(farms.get(0).get("cropType").asText()).isEqualTo("TOMATO");
            assertThat(farms.get(0).get("myRole").asText()).isEqualTo("OWNER");
        }
    }

    @Nested
    @DisplayName("파괴적 작업 차단 — 전부 403 A007")
    class DestructiveOperationsBlocked {

        @Test
        @DisplayName("회원 탈퇴(DELETE /api/users/me)는 비밀번호와 무관하게 A007")
        void withdrawBlocked() throws Exception {
            expectA007(mockMvc.perform(delete("/api/users/me")
                    .header("Authorization", "Bearer " + demoAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new WithdrawRequest("password123")))));
        }

        @Test
        @DisplayName("농장 생성(POST /api/farms)은 A007")
        void createFarmBlocked() throws Exception {
            expectA007(mockMvc.perform(post("/api/farms")
                    .header("Authorization", "Bearer " + demoAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            new FarmRequest("새 농장", CropType.TOMATO, null)))));
        }

        @Test
        @DisplayName("농장 수정(PATCH /api/farms/{id})은 본인 소유 농장이어도 A007")
        void updateFarmBlocked() throws Exception {
            String token = demoAccessToken();
            expectA007(mockMvc.perform(patch("/api/farms/" + demoFarmId(token))
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            new FarmUpdateRequest("이름 변경 시도", null, null)))));
        }

        @Test
        @DisplayName("농장 삭제(DELETE /api/farms/{id})는 A007")
        void deleteFarmBlocked() throws Exception {
            String token = demoAccessToken();
            expectA007(mockMvc.perform(delete("/api/farms/" + demoFarmId(token))
                    .header("Authorization", "Bearer " + token)));
        }

        @Test
        @DisplayName("웹훅 설정(PATCH /api/farms/{id}/webhook)은 A007")
        void updateWebhookBlocked() throws Exception {
            String token = demoAccessToken();
            expectA007(mockMvc.perform(patch("/api/farms/" + demoFarmId(token) + "/webhook")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"webhookUrl\":null}")));
        }

        @Test
        @DisplayName("초대코드 발급(POST /api/farms/{id}/invitations)은 A007")
        void createInvitationBlocked() throws Exception {
            String token = demoAccessToken();
            expectA007(mockMvc.perform(post("/api/farms/" + demoFarmId(token) + "/invitations")
                    .header("Authorization", "Bearer " + token)));
        }

        @Test
        @DisplayName("초대 수락(POST /api/invitations/accept)은 유효한 코드여도 A007")
        void acceptInvitationBlocked() throws Exception {
            // 일반 계정 농장의 실제 유효 코드로도 데모 계정 수락은 차단됨을 확인
            String ownerToken = signupAndLogin("데모차단오너");
            long farmId = createFarm(ownerToken, "초대 검증 농장");
            String code = createInvitationCode(ownerToken, farmId);

            expectA007(mockMvc.perform(post("/api/invitations/accept")
                    .header("Authorization", "Bearer " + demoAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new AcceptInvitationRequest(code)))));
        }

        @Test
        @DisplayName("멤버 제거/농장 나가기(DELETE /api/farms/{id}/members/{memberId})는 A007")
        void removeMemberBlocked() throws Exception {
            String token = demoAccessToken();
            long farmId = demoFarmId(token);
            long memberId = memberIdOf(token, farmId, "데모 계정");
            expectA007(mockMvc.perform(delete("/api/farms/" + farmId + "/members/" + memberId)
                    .header("Authorization", "Bearer " + token)));
        }
    }

    @Nested
    @DisplayName("일반 계정 회귀 — 동일 작업이 기존대로 동작")
    class RegularAccountRegression {

        @Test
        @DisplayName("일반 계정은 농장 생성·수정·웹훅·초대 발급·농장 삭제·탈퇴가 그대로 동작한다")
        void regularAccountUnaffected() throws Exception {
            String token = signupAndLogin("일반유저");

            long farmId = createFarm(token, "일반 농장"); // 201은 헬퍼가 검증
            mockMvc.perform(patch("/api/farms/" + farmId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new FarmUpdateRequest("일반 농장 개명", null, null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("일반 농장 개명"));
            mockMvc.perform(patch("/api/farms/" + farmId + "/webhook")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"webhookUrl\":null}"))
                    .andExpect(status().isOk());
            createInvitationCode(token, farmId); // 201은 헬퍼가 검증
            mockMvc.perform(delete("/api/farms/" + farmId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/users/me")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new WithdrawRequest("password123"))))
                    .andExpect(status().isNoContent());
        }
    }
}
