package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.FarmRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 농장 디스코드 웹훅 설정/해제(PATCH /api/farms/{farmId}/webhook, 이슈 #21) 통합 테스트.
 * 실제 발송 검증(MockWebServer)은 {@link com.smartfarm.service.service.PrescriptionWebhookNotificationTest}.
 */
class FarmWebhookApiIntegrationTest extends FarmTestSupport {

    private static final String VALID_WEBHOOK_URL =
            "https://discord.com/api/webhooks/123456789012345678/AbCdEfGhIjKlMnOpQrStUvWxYz";

    @Test
    @DisplayName("OWNER는 웹훅을 설정할 수 있고 응답엔 URL 원문 없이 webhookConfigured=true만 노출된다")
    void ownerSetsWebhook() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmId = createFarm(ownerToken, "웹훅 농장");

        mockMvc.perform(patch("/api/farms/" + farmId + "/webhook")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookUrl\":\"" + VALID_WEBHOOK_URL + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webhookConfigured").value(true))
                .andExpect(jsonPath("$.webhookUrl").doesNotExist());
    }

    @Test
    @DisplayName("null 전달 시 웹훅이 해제되고 webhookConfigured=false가 된다")
    void ownerClearsWebhook() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmId = createFarm(ownerToken, "웹훅 해제 농장");
        mockMvc.perform(patch("/api/farms/" + farmId + "/webhook")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookUrl\":\"" + VALID_WEBHOOK_URL + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webhookConfigured").value(true));

        mockMvc.perform(patch("/api/farms/" + farmId + "/webhook")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookUrl\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webhookConfigured").value(false));
    }

    @Test
    @DisplayName("디스코드 웹훅이 아닌 임의 URL은 400 C001을 반환한다 (SSRF 차단)")
    void rejectsNonDiscordUrl() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmId = createFarm(ownerToken, "검증 농장");

        mockMvc.perform(patch("/api/farms/" + farmId + "/webhook")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookUrl\":\"https://evil.example.com/steal\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("http(비TLS) discord.com 웹훅도 400 C001을 반환한다 (https 고정)")
    void rejectsNonHttpsDiscordUrl() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmId = createFarm(ownerToken, "검증 농장2");

        mockMvc.perform(patch("/api/farms/" + farmId + "/webhook")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookUrl\":\"http://discord.com/api/webhooks/1/token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("MEMBER가 웹훅 설정 시 403 F003을 반환한다")
    void memberCannotSetWebhook() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String memberToken = signupAndLogin("일꾼이");
        long farmId = createFarm(ownerToken, "권한 농장");
        joinFarmAs(ownerToken, farmId, memberToken, FarmRole.OPERATOR);

        mockMvc.perform(patch("/api/farms/" + farmId + "/webhook")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookUrl\":\"" + VALID_WEBHOOK_URL + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 타 농장 웹훅 설정 시 403 F002를 반환한다")
    void nonMemberCannotSetWebhook() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 농장");

        mockMvc.perform(patch("/api/farms/" + farmId + "/webhook")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookUrl\":\"" + VALID_WEBHOOK_URL + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }
}
