package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.PrescriptionApiTestSupport;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.repository.FarmRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 처방 완료/실패 디스코드 웹훅 발송(이슈 #21, contract §3 Phase 3) 통합 검증.
 *
 * <p>PATCH 검증(discord.com 프리픽스 고정)은 별도로({@link com.smartfarm.service.controller.FarmWebhookApiIntegrationTest})
 * 확인하므로, 여기서는 실제 발송 대상인 discord.com을 MockWebServer로 대체할 수 없어 <b>PATCH를
 * 우회하고 repository로 직접 웹훅 URL을 주입</b>한다(handoff 재량 — 발송 로직 자체는 저장된 URL의
 * 출처를 검증하지 않으며, 프리픽스 검증은 쓰기 시점 DTO 한 곳에서만 책임진다).
 */
class PrescriptionWebhookNotificationTest extends PrescriptionApiTestSupport {

    private static final MockWebServer WEBHOOK_SERVER = new MockWebServer();

    static {
        try {
            WEBHOOK_SERVER.start();
        } catch (IOException e) {
            throw new IllegalStateException("MockWebServer(webhook) 기동 실패", e);
        }
    }

    /** 발송 타임아웃 테스트를 빠르게 돌리기 위해 5s(운영값) 대신 짧게 override. */
    @DynamicPropertySource
    static void webhookProperties(DynamicPropertyRegistry registry) {
        registry.add("webhook.timeout", () -> "1s");
    }

    @Autowired
    private FarmRepository farmRepository;

    private void setWebhookUrl(long farmId, String path) {
        Farm farm = farmRepository.findById(farmId).orElseThrow();
        farm.updateWebhookUrl("http://localhost:" + WEBHOOK_SERVER.getPort() + path);
        farmRepository.save(farm);
    }

    private RecordedRequest takeWebhookRequest() throws InterruptedException {
        RecordedRequest request = WEBHOOK_SERVER.takeRequest(3, TimeUnit.SECONDS);
        assertThat(request).as("웹훅 서버가 요청을 받지 못함").isNotNull();
        return request;
    }

    @Test
    @DisplayName("처방 COMPLETED 시 농장명·상세 링크가 담긴 웹훅이 발송된다")
    void completedSendsWebhook() throws Exception {
        String token = signupAndLogin("웹훅농부1");
        long farmId = createFarm(token, "완료 웹훅 농장");
        setWebhookUrl(farmId, "/hooks/completed");
        String marker = "웹훅완료질문-" + System.nanoTime();
        enqueuePrescriptionOk();

        long id = createPrescription(token, farmId, marker, null);
        JsonNode result = awaitPrescriptionTerminal(token, farmId, id);
        assertThat(result.get("status").asText()).isEqualTo("COMPLETED");

        RecordedRequest webhookRequest = takeWebhookRequest();
        assertThat(webhookRequest.getPath()).isEqualTo("/hooks/completed");
        String body = webhookRequest.getBody().readUtf8();
        assertThat(body).contains("완료 웹훅 농장");
        assertThat(body).contains("https://farm.luma200ok.com/farms/" + farmId + "/prescriptions/" + id);
    }

    @Test
    @DisplayName("처방 FAILED 시에도 농장명·상세 링크가 담긴 웹훅이 발송된다")
    void failedSendsWebhook() throws Exception {
        String token = signupAndLogin("웹훅농부2");
        long farmId = createFarm(token, "실패 웹훅 농장");
        setWebhookUrl(farmId, "/hooks/failed");
        String marker = "웹훅실패질문-" + System.nanoTime();
        AI_SERVER.enqueue(new MockResponse().setResponseCode(500)); // 재시도 없이 즉시 P002 FAILED

        long id = createPrescription(token, farmId, marker, null);
        JsonNode result = awaitPrescriptionTerminal(token, farmId, id);
        assertThat(result.get("status").asText()).isEqualTo("FAILED");

        RecordedRequest webhookRequest = takeWebhookRequest();
        assertThat(webhookRequest.getPath()).isEqualTo("/hooks/failed");
        String body = webhookRequest.getBody().readUtf8();
        assertThat(body).contains("실패 웹훅 농장");
        assertThat(body).contains("https://farm.luma200ok.com/farms/" + farmId + "/prescriptions/" + id);
    }

    @Test
    @DisplayName("웹훅 미설정 농장은 발송을 스킵한다")
    void skipsWhenWebhookNotConfigured() throws Exception {
        String token = signupAndLogin("웹훅농부3");
        long farmId = createFarm(token, "미설정 농장"); // setWebhookUrl 호출 없음
        enqueuePrescriptionOk();
        int before = WEBHOOK_SERVER.getRequestCount();

        long id = createPrescription(token, farmId, "미설정질문", null);
        JsonNode result = awaitPrescriptionTerminal(token, farmId, id);
        assertThat(result.get("status").asText()).isEqualTo("COMPLETED");

        // 발송이 있었다면 이 여유 시간 안에 WEBHOOK_SERVER에 도달했을 것 — 요청 수 불변으로 스킵을 확인한다
        // (클래스 내 테스트는 JUnit5 기본 순차 실행이라 다른 테스트와 카운트가 섞이지 않는다).
        Thread.sleep(300);
        assertThat(WEBHOOK_SERVER.getRequestCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("웹훅 발송이 5xx로 실패해도 처방 상태(COMPLETED)는 영향받지 않는다")
    void webhookFailureDoesNotAffectPrescriptionStatus() throws Exception {
        String token = signupAndLogin("웹훅농부4");
        long farmId = createFarm(token, "5xx 웹훅 농장");
        setWebhookUrl(farmId, "/hooks/broken");
        WEBHOOK_SERVER.enqueue(new MockResponse().setResponseCode(500));
        enqueuePrescriptionOk();

        long id = createPrescription(token, farmId, "5xx웹훅질문", null);
        JsonNode result = awaitPrescriptionTerminal(token, farmId, id);

        assertThat(result.get("status").asText()).isEqualTo("COMPLETED");
        takeWebhookRequest(); // 발송 시도 자체는 있었음(5xx라도 요청은 도달)
    }

    @Test
    @DisplayName("웹훅 발송이 타임아웃으로 실패해도 처방 상태(COMPLETED)는 영향받지 않는다")
    void webhookTimeoutDoesNotAffectPrescriptionStatus() throws Exception {
        String token = signupAndLogin("웹훅농부5");
        long farmId = createFarm(token, "타임아웃 웹훅 농장");
        setWebhookUrl(farmId, "/hooks/timeout");
        // webhook.timeout=1s(테스트 override)보다 긴 지연 — 클라이언트가 타임아웃으로 실패해야 한다.
        WEBHOOK_SERVER.enqueue(new MockResponse()
                .setBody("{}")
                .setBodyDelay(3, TimeUnit.SECONDS));
        enqueuePrescriptionOk();

        long id = createPrescription(token, farmId, "타임아웃웹훅질문", null);
        JsonNode result = awaitPrescriptionTerminal(token, farmId, id);

        assertThat(result.get("status").asText()).isEqualTo("COMPLETED");
        takeWebhookRequest(); // 발송 시도(요청 도달) 자체는 있었음 — 응답이 늦어 타임아웃으로 실패할 뿐
    }
}
