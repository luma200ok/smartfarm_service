package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.smartfarm.service.config.WebhookProperties;
import com.smartfarm.service.dto.PrescriptionResult;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.service.PrescriptionTransitionService.PrescriptionJob;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link PrescriptionWebhookNotifier} 단위 테스트(이슈 #21 리뷰 픽스 라운드) — RestClient는
 * Mockito로 대체한다.
 *
 * <p>리뷰 P2로 발송 직전 defense-in-depth 재검증(host==discord.com)이 추가되면서, 저장된 URL이
 * 항상 실제 discord.com이어야만 발송 경로를 태울 수 있게 됐다. 그 host는 로컬 MockWebServer로
 * 가로챌 수 없으므로(고정값 — SSRF 차단이 핵심이라 테스트용으로 느슨하게 열어주면 안 됨), 실제
 * 네트워크 없이 {@code RestClient}를 모킹해 "요청을 올바르게 구성하고, 어떤 예외든 삼키며, 예외
 * 객체 자체(URL이 실릴 수 있는 메시지 포함)를 로그에 붙이지 않는다"는 우리 코드의 책임만 검증한다.
 * 실제 HTTP 타임아웃/재시도 동작은 Spring {@code RestClient}/JDK 클라이언트의 책임이라 재검증하지
 * 않는다. PATCH 시점 프리픽스 검증(정상 쓰기 경로)은 {@link
 * com.smartfarm.service.controller.FarmWebhookApiIntegrationTest}에서 별도로 확인한다.
 */
class PrescriptionWebhookNotifierUnitTest {

    private static final String VALID_URL = "https://discord.com/api/webhooks/123456789012345678/token-abcXYZ";

    private final FarmRepository farmRepository = mock(FarmRepository.class);
    private final RestClient webhookRestClient = mock(RestClient.class);
    private final RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
    private final RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
    private final RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
    private final WebhookProperties webhookProperties =
            new WebhookProperties(Duration.ofSeconds(5), "https://farm.luma200ok.com");

    private final PrescriptionWebhookNotifier notifier =
            new PrescriptionWebhookNotifier(farmRepository, webhookProperties, webhookRestClient);

    @BeforeEach
    void stubFluentChain() {
        when(webhookRestClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
    }

    private Farm farmWithWebhook(String name, String webhookUrl) {
        Farm farm = Farm.builder().name(name).cropType(CropType.TOMATO).build();
        farm.updateWebhookUrl(webhookUrl);
        return farm;
    }

    @Test
    @DisplayName("COMPLETED — 농장명·질문·요약·상세 링크를 담아 POST한다")
    void notifyCompletedSendsExpectedPayload() {
        when(farmRepository.findById(10L)).thenReturn(Optional.of(farmWithWebhook("완료 농장", VALID_URL)));
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());
        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);

        notifier.notifyCompleted(new PrescriptionJob(1L, 10L, "질문입니다", null),
                new PrescriptionResult("요약입니다", List.of(), null, List.of()));

        verify(uriSpec).uri(VALID_URL);
        verify(bodySpec).body(bodyCaptor.capture());
        String payload = bodyCaptor.getValue().toString();
        assertThat(payload).contains("완료 농장", "질문입니다", "요약입니다",
                "https://farm.luma200ok.com/farms/10/prescriptions/1");
    }

    @Test
    @DisplayName("FAILED — errorCode가 상태 라벨에 포함된다")
    void notifyFailedSendsExpectedPayload() {
        when(farmRepository.findById(20L)).thenReturn(Optional.of(farmWithWebhook("실패 농장", VALID_URL)));
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());
        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);

        notifier.notifyFailed(new PrescriptionJob(2L, 20L, "실패질문", null), ErrorCode.P002);

        verify(bodySpec).body(bodyCaptor.capture());
        assertThat(bodyCaptor.getValue().toString()).contains("실패 농장", "P002");
    }

    @Test
    @DisplayName("질문은 50자에서 절단된다")
    void truncatesQuestionAt50Chars() {
        when(farmRepository.findById(10L)).thenReturn(Optional.of(farmWithWebhook("절단 농장", VALID_URL)));
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());
        String longQuestion = "가".repeat(60);
        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);

        notifier.notifyCompleted(new PrescriptionJob(1L, 10L, longQuestion, null),
                new PrescriptionResult("요약", List.of(), null, List.of()));

        verify(bodySpec).body(bodyCaptor.capture());
        String payload = bodyCaptor.getValue().toString();
        assertThat(payload).contains("가".repeat(50) + "…");
        assertThat(payload).doesNotContain("가".repeat(51));
    }

    @Test
    @DisplayName("웹훅 미설정 농장은 발송하지 않는다")
    void skipsWhenNotConfigured() {
        when(farmRepository.findById(30L)).thenReturn(Optional.of(farmWithWebhook("미설정 농장", null)));

        notifier.notifyCompleted(new PrescriptionJob(1L, 30L, "질문", null),
                new PrescriptionResult("요약", List.of(), null, List.of()));

        verifyNoInteractions(webhookRestClient);
    }

    @Test
    @DisplayName("농장이 조회되지 않으면(소멸) 발송하지 않는다")
    void skipsWhenFarmMissing() {
        when(farmRepository.findById(99L)).thenReturn(Optional.empty());

        notifier.notifyCompleted(new PrescriptionJob(1L, 99L, "질문", null),
                new PrescriptionResult("요약", List.of(), null, List.of()));

        verifyNoInteractions(webhookRestClient);
    }

    @Test
    @DisplayName("저장된 URL이 프리픽스 검증을 통과하지 못하면 발송하지 않는다(defense-in-depth, 리뷰 P2)")
    void skipsWhenStoredUrlFailsPrefixCheck() {
        // 정상 쓰기 경로(@DiscordWebhookUrl)를 우회해 다른 호스트가 저장된 상황을 가정한다.
        when(farmRepository.findById(40L))
                .thenReturn(Optional.of(farmWithWebhook("변조 농장", "https://evil.example.com/api/webhooks/1/token")));

        notifier.notifyCompleted(new PrescriptionJob(1L, 40L, "질문", null),
                new PrescriptionResult("요약", List.of(), null, List.of()));

        verifyNoInteractions(webhookRestClient);
    }

    @Test
    @DisplayName("발송 중 RestClientException이 나도 전파되지 않는다(처방 상태 무영향)")
    void swallowsRestClientException() {
        when(farmRepository.findById(10L)).thenReturn(Optional.of(farmWithWebhook("장애 농장", VALID_URL)));
        when(responseSpec.toBodilessEntity())
                .thenThrow(new RestClientException("500 Server Error on POST request for \"" + VALID_URL + "\""));

        // 예외가 여기까지 전파되지 않고 정상 반환되면(테스트 메서드가 끝까지 실행되면) 성공.
        notifier.notifyCompleted(new PrescriptionJob(1L, 10L, "질문", null),
                new PrescriptionResult("요약", List.of(), null, List.of()));
    }

    @Test
    @DisplayName("발송 실패 시 로그에 예외 객체(URL이 실릴 수 있는 메시지)를 첨부하지 않는다(리뷰 P1)")
    void failureLogDoesNotAttachThrowableOrLeakUrl() {
        when(farmRepository.findById(10L)).thenReturn(Optional.of(farmWithWebhook("로그검증 농장", VALID_URL)));
        // Spring RestClient가 실제로 던지는 예외는 요청 URI(디스코드 토큰 포함)를 메시지에 담는다 —
        // 그 상황을 그대로 재현해, 로그가 이 메시지를 절대 실어 나르지 않는지 확인한다.
        when(responseSpec.toBodilessEntity())
                .thenThrow(new RestClientException(
                        "I/O error on POST request for \"" + VALID_URL + "\": Read timed out"));

        Logger notifierLogger = (Logger) LoggerFactory.getLogger(PrescriptionWebhookNotifier.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        notifierLogger.addAppender(appender);
        try {
            notifier.notifyCompleted(new PrescriptionJob(1L, 10L, "질문", null),
                    new PrescriptionResult("요약", List.of(), null, List.of()));
        } finally {
            notifierLogger.detachAndStopAllAppenders();
        }

        assertThat(appender.list).as("발송 실패 WARN 로그가 기록돼야 한다").isNotEmpty();
        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(VALID_URL);
            // 예외를 그대로 로거에 넘기면(e를 마지막 인자로) 스택트레이스에 담긴 요청 URI가 통째로
            // 로그에 남는다 — throwableProxy가 없어야 그 경로가 원천 차단됐다는 증거다.
            assertThat(event.getThrowableProxy())
                    .as("예외 객체를 로그에 첨부하면 안 됨(메시지에 웹훅 URL이 실릴 수 있음)")
                    .isNull();
        }
    }
}
