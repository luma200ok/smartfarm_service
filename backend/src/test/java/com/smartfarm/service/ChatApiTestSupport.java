package com.smartfarm.service;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.dto.ChatRequest;
import okhttp3.mockwebserver.MockResponse;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 챗(Chat) 통합 테스트 공통 지원 — {@link DiagnosisApiTestSupport}의 MockWebServer(ai-server)를
 * 그대로 공유해 Spring 컨텍스트 재기동 없이 챗 엔드포인트도 모킹한다(PrescriptionApiTestSupport와
 * 동일 패턴). 챗은 동기 200 응답이라 처방처럼 폴링 헬퍼가 필요 없다.
 */
public abstract class ChatApiTestSupport extends DiagnosisApiTestSupport {

    protected void enqueueChatOk(String answer, boolean fallback) {
        AI_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"answer": "%s", "sources": ["농진청 토마토 재배 매뉴얼"], "fallback": %s}
                        """.formatted(answer, fallback)));
    }

    protected void enqueueChatTooManyRequests() {
        AI_SERVER.enqueue(new MockResponse().setResponseCode(429));
    }

    /** 정상 200을 기대하는 호출 — id를 돌려준다. */
    protected long createChat(String token, long farmId, String question) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/farms/" + farmId + "/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest(question))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("id").asLong();
    }
}
