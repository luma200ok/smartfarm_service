package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.dto.LoginRequest;
import com.smartfarm.service.dto.RefreshRequest;
import com.smartfarm.service.dto.SignupRequest;
import com.smartfarm.service.entity.RefreshToken;
import com.smartfarm.service.repository.RefreshTokenRepository;
import com.smartfarm.service.repository.UserRepository;
import com.smartfarm.service.service.TokenHasher;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    UserRepository userRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    // --- 헬퍼 ---

    MvcResult signup(String email, String password, String nickname) throws Exception {
        return mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, password, nickname))))
                .andReturn();
    }

    JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))));
    }

    @Nested
    @DisplayName("회원가입")
    class Signup {

        @Test
        @DisplayName("성공 시 201과 UserResponse를 반환한다")
        void signupSuccess() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SignupRequest("signup-ok@example.com", "password123", "가입유저"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.email").value("signup-ok@example.com"))
                    .andExpect(jsonPath("$.nickname").value("가입유저"))
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        @DisplayName("이메일 중복 시 409 A001을 반환한다")
        void signupDuplicateEmail() throws Exception {
            signup("dup@example.com", "password123", "원조유저");
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SignupRequest("dup@example.com", "password123", "중복유저"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("A001"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("이메일은 trim+소문자로 정규화 저장되고, 대소문자만 다른 이메일은 중복(A001)이다")
        void signupNormalizesEmail() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SignupRequest("  Mixed.Case@Example.COM ", "password123", "정규화유저"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("mixed.case@example.com"));

            // 대소문자만 다른 재가입 → A001
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SignupRequest("MIXED.CASE@example.com", "password123", "중복시도"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("A001"));

            // 다른 케이스 표기로 로그인 성공
            login("Mixed.Case@EXAMPLE.com", "password123");
        }

        @Test
        @DisplayName("raw JSON 본문(Jackson 역직렬화 경로)에서도 이메일이 정규화된다")
        void signupNormalizesEmailViaRawJson() throws Exception {
            // objectMapper 직렬화는 compact constructor가 먼저 실행되므로,
            // 서버 역직렬화 경로 검증은 raw 문자열 본문으로 수행한다.
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"  Raw.Json@Example.COM \", "
                                    + "\"password\": \"password123\", \"nickname\": \"로우제이슨\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("raw.json@example.com"));
        }

        @Test
        @DisplayName("비밀번호가 72바이트 초과(한글 25자)면 500이 아닌 400 C001로 응답한다")
        void signupPasswordOver72Bytes() throws Exception {
            String koreanPassword = "가".repeat(25); // 75바이트 (BCrypt 상한 72바이트 초과)
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SignupRequest("bytes-over@example.com", koreanPassword, "바이트유저"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("이메일 255자 초과는 400 C001 (A001 오매핑 방지)")
        void signupEmailTooLong() throws Exception {
            String longEmail = "a".repeat(250) + "@ex.com"; // 257자
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SignupRequest(longEmail, "password123", "긴이메일"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("깨진 JSON 본문이면 400 C001로 응답한다")
        void signupBrokenJsonBody() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"broken@example.com\", \"password\": "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("비밀번호 8자 미만이면 400 C001과 {timestamp, code, message} 형식으로 응답한다")
        void signupValidationFail() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SignupRequest("short@example.com", "short", "짧은비번"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("성공 시 200과 access/refresh 토큰을 반환한다")
        void loginSuccess() throws Exception {
            signup("login-ok@example.com", "password123", "로그인유저");
            JsonNode tokens = login("login-ok@example.com", "password123");
            org.assertj.core.api.Assertions.assertThat(tokens.get("accessToken").asText()).isNotBlank();
            org.assertj.core.api.Assertions.assertThat(tokens.get("refreshToken").asText()).isNotBlank();
        }

        @Test
        @DisplayName("비밀번호 불일치 시 401 A002를 반환한다")
        void loginWrongPassword() throws Exception {
            signup("login-wrong@example.com", "password123", "로그인유저");
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest("login-wrong@example.com", "wrong-password"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A002"));
        }

        @Test
        @DisplayName("존재하지 않는 이메일이면 401 A002를 반환한다")
        void loginUnknownEmail() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest("no-such-user@example.com", "password123"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A002"));
        }
    }

    @Nested
    @DisplayName("refresh 로테이션")
    class Refresh {

        @Test
        @DisplayName("정상 refresh 시 새 토큰 쌍을 발급한다")
        void refreshRotation() throws Exception {
            signup("rotate@example.com", "password123", "로테유저");
            JsonNode tokens = login("rotate@example.com", "password123");

            refresh(tokens.get("refreshToken").asText())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty());
        }

        @Test
        @DisplayName("사용된 refresh 토큰 재사용 시 A004 + 해당 유저 전체 토큰 무효화")
        void refreshReuseDetection() throws Exception {
            signup("reuse@example.com", "password123", "재사용유저");
            JsonNode tokens1 = login("reuse@example.com", "password123");
            String oldRefresh = tokens1.get("refreshToken").asText();

            // 1차 로테이션 성공
            MvcResult rotated = refresh(oldRefresh)
                    .andExpect(status().isOk())
                    .andReturn();
            String newRefresh = objectMapper.readTree(rotated.getResponse().getContentAsString())
                    .get("refreshToken").asText();

            // 구 토큰 재사용 → A004
            refresh(oldRefresh)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A004"));

            // 재사용 감지로 신 토큰까지 전체 무효화 → A004
            refresh(newRefresh)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A004"));
        }

        @Test
        @DisplayName("만료된 refresh 토큰이면 401 A004를 반환한다 (A003은 access 만료 전용)")
        void refreshExpired() throws Exception {
            signup("expired-rt@example.com", "password123", "만료유저");
            Long userId = userRepository.findByEmail("expired-rt@example.com").orElseThrow().getId();

            String rawToken = "test-expired-refresh-token-raw-value";
            refreshTokenRepository.save(RefreshToken.builder()
                    .userId(userId)
                    .tokenHash(TokenHasher.sha256(rawToken))
                    .expiresAt(LocalDateTime.now().minusDays(1))
                    .build());

            refresh(rawToken)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A004"));
        }

        @Test
        @DisplayName("soft delete된 유저의 refresh 토큰은 401 A004 — 세션 연장 차단")
        void refreshAfterUserSoftDeleted() throws Exception {
            signup("deleted-user@example.com", "password123", "삭제유저");
            JsonNode tokens = login("deleted-user@example.com", "password123");

            userRepository.delete(userRepository.findByEmail("deleted-user@example.com").orElseThrow());

            refresh(tokens.get("refreshToken").asText())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A004"));
        }

        @Test
        @DisplayName("존재하지 않는 refresh 토큰이면 401 A004를 반환한다")
        void refreshUnknownToken() throws Exception {
            refresh("test-unknown-refresh-token")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A004"));
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("로그아웃 시 204, 이후 해당 refresh 토큰은 무효(A004)")
        void logoutRevokesRefreshToken() throws Exception {
            signup("logout@example.com", "password123", "로그아웃유저");
            JsonNode tokens = login("logout@example.com", "password123");
            String accessToken = tokens.get("accessToken").asText();
            String refreshToken = tokens.get("refreshToken").asText();

            mockMvc.perform(post("/api/auth/logout")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                    .andExpect(status().isNoContent());

            refresh(refreshToken)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A004"));
        }

        @Test
        @DisplayName("타 유저의 refresh 토큰으로 로그아웃해도 204 no-op — 남의 토큰은 무효화되지 않는다")
        void logoutWithOtherUsersRefreshTokenIsNoOp() throws Exception {
            signup("owner-a@example.com", "password123", "소유자A");
            signup("attacker-b@example.com", "password123", "유저B");
            JsonNode tokensA = login("owner-a@example.com", "password123");
            JsonNode tokensB = login("attacker-b@example.com", "password123");

            // B가 A의 refresh 토큰으로 로그아웃 시도 → 204 (존재 여부 비노출)
            mockMvc.perform(post("/api/auth/logout")
                            .header("Authorization", "Bearer " + tokensB.get("accessToken").asText())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RefreshRequest(tokensA.get("refreshToken").asText()))))
                    .andExpect(status().isNoContent());

            // A의 refresh 토큰은 여전히 유효
            refresh(tokensA.get("refreshToken").asText())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("무인증 로그아웃 요청은 401")
        void logoutWithoutToken() throws Exception {
            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest("any-token"))))
                    .andExpect(status().isUnauthorized());
        }
    }
}
