package com.smartfarm.service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.PrescriptionApiTestSupport;
import com.smartfarm.service.dto.PrescriptionRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class PrescriptionApiIntegrationTest extends PrescriptionApiTestSupport {

    // ── 상태 전이 전체 (202 접수 → 폴링 → COMPLETED) ──────────

    @Test
    @DisplayName("처방 접수 시 202 PENDING 즉시 응답, 폴링으로 COMPLETED와 구조화 result를 관측한다")
    void createPrescriptionCompletesViaPolling() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "처방 농장");
        enqueuePrescriptionOk();
        String question = "잎이 노랗게 변해요 " + UUID.randomUUID();

        MvcResult accepted = mockMvc.perform(post("/api/farms/" + farmId + "/prescriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PrescriptionRequest(question, null))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.question").value(question))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.createdBy").isNumber())
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();
        long prescriptionId = readJson(accepted).get("id").asLong();

        JsonNode done = awaitPrescriptionTerminal(token, farmId, prescriptionId);
        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        // 한글 키 → contract §4 영문 스키마 매핑 검증(summary←진단요약, actions←즉시조치+예방, caution←원인/재촬영시점, sources←근거출처)
        assertThat(done.get("result").get("summary").asText()).contains("잎곰팡이병");
        assertThat(done.get("result").get("actions").get(0).asText()).isEqualTo("감염된 잎을 제거하고 환기를 강화하세요");
        assertThat(done.get("result").get("actions").get(1).asText()).contains("물주기는 아침에");
        assertThat(done.get("result").get("caution").asText()).contains("원인: 잎곰팡이병은 습한 환경");
        assertThat(done.get("result").get("caution").asText()).contains("재촬영 시점: 3일 뒤");
        assertThat(done.get("result").get("sources").get(0).asText()).contains("농진청");
        assertThat(done.get("errorCode").isNull()).isTrue();
        assertThat(done.get("completedAt").asText()).isNotBlank();

        String requestBody = takePrescriptionRequestBody(question);
        assertThat(requestBody).contains("name=\"question\"");
        assertThat(requestBody).doesNotContain("name=\"diagnosis\""); // diagnosisId 없으면 미전달
    }

    @Test
    @DisplayName("diagnosisId 지정 시 저장된 진단이 snake_case JSON으로 ai-server diagnosis 필드에 전달된다")
    void createPrescriptionForwardsStoredDiagnosisJson() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "진단 연계 농장");
        enqueueOkResponse();
        long diagnosisId = createDiagnosisViaApi(token, farmId);
        enqueuePrescriptionOk();
        String question = "이 진단 결과에 맞는 처방은? " + UUID.randomUUID();

        long prescriptionId = createPrescription(token, farmId, question, diagnosisId);
        JsonNode done = awaitPrescriptionTerminal(token, farmId, prescriptionId);
        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.get("diagnosisId").asLong()).isEqualTo(diagnosisId);

        String requestBody = takePrescriptionRequestBody(question);
        assertThat(requestBody).contains("name=\"diagnosis\"");
        assertThat(requestBody).contains("early_blight");
        assertThat(requestBody).contains("\"ood_blocked\":false");
        assertThat(requestBody).doesNotContain("aGVsbG8="); // cam_png_base64 제외(프롬프트 비대화 방지)
    }

    // ── 429 백오프 재시도 ─────────────────────────────────────

    @Test
    @DisplayName("ai-server 429 2회 후 성공하면 재시도로 COMPLETED가 된다(총 3회 호출)")
    void retryOn429ThenSucceeds() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "재시도 농장");
        int before = AI_SERVER.getRequestCount();
        enqueueTooManyRequests();
        enqueueTooManyRequests();
        enqueuePrescriptionOk();

        long prescriptionId = createPrescription(token, farmId, "혼잡 재시도 질문", null);
        JsonNode done = awaitPrescriptionTerminal(token, farmId, prescriptionId);

        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.get("result").get("summary").asText()).isNotBlank();
        assertThat(AI_SERVER.getRequestCount() - before).isEqualTo(3);
    }

    @Test
    @DisplayName("ai-server 429 3회(재시도 소진)면 FAILED에 errorCode P003이 기록된다")
    void retryOn429ExhaustedFails() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "혼잡 농장");
        int before = AI_SERVER.getRequestCount();
        enqueueTooManyRequests();
        enqueueTooManyRequests();
        enqueueTooManyRequests();

        long prescriptionId = createPrescription(token, farmId, "혼잡 소진 질문", null);
        JsonNode done = awaitPrescriptionTerminal(token, farmId, prescriptionId);

        assertThat(done.get("status").asText()).isEqualTo("FAILED");
        assertThat(done.get("errorCode").asText()).isEqualTo("P003");
        assertThat(done.get("result").isNull()).isTrue();
        assertThat(done.get("completedAt").asText()).isNotBlank();
        assertThat(AI_SERVER.getRequestCount() - before).isEqualTo(3);
    }

    // ── 5xx/타임아웃 — 재시도 없이 FAILED(P002) ────────────────

    @Test
    @DisplayName("ai-server 5xx면 재시도 없이(호출 1회) FAILED P002가 기록된다")
    void aiServer5xxFailsWithoutRetry() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "오류 농장");
        int before = AI_SERVER.getRequestCount();
        AI_SERVER.enqueue(new MockResponse().setResponseCode(500));

        long prescriptionId = createPrescription(token, farmId, "서버 오류 질문", null);
        JsonNode done = awaitPrescriptionTerminal(token, farmId, prescriptionId);

        assertThat(done.get("status").asText()).isEqualTo("FAILED");
        assertThat(done.get("errorCode").asText()).isEqualTo("P002");
        assertThat(AI_SERVER.getRequestCount() - before).isEqualTo(1);
    }

    @Test
    @DisplayName("ai-server 응답 없음(타임아웃)이면 FAILED P002가 기록된다")
    void aiServerTimeoutFails() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "타임아웃 농장");
        AI_SERVER.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        long prescriptionId = createPrescription(token, farmId, "타임아웃 질문", null);
        JsonNode done = awaitPrescriptionTerminal(token, farmId, prescriptionId);

        assertThat(done.get("status").asText()).isEqualTo("FAILED");
        assertThat(done.get("errorCode").asText()).isEqualTo("P002");
    }

    // ── 응답 스키마 매핑 (한글 키 → contract 영문 result) ────────

    @Test
    @DisplayName("미매핑 응답(영문 키 등 이상 스키마)은 빈 COMPLETED 대신 FAILED P002가 된다(빈 성공 금지)")
    void unmappedSchemaFailsInsteadOfEmptyCompleted() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "미매핑 농장");
        // 구(영문) 스키마 — 한글 키 필드가 전부 null로 역직렬화되는 fail-open 상황 재현
        AI_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"summary": "english schema", "actions": ["a"], "caution": "c", "sources": ["s"]}
                        """));

        long prescriptionId = createPrescription(token, farmId, "미매핑 응답 질문", null);
        JsonNode done = awaitPrescriptionTerminal(token, farmId, prescriptionId);

        assertThat(done.get("status").asText()).isEqualTo("FAILED");
        assertThat(done.get("errorCode").asText()).isEqualTo("P002");
        assertThat(done.get("result").isNull()).isTrue();
    }

    @Test
    @DisplayName("필드별 크기 캡 — summary 2,000자·목록 20개·원소 500자로 절단된다")
    void oversizedFieldsAreCapped() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "캡 농장");
        String bigSummary = "가".repeat(2_500);
        String bigSource = "출".repeat(600);
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("진단요약", bigSummary);
        body.put("근거출처", java.util.Collections.nCopies(25, bigSource));
        AI_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(objectMapper.writeValueAsString(body)));

        long prescriptionId = createPrescription(token, farmId, "크기 캡 질문", null);
        JsonNode done = awaitPrescriptionTerminal(token, farmId, prescriptionId);

        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.get("result").get("summary").asText()).hasSize(2_000);
        assertThat(done.get("result").get("sources")).hasSize(20);
        assertThat(done.get("result").get("sources").get(0).asText()).hasSize(500);
        assertThat(done.get("result").get("actions")).isEmpty(); // 즉시조치·예방 부재 → 빈 목록
        assertThat(done.get("result").get("caution").isNull()).isTrue(); // 원인·재촬영시점 부재 → null
    }

    // ── 직렬화 (단일 워커 — 동시 접수도 순차 처리) ──────────────

    @Test
    @DisplayName("동시 접수 3건이 단일 워커로 순차 처리된다(ai-server 동시 in-flight 최대 1)")
    void concurrentSubmissionsAreSerialized() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "직렬화 농장");

        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        AI_SERVER.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                int current = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(current, Math::max);
                try {
                    // 처리 시간 창을 벌려 워커가 2개 이상이면 반드시 겹치게 만든다
                    Thread.sleep(300);
                } finally {
                    inFlight.decrementAndGet();
                }
                return new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setBody(PRESCRIPTION_OK_BODY);
            }
        });
        try {
            long first = createPrescription(token, farmId, "직렬화 질문 1", null);
            long second = createPrescription(token, farmId, "직렬화 질문 2", null);
            long third = createPrescription(token, farmId, "직렬화 질문 3", null);

            assertThat(awaitPrescriptionTerminal(token, farmId, first).get("status").asText())
                    .isEqualTo("COMPLETED");
            assertThat(awaitPrescriptionTerminal(token, farmId, second).get("status").asText())
                    .isEqualTo("COMPLETED");
            assertThat(awaitPrescriptionTerminal(token, farmId, third).get("status").asText())
                    .isEqualTo("COMPLETED");
            assertThat(maxInFlight.get()).isEqualTo(1);
        } finally {
            AI_SERVER.setDispatcher(new QueueDispatcher()); // 이후 테스트는 다시 enqueue 방식
        }
    }

    // ── 접수 상한 (농장당 진행 중 3건 → P004) ───────────────────

    @Test
    @DisplayName("농장당 진행 중(PENDING+PROCESSING) 3건 상태에서 추가 접수는 429 P004로 거절된다")
    void createPrescriptionOverActiveCapReturnsP004() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "상한 농장");

        CountDownLatch release = new CountDownLatch(1);
        AI_SERVER.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                // 응답을 붙잡아 3건이 전부 진행 중(PENDING/PROCESSING) 상태를 유지하게 한다
                release.await(10, TimeUnit.SECONDS);
                return new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setBody(PRESCRIPTION_OK_BODY);
            }
        });
        try {
            long first = createPrescription(token, farmId, "상한 질문 1", null);
            long second = createPrescription(token, farmId, "상한 질문 2", null);
            long third = createPrescription(token, farmId, "상한 질문 3", null);

            mockMvc.perform(post("/api/farms/" + farmId + "/prescriptions")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new PrescriptionRequest("상한 초과 질문", null))))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("P004"));

            release.countDown();
            assertThat(awaitPrescriptionTerminal(token, farmId, first).get("status").asText())
                    .isEqualTo("COMPLETED");
            assertThat(awaitPrescriptionTerminal(token, farmId, second).get("status").asText())
                    .isEqualTo("COMPLETED");
            assertThat(awaitPrescriptionTerminal(token, farmId, third).get("status").asText())
                    .isEqualTo("COMPLETED");
        } finally {
            release.countDown();
            AI_SERVER.setDispatcher(new QueueDispatcher()); // enqueue는 QueueDispatcher 전제 — 복원 후 사용
        }

        // 진행 중이 모두 끝나면 다시 접수 가능(상한은 진행 중 건수 기준)
        enqueuePrescriptionOk();
        long fourth = createPrescription(token, farmId, "상한 해제 질문", null);
        assertThat(awaitPrescriptionTerminal(token, farmId, fourth).get("status").asText())
                .isEqualTo("COMPLETED");
    }

    // ── cross-tenant ─────────────────────────────────────────

    @Test
    @DisplayName("cross-tenant: 미멤버가 처방 접수 시 403 F002, job은 생성되지 않는다(ai-server 무호출)")
    void createPrescriptionAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 농장");
        int before = AI_SERVER.getRequestCount();

        mockMvc.perform(post("/api/farms/" + farmId + "/prescriptions")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PrescriptionRequest("남의 농장 질문", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
        assertThat(AI_SERVER.getRequestCount() - before).isZero();
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 diagnosisId 참조 접수는 404 D001을 반환한다(농장 스코프)")
    void createPrescriptionWithCrossTenantDiagnosis() throws Exception {
        String ownerAToken = signupAndLogin("농장주A");
        String ownerBToken = signupAndLogin("농장주B");
        long farmA = createFarm(ownerAToken, "농장 A");
        long farmB = createFarm(ownerBToken, "농장 B");
        enqueueOkResponse();
        long diagnosisIdOfA = createDiagnosisViaApi(ownerAToken, farmA);

        mockMvc.perform(post("/api/farms/" + farmB + "/prescriptions")
                        .header("Authorization", "Bearer " + ownerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PrescriptionRequest("타 농장 진단 참조", diagnosisIdOfA))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("D001"));
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 prescriptionId 상세 조회는 404 P001을 반환한다(농장 스코프)")
    void findPrescriptionCrossTenant() throws Exception {
        String ownerAToken = signupAndLogin("농장주A");
        String ownerBToken = signupAndLogin("농장주B");
        long farmA = createFarm(ownerAToken, "농장 A둘");
        long farmB = createFarm(ownerBToken, "농장 B둘");
        enqueuePrescriptionOk();
        long prescriptionId = createPrescription(ownerAToken, farmA, "A 농장 질문", null);
        awaitPrescriptionTerminal(ownerAToken, farmA, prescriptionId);

        mockMvc.perform(get("/api/farms/" + farmB + "/prescriptions/" + prescriptionId)
                        .header("Authorization", "Bearer " + ownerBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 처방 목록 조회 시 403 F002를 반환한다")
    void findPrescriptionsAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 농장 둘");

        mockMvc.perform(get("/api/farms/" + farmId + "/prescriptions")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("존재하지 않는 prescriptionId는 404 P001을 반환한다")
    void findPrescriptionNotFound() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "미존재 농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/prescriptions/999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    // ── 요청 검증 ─────────────────────────────────────────────

    @Test
    @DisplayName("질문이 공백이면 400 C001을 반환한다(ai-server 무호출)")
    void createPrescriptionBlankQuestion() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "검증 농장");
        int before = AI_SERVER.getRequestCount();

        mockMvc.perform(post("/api/farms/" + farmId + "/prescriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PrescriptionRequest("   ", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        assertThat(AI_SERVER.getRequestCount() - before).isZero();
    }

    @Test
    @DisplayName("질문이 500자를 초과하면 400 C001을 반환한다")
    void createPrescriptionQuestionTooLong() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "검증 농장 둘");

        mockMvc.perform(post("/api/farms/" + farmId + "/prescriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PrescriptionRequest("가".repeat(501), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("같은 농장이라도 존재하지 않는 diagnosisId면 404 D001을 반환한다")
    void createPrescriptionUnknownDiagnosis() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "검증 농장 셋");

        mockMvc.perform(post("/api/farms/" + farmId + "/prescriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PrescriptionRequest("미존재 진단 참조", 999999999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("D001"));
    }

    // ── 목록 (페이지네이션·경량 Summary) ───────────────────────

    @Test
    @DisplayName("처방 목록은 최신순 PageResponse이며 Summary에는 result 본문이 없다")
    void findPrescriptionsPagination() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "이력 농장");
        enqueuePrescriptionOk();
        long first = createPrescription(token, farmId, "질문 하나", null);
        awaitPrescriptionTerminal(token, farmId, first);
        enqueuePrescriptionOk();
        long second = createPrescription(token, farmId, "질문 둘", null);
        awaitPrescriptionTerminal(token, farmId, second);
        enqueuePrescriptionOk();
        long third = createPrescription(token, farmId, "질문 셋", null);
        awaitPrescriptionTerminal(token, farmId, third);

        mockMvc.perform(get("/api/farms/" + farmId + "/prescriptions")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].question").value("질문 셋"))
                .andExpect(jsonPath("$.content[0].completedAt").exists())
                .andExpect(jsonPath("$.content[0].result").doesNotExist())
                .andExpect(jsonPath("$.content[0].errorCode").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/farms/" + farmId + "/prescriptions")
                        .param("page", "1")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first));
    }

    private long createDiagnosisViaApi(String token, long farmId) throws Exception {
        MockMultipartFile leafImage = new MockMultipartFile("file", "leaf.jpg", "image/jpeg",
                "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(leafImage)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }
}
