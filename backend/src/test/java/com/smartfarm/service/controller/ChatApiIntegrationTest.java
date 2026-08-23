package com.smartfarm.service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.ChatApiTestSupport;
import com.smartfarm.service.dto.ChatRequest;
import com.smartfarm.service.service.ChatConcurrencyGuard;
import com.smartfarm.service.service.ChatRateLimiter;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ChatApiIntegrationTest extends ChatApiTestSupport {

    @Autowired
    private ChatRateLimiter chatRateLimiter;

    @Autowired
    private ChatConcurrencyGuard chatConcurrencyGuard;

    // ── 정상 프록시 ───────────────────────────────────────────

    @Test
    @DisplayName("정상 질의 시 200 + answer·sources·fallback=false가 응답에 실리고 이력에 저장된다")
    void createChatSuccess() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "챗 농장");
        long userId = myUserId(token);
        String question = "토마토 잎에 반점이 생겼어요 " + UUID.randomUUID();
        enqueueChatOk("잎마름병일 수 있습니다. 환기를 강화하세요.", false);

        mockMvc.perform(post("/api/farms/" + farmId + "/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest(question))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.question").value(question))
                .andExpect(jsonPath("$.answer").value("잎마름병일 수 있습니다. 환기를 강화하세요."))
                .andExpect(jsonPath("$.sources[0]").value("농진청 토마토 재배 매뉴얼"))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.createdBy").value(userId))
                .andExpect(jsonPath("$.createdAt").exists());

        // 이력에도 반영되는지 확인
        mockMvc.perform(get("/api/farms/" + farmId + "/chat")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].question").value(question));
    }

    @Test
    @DisplayName("ai-server가 fallback=true+200을 주면 성공으로 수용해 저장·응답한다(처방과 동일 원칙)")
    void createChatFallbackAccepted() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "폴백 농장");
        enqueueChatOk("죄송합니다, 지금은 답변드리기 어려워요.", true);

        mockMvc.perform(post("/api/farms/" + farmId + "/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("폴백 질문"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.answer").value("죄송합니다, 지금은 답변드리기 어려워요."));
    }

    @Test
    @DisplayName("데모 계정도 챗 질의가 200으로 성공한다(체험 핵심 — 별도 차단 없음)")
    void createChatAsDemoAccountAllowed() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/demo-login"))
                .andExpect(status().isOk())
                .andReturn();
        String demoToken = readJson(loginResult).get("accessToken").asText();
        MvcResult farmsResult = mockMvc.perform(get("/api/farms")
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isOk())
                .andReturn();
        long demoFarmId = readJson(farmsResult).get(0).get("id").asLong();
        enqueueChatOk("데모 계정 답변입니다.", false);

        mockMvc.perform(post("/api/farms/" + demoFarmId + "/chat")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("데모 질문"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("데모 계정 답변입니다."));
    }

    // ── ai-server 오류 매핑 ───────────────────────────────────

    @Test
    @DisplayName("ai-server 429면 CH002(429)를 반환한다")
    void createChatTooManyRequestsMapsToCh002() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "혼잡 농장");
        enqueueChatTooManyRequests();

        mockMvc.perform(post("/api/farms/" + farmId + "/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("혼잡 질문"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("CH002"));
    }

    @Test
    @DisplayName("ai-server 5xx면 CH001(502)을 반환한다")
    void createChat5xxMapsToCh001() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "오류 농장");
        AI_SERVER.enqueue(new MockResponse().setResponseCode(500));

        mockMvc.perform(post("/api/farms/" + farmId + "/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("서버 오류 질문"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CH001"));
    }

    @Test
    @DisplayName("ai-server 응답 없음(타임아웃)이면 CH001(502)을 반환한다")
    void createChatTimeoutMapsToCh001() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "타임아웃 농장");
        AI_SERVER.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        mockMvc.perform(post("/api/farms/" + farmId + "/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("타임아웃 질문"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CH001"));
    }

    // ── 레이트리밋 ────────────────────────────────────────────

    @Test
    @DisplayName("농장당 분당 상한(10건) 초과 시 11번째 요청은 ai-server 호출 없이 CH002를 반환한다")
    void createChatOverRateLimitReturnsCh002() throws Exception {
        chatRateLimiter.resetState();
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "레이트리밋 농장");
        for (int i = 0; i < 10; i++) {
            enqueueChatOk("답변 " + i, false);
            createChat(token, farmId, "질문 " + i);
        }
        int before = AI_SERVER.getRequestCount();

        mockMvc.perform(post("/api/farms/" + farmId + "/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("상한 초과 질문"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("CH002"));
        assertThat(AI_SERVER.getRequestCount() - before).isZero();
        chatRateLimiter.resetState();
    }

    @Test
    @DisplayName("사용자 단위 분당 상한(10건) 초과 시 다른 농장으로 요청해도 CH002를 반환한다(농장 단위 우회 차단, 보안 리뷰 P1-B)")
    void createChatOverUserRateLimitAcrossFarmsReturnsCh002() throws Exception {
        chatRateLimiter.resetState();
        String token = signupAndLogin("농부-우회");
        long farmA = createFarm(token, "우회 농장A");
        long farmB = createFarm(token, "우회 농장B");
        for (int i = 0; i < 5; i++) {
            enqueueChatOk("답변A" + i, false);
            createChat(token, farmA, "질문A" + i);
        }
        for (int i = 0; i < 5; i++) {
            enqueueChatOk("답변B" + i, false);
            createChat(token, farmB, "질문B" + i);
        }
        // 농장 각각은 5건씩이라 농장 단위 상한(10)에는 걸리지 않는다 — 오직 사용자 단위 합산(10)만 초과.
        int before = AI_SERVER.getRequestCount();

        mockMvc.perform(post("/api/farms/" + farmA + "/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("우회 초과 질문"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("CH002"));
        assertThat(AI_SERVER.getRequestCount() - before).isZero();
        chatRateLimiter.resetState();
    }

    // ── 전역 동시성 가드(보안 리뷰 P1-A) ─────────────────────────

    @Test
    @DisplayName("전역 챗 동시성 가드(2슬롯) 포화 시 3번째 요청은 ai-server 호출 없이 즉시 CH002, 해제 후에는 정상 통과한다")
    void createChatOverGlobalConcurrencyReturnsCh002ImmediatelyThenRecovers() throws Exception {
        chatConcurrencyGuard.resetState();
        String tokenA = signupAndLogin("농부-동시성A");
        String tokenB = signupAndLogin("농부-동시성B");
        String tokenC = signupAndLogin("농부-동시성C");
        long farmA = createFarm(tokenA, "동시성 농장A");
        long farmB = createFarm(tokenB, "동시성 농장B");
        long farmC = createFarm(tokenC, "동시성 농장C");

        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch bothInFlight = new CountDownLatch(2);
        AI_SERVER.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                bothInFlight.countDown();
                release.await(10, TimeUnit.SECONDS);
                return new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setBody("""
                                {"answer": "동시성 답변", "sources": [], "fallback": false}
                                """);
            }
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() -> mockMvc.perform(
                            post("/api/farms/" + farmA + "/chat")
                                    .header("Authorization", "Bearer " + tokenA)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new ChatRequest("동시성 질문 A"))))
                    .andReturn());
            Future<MvcResult> second = executor.submit(() -> mockMvc.perform(
                            post("/api/farms/" + farmB + "/chat")
                                    .header("Authorization", "Bearer " + tokenB)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new ChatRequest("동시성 질문 B"))))
                    .andReturn());

            // 두 요청이 모두 ai-server 호출까지 진입해(=가드 슬롯 2개 모두 소진) 응답을 기다리는 상태를 만든다.
            assertThat(bothInFlight.await(5, TimeUnit.SECONDS)).isTrue();

            int before = AI_SERVER.getRequestCount();
            mockMvc.perform(post("/api/farms/" + farmC + "/chat")
                            .header("Authorization", "Bearer " + tokenC)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ChatRequest("동시성 질문 C"))))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("CH002"));
            // ai-server를 아예 호출하지 않고 즉시 거절됐는지 확인(가드는 ai-server 호출 전에 검사).
            assertThat(AI_SERVER.getRequestCount() - before).isZero();

            // 슬롯 해제 후에는 먼저 대기하던 두 요청이 정상적으로 완료된다.
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            assertThat(second.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        } finally {
            release.countDown();
            executor.shutdownNow();
            AI_SERVER.setDispatcher(new QueueDispatcher()); // 이후 테스트는 다시 enqueue 방식
            chatConcurrencyGuard.resetState();
        }
    }

    // ── cross-tenant ─────────────────────────────────────────

    @Test
    @DisplayName("cross-tenant: 미멤버가 챗 질의 시 403 F002, ai-server는 호출되지 않는다")
    void createChatAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 농장");
        int before = AI_SERVER.getRequestCount();

        mockMvc.perform(post("/api/farms/" + farmId + "/chat")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("남의 농장 질문"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
        assertThat(AI_SERVER.getRequestCount() - before).isZero();
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 챗 이력 조회 시 403 F002를 반환한다")
    void findChatMessagesAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 농장 둘");

        mockMvc.perform(get("/api/farms/" + farmId + "/chat")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    // ── 입력 검증 ─────────────────────────────────────────────

    @Test
    @DisplayName("질문이 공백이면 400 C001을 반환한다(ai-server 무호출)")
    void createChatBlankQuestion() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "검증 농장");
        int before = AI_SERVER.getRequestCount();

        mockMvc.perform(post("/api/farms/" + farmId + "/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        assertThat(AI_SERVER.getRequestCount() - before).isZero();
    }

    @Test
    @DisplayName("질문이 500자를 초과하면 400 C001을 반환한다")
    void createChatQuestionTooLong() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "검증 농장 둘");

        mockMvc.perform(post("/api/farms/" + farmId + "/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("가".repeat(501)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    // ── 이력 목록(페이지네이션·최신순) ───────────────────────────

    @Test
    @DisplayName("챗 이력은 최신순 PageResponse이며 필드가 정확하다")
    void findChatMessagesPagination() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "이력 농장");
        enqueueChatOk("답변 하나", false);
        long first = createChat(token, farmId, "질문 하나");
        enqueueChatOk("답변 둘", false);
        long second = createChat(token, farmId, "질문 둘");
        enqueueChatOk("답변 셋", false);
        long third = createChat(token, farmId, "질문 셋");

        mockMvc.perform(get("/api/farms/" + farmId + "/chat")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/farms/" + farmId + "/chat")
                        .param("page", "1")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first));
    }
}
