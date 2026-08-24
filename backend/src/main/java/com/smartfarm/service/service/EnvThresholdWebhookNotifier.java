package com.smartfarm.service.service;

import com.smartfarm.service.config.WebhookProperties;
import com.smartfarm.service.dto.DiscordWebhookUrlValidator;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.Farm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 알람 규칙 이탈 디스코드 웹훅 발송(contract §4.6, 이슈 #52 → #118) — {@link EnvThresholdAlertService}가
 * 트랜잭션 밖에서 호출한다. {@link PrescriptionWebhookNotifier}와 동일한 컨벤션(발송 실패는 WARN
 * 로그만·처리 흐름 무영향, URL 원문은 로그에 남기지 않음, 발송 직전 defense-in-depth 프리픽스
 * 재검증, {@code webhookRestClient}(타임아웃 5s) 재사용)을 따르되, 별도 클래스로 둔다 —
 * PrescriptionWebhookNotifier는 기존 테스트(로거 클래스 고정 검증 포함)가 있어 공통 클래스로
 * 묶으면 그 회귀 위험을 지게 된다(handoff: 기존 처방 알림 회귀 금지).
 *
 * <p>#118부터 발송 대상이 환경 임계치를 넘어 알람 규칙 3종(환경·센서·장비 통신)으로 넓어졌지만
 * 클래스 이름은 그대로 둔다 — 이름 변경은 순수 리네임이라 이 사이클의 회귀 위험만 늘린다(후속).
 * 본문 문구는 이미 규칙 일반 표현으로 바꿔 뒀다.
 */
@Slf4j
@Component
public class EnvThresholdWebhookNotifier {

    private final WebhookProperties webhookProperties;
    private final RestClient webhookRestClient;

    public EnvThresholdWebhookNotifier(WebhookProperties webhookProperties,
                                        @Qualifier("webhookRestClient") RestClient webhookRestClient) {
        this.webhookProperties = webhookProperties;
        this.webhookRestClient = webhookRestClient;
    }

    /**
     * {@code message}는 알람 이벤트에 저장되는 문구와 <b>같은 문자열</b>이다(#118) — 웹훅 본문과
     * 알람 화면이 서로 다른 설명을 하지 않도록 {@link EnvThresholdAlertService}가 한 번만 만든다.
     */
    public void notifyBreach(Farm farm, AlarmSeverity severity, String message) {
        try {
            String webhookUrl = farm.getWebhookUrl();
            if (webhookUrl == null) {
                return; // 웹훅 해제 시점 race — 스킵
            }
            if (!DiscordWebhookUrlValidator.isDiscordWebhookUrl(webhookUrl)) {
                log.warn("웹훅 URL이 프리픽스 검증을 통과하지 못해 알람 발송 스킵: farmId={}", farm.getId());
                return;
            }
            String content = buildContent(farm, severity, message);
            webhookRestClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(DiscordWebhookPayload.of(content))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // 요청 URI(디스코드 웹훅 토큰 포함)가 예외 메시지에 실릴 수 있어 원문을 로그에 남기지 않는다.
            log.warn("알람 웹훅 발송 실패(무영향): farmId={}, exceptionType={}",
                    farm.getId(), e.getClass().getSimpleName());
        } catch (Exception e) {
            log.warn("알람 웹훅 발송 중 예상 밖 오류(무영향): farmId={}, exceptionType={}",
                    farm.getId(), e.getClass().getSimpleName());
        }
    }

    private String buildContent(Farm farm, AlarmSeverity severity, String message) {
        String severityLabel = severity == AlarmSeverity.CRITICAL ? "경보" : "주의";
        return "[" + farm.getName() + "] 알람 " + severityLabel + " — " + message + "\n"
                + webhookProperties.detailBaseUrl() + "/farms/" + farm.getId();
    }

}
