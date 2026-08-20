package com.smartfarm.service.service;

import com.smartfarm.service.config.WebhookProperties;
import com.smartfarm.service.dto.DiscordWebhookUrlValidator;
import com.smartfarm.service.dto.PrescriptionResult;
import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.service.PrescriptionTransitionService.PrescriptionJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

/**
 * 처방 완료/실패 디스코드 웹훅 발송(contract §3 Phase 3, 이슈 #21) — {@link PrescriptionJobWorker}가
 * 전이(COMPLETED/FAILED) 커밋이 <b>실제로 성공했을 때만</b> 트랜잭션 밖에서 호출한다.
 *
 * <p>실패 처리 원칙(handoff): 발송 실패는 WARN 로그만 남기고 처방 상태에 영향을 주지 않는다.
 * 로그에는 웹훅 URL 원문을 남기지 않는다(farmId만 — URL에 discord 웹훅 토큰이 포함돼 있어 원문 로그는
 * 시크릿 노출과 동급). 농장이 웹훅 미설정(또는 조회 시점에 이미 soft delete)이면 조용히 스킵한다.
 */
@Slf4j
@Component
public class PrescriptionWebhookNotifier {

    private static final int QUESTION_TRUNCATE = 50;

    private final FarmRepository farmRepository;
    private final WebhookProperties webhookProperties;
    private final RestClient webhookRestClient;

    public PrescriptionWebhookNotifier(FarmRepository farmRepository,
                                        WebhookProperties webhookProperties,
                                        @Qualifier("webhookRestClient") RestClient webhookRestClient) {
        this.farmRepository = farmRepository;
        this.webhookProperties = webhookProperties;
        this.webhookRestClient = webhookRestClient;
    }

    public void notifyCompleted(PrescriptionJob job, PrescriptionResult result) {
        send(job, "완료", result == null ? null : result.summary());
    }

    public void notifyFailed(PrescriptionJob job, ErrorCode errorCode) {
        send(job, "실패(" + errorCode.getCode() + ")", null);
    }

    /**
     * findById 조회부터 전부 try 안(리뷰 P3) — "예외는 절대 전파하지 않는다"는 이 메서드의 계약을
     * 완결한다(조회 실패까지 포함해 어떤 예외 경로도 오해성 로그·전파 없이 여기서 끝난다).
     */
    private void send(PrescriptionJob job, String statusLabel, String summary) {
        try {
            Farm farm = farmRepository.findById(job.farmId()).orElse(null);
            if (farm == null || farm.getWebhookUrl() == null) {
                return; // 웹훅 미설정/농장 소멸 — 스킵(handoff)
            }
            String webhookUrl = farm.getWebhookUrl();
            // defense-in-depth(리뷰 P2) — 쓰기 시점 DTO 검증(@DiscordWebhookUrl)을 신뢰하되, 미래에
            // 검증을 우회하는 쓰기 경로가 생겨도 발송 직전 한 번 더 막는다. URL 원문은 로그에 남기지 않는다.
            if (!DiscordWebhookUrlValidator.isDiscordWebhookUrl(webhookUrl)) {
                log.warn("웹훅 URL이 프리픽스 검증을 통과하지 못해 발송 스킵: farmId={}", farm.getId());
                return;
            }
            String content = buildContent(farm.getName(), job, statusLabel, summary);
            webhookRestClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new DiscordPayload(content))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // 예외 메시지·스택트레이스에 요청 URI(디스코드 웹훅 토큰 포함)가 실릴 수 있어 원문을 절대
            // 로깅하지 않는다(리뷰 P1) — farmId·예외 타입명만 남긴다.
            log.warn("디스코드 웹훅 발송 실패(처방 상태 무영향): farmId={}, exceptionType={}",
                    job.farmId(), e.getClass().getSimpleName());
        } catch (Exception e) {
            log.warn("디스코드 웹훅 발송 중 예상 밖 오류(처방 상태 무영향): farmId={}, exceptionType={}",
                    job.farmId(), e.getClass().getSimpleName());
        }
    }

    private String buildContent(String farmName, PrescriptionJob job, String statusLabel, String summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(farmName).append("] 처방 ").append(statusLabel).append('\n');
        sb.append("질문: ").append(truncateQuestion(job.question())).append('\n');
        if (summary != null && !summary.isBlank()) {
            sb.append(summary).append('\n');
        }
        sb.append(webhookProperties.detailBaseUrl())
                .append("/farms/").append(job.farmId())
                .append("/prescriptions/").append(job.id());
        return sb.toString();
    }

    private String truncateQuestion(String question) {
        if (question == null) {
            return "";
        }
        return question.length() <= QUESTION_TRUNCATE ? question : question.substring(0, QUESTION_TRUNCATE) + "…";
    }

    /** 디스코드 웹훅 payload 최소 스키마 — content 필드만 사용(embeds는 1차 미사용). */
    private record DiscordPayload(String content) {
    }
}
