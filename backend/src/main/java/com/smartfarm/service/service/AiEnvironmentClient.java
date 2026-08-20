package com.smartfarm.service.service;

import com.smartfarm.service.dto.AiEnvironmentResponse;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * ai-server(FastAPI) {@code GET /api/environment/today} 프록시(contract §3 Phase 3).
 *
 * <p>ai-server 자체 장애(5xx·접속불가·타임아웃)는 D003으로 매핑한다. ai-server의 상류 장애(상태
 * 파일·KMA 조회 실패 등)는 ai-server가 자체적으로 200 + 가용 필드 + alerts 사유로 흡수하므로
 * (contract "항상 200") 이 클라이언트에서는 볼 일이 없다 — 여기서 던지는 D003은 순수히 ai-server
 * 자신에 도달하지 못했거나 ai-server가 비정상 응답을 준 경우다.
 */
@Slf4j
@Component
public class AiEnvironmentClient {

    private final RestClient aiServerEnvironmentRestClient;

    public AiEnvironmentClient(
            @Qualifier("aiServerEnvironmentRestClient") RestClient aiServerEnvironmentRestClient) {
        this.aiServerEnvironmentRestClient = aiServerEnvironmentRestClient;
    }

    public AiEnvironmentResponse fetchToday() {
        try {
            AiEnvironmentResponse response = aiServerEnvironmentRestClient.get()
                    .uri("/api/environment/today")
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            (req, res) -> {
                                throw new CustomException(ErrorCode.D003);
                            })
                    .body(AiEnvironmentResponse.class);
            if (response == null) {
                throw new CustomException(ErrorCode.D003);
            }
            return response;
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            // 예외 본문에 요청 URL·응답 조각이 실릴 수 있어 WARN에는 클래스명만(AiPrescriptionClient와 동일 패턴).
            log.warn("ai-server 환경 데이터 요청 실패(접속불가/타임아웃): {}", e.getClass().getSimpleName());
            log.debug("ai-server 환경 데이터 요청 실패 상세", e);
            throw new CustomException(ErrorCode.D003);
        }
    }
}
