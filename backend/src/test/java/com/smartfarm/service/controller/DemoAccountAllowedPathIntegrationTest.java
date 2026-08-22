package com.smartfarm.service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.PrescriptionApiTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 데모 계정 허용 경로 회귀 그물 (#49 code-reviewer P2-3) — 차단 가드가 체험 핵심(조회·진단·처방)을
 * 오차단하지 않는지 검증한다(contract §4.5 "허용: 전체 조회 + 진단 업로드 + 처방 생성").
 * ai-server는 기존 MockWebServer 인프라({@link PrescriptionApiTestSupport}) 재사용.
 */
class DemoAccountAllowedPathIntegrationTest extends PrescriptionApiTestSupport {

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private String demoAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/demo-login"))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("accessToken").asText();
    }

    private long demoFarmId(String demoToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/farms")
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get(0).get("id").asLong();
    }

    private MockMultipartFile leafImage() {
        byte[] body = "demo-fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[JPEG_SIGNATURE.length + body.length];
        System.arraycopy(JPEG_SIGNATURE, 0, bytes, 0, JPEG_SIGNATURE.length);
        System.arraycopy(body, 0, bytes, JPEG_SIGNATURE.length, body.length);
        return new MockMultipartFile("file", "demo-leaf.jpg", "image/jpeg", bytes);
    }

    @Test
    @DisplayName("데모 계정으로 진단 업로드가 201로 성공한다(체험 핵심 — 가드 오차단 없음)")
    void demoDiagnosisUploadAllowed() throws Exception {
        String token = demoAccessToken();
        long farmId = demoFarmId(token);
        enqueueOkResponse();

        mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(leafImage())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.label").value("early_blight"));
    }

    @Test
    @DisplayName("데모 계정으로 처방 생성이 접수(202)되고 COMPLETED까지 전이된다(체험 핵심)")
    void demoPrescriptionAllowed() throws Exception {
        String token = demoAccessToken();
        long farmId = demoFarmId(token);
        enqueuePrescriptionOk();

        String marker = "데모 처방 질문 " + UUID.randomUUID();
        long prescriptionId = createPrescription(token, farmId, marker, null); // 202는 헬퍼가 검증

        JsonNode terminal = awaitPrescriptionTerminal(token, farmId, prescriptionId);
        assertThat(terminal.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(terminal.get("question").asText()).isEqualTo(marker);
    }
}
