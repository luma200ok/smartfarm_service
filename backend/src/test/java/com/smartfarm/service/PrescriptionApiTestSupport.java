package com.smartfarm.service;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.dto.PrescriptionRequest;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 처방(Prescription) 통합 테스트 공통 지원 — {@link DiagnosisApiTestSupport}의 MockWebServer
 * (ai-server)를 그대로 공유해 Spring 컨텍스트 재기동 없이 처방 엔드포인트도 모킹한다.
 * 비동기 job이라 폴링 헬퍼({@link #awaitPrescriptionTerminal})로 종료 상태를 관측한다.
 */
public abstract class PrescriptionApiTestSupport extends DiagnosisApiTestSupport {

    /** ai-server 실스키마(한글 키 — smartfarm_ai src/llm/prescribe.py Prescription, contract §3). */
    protected static final String PRESCRIPTION_OK_BODY = """
            {
              "진단요약": "잎곰팡이병으로 보입니다(신뢰도 87%)",
              "원인": "잎곰팡이병은 습한 환경에서 곰팡이가 번져 생깁니다",
              "즉시조치": "감염된 잎을 제거하고 환기를 강화하세요",
              "예방": "물주기는 아침에 하고 잎이 젖지 않게 관리하세요",
              "재촬영시점": "3일 뒤 같은 잎을 밝은 곳에서 다시 찍어 확인하세요",
              "근거출처": ["농진청 토마토 재배 매뉴얼"]
            }
            """;

    protected void enqueuePrescriptionOk() {
        AI_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(PRESCRIPTION_OK_BODY));
    }

    protected void enqueueTooManyRequests() {
        AI_SERVER.enqueue(new MockResponse().setResponseCode(429));
    }

    /** POST 접수 — 202 + PENDING 응답을 검증하고 prescriptionId를 돌려준다. */
    protected long createPrescription(String token, long farmId, String question, Long diagnosisId)
            throws Exception {
        MvcResult result = mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/farms/" + farmId + "/prescriptions")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new PrescriptionRequest(question, diagnosisId))))
                .andExpect(status().isAccepted())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    /** 폴링(계약: 프론트 2~3초 간격) — 테스트는 50ms 간격으로 종료 상태(COMPLETED/FAILED)까지 관측한다. */
    protected JsonNode awaitPrescriptionTerminal(String token, long farmId, long prescriptionId)
            throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            MvcResult result = mockMvc.perform(
                            MockMvcRequestBuilders.get(
                                            "/api/farms/" + farmId + "/prescriptions/" + prescriptionId)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode node = readJson(result);
            String status = node.get("status").asText();
            if (!"PENDING".equals(status) && !"PROCESSING".equals(status)) {
                return node;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("처방이 제한 시간 내 종료 상태로 전이되지 않음: " + node);
            }
            Thread.sleep(50);
        }
    }

    /**
     * ai-server가 받은 처방 요청 중 marker(테스트별 유니크 질문)가 포함된 것을 찾는다.
     * 공유 MockWebServer라 이전 테스트가 남긴 기록이 섞여 있을 수 있어 매칭될 때까지 드레인한다.
     */
    protected String takePrescriptionRequestBody(String marker) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            RecordedRequest request = AI_SERVER.takeRequest(500, TimeUnit.MILLISECONDS);
            if (request == null) {
                continue;
            }
            String body = request.getBody().readUtf8();
            if ("/api/prescriptions".equals(request.getPath()) && body.contains(marker)) {
                return body;
            }
        }
        throw new AssertionError("ai-server에서 처방 요청을 관측하지 못함: marker=" + marker);
    }
}
