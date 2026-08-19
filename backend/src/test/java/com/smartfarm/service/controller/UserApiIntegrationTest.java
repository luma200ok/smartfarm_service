package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.config.JwtProperties;
import com.smartfarm.service.config.JwtTokenProvider;
import com.smartfarm.service.dto.LoginRequest;
import com.smartfarm.service.dto.SignupRequest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class UserApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    JwtProperties jwtProperties;

    String signupAndGetAccessToken(String email) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(email, "password123", "미유저"))))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokens = objectMapper.readTree(result.getResponse().getContentAsString());
        return tokens.get("accessToken").asText();
    }

    @Test
    @DisplayName("유효한 토큰으로 /api/users/me 조회 시 200 UserResponse를 반환한다")
    void meSuccess() throws Exception {
        String accessToken = signupAndGetAccessToken("me-ok@example.com");

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("me-ok@example.com"))
                .andExpect(jsonPath("$.nickname").value("미유저"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("무인증 접근 시 401과 {timestamp, code, message} 형식으로 응답한다")
    void meWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A004"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("만료된 액세스 토큰이면 401 A003을 반환한다")
    void meWithExpiredToken() throws Exception {
        signupAndGetAccessToken("me-expired@example.com");
        // 동일 시크릿·음수 유효기간으로 이미 만료된 토큰 생성
        JwtTokenProvider expiredProvider = new JwtTokenProvider(new JwtProperties(
                jwtProperties.secret(), Duration.ofSeconds(-10), jwtProperties.refreshTokenValidity()));
        String expiredToken = expiredProvider.createAccessToken(1L);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A003"));
    }

    @Test
    @DisplayName("인증된 요청의 미존재 경로는 404 C003을 반환한다")
    void unknownPathReturnsC003() throws Exception {
        String accessToken = signupAndGetAccessToken("c003@example.com");
        mockMvc.perform(get("/api/no-such-path")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("C003"));
    }

    @Test
    @DisplayName("허용되지 않는 메서드는 405 C004를 반환한다")
    void methodNotAllowedReturnsC004() throws Exception {
        mockMvc.perform(get("/api/auth/signup"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("C004"));
    }

    @Test
    @DisplayName("변조된 토큰이면 401 A004를 반환한다")
    void meWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A004"));
    }
}
