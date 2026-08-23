package com.smartfarm.service.service;

import com.smartfarm.service.dto.AiPrescriptionResponse;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.util.concurrent.CancellationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * ai-server(FastAPI) {@code POST /api/prescriptions} 클라이언트(contract §3 ai-server 연동 표 —
 * multipart form: question, diagnosis?=진단 JSON 문자열).
 *
 * <p>오류 매핑: 429(혼잡)는 P003(워커가 백오프 재시도 판단), 그 외 오류·접속불가·타임아웃은 P002
 * (재시도 없이 실패). {@link AiServerClient}(진단)와 분리된 이유는 응답 타임아웃 차이
 * ({@code aiServerPrescriptionRestClient} 참고).
 */
@Slf4j
@Component
public class AiPrescriptionClient {

    private final RestClient aiServerPrescriptionRestClient;

    /** RestClient 빈 2개(진단/처방) — 파라미터명 매칭에 기대지 않고 @Qualifier로 명시(reviewer P3). */
    public AiPrescriptionClient(
            @Qualifier("aiServerPrescriptionRestClient") RestClient aiServerPrescriptionRestClient) {
        this.aiServerPrescriptionRestClient = aiServerPrescriptionRestClient;
    }

    public AiPrescriptionResponse prescribe(String question, String diagnosisJson) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("question", question);
        if (diagnosisJson != null) {
            body.add("diagnosis", diagnosisJson);
        }

        try {
            AiPrescriptionResponse result = aiServerPrescriptionRestClient.post()
                    .uri("/api/prescriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.value() == 429,
                            (req, res) -> {
                                throw new CustomException(ErrorCode.P003);
                            })
                    .onStatus(status -> status.isError(),
                            (req, res) -> {
                                throw new CustomException(ErrorCode.P002);
                            })
                    .body(AiPrescriptionResponse.class);
            if (result == null) {
                throw new CustomException(ErrorCode.P002);
            }
            return result;
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException | CancellationException e) {
            // JDK HttpClient 기반 요청 팩토리는 읽기 타임아웃 시 future를 cancel해 RestClientException이
            // 아닌 CancellationException으로 표면화될 수 있다(이슈 #80, 운영 실측). 예외 본문에 요청
            // URL·응답 조각이 실릴 수 있어 WARN에는 클래스명만, 상세는 DEBUG로(reviewer P3)
            log.warn("ai-server 처방 요청 실패(접속불가/타임아웃): {}", e.getClass().getSimpleName());
            log.debug("ai-server 처방 요청 실패 상세", e);
            throw new CustomException(ErrorCode.P002);
        }
    }
}
