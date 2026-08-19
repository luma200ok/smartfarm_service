package com.smartfarm.service.service;

import com.smartfarm.service.dto.AiPrescriptionResponse;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class AiPrescriptionClient {

    /** 빈 이름 매칭 주입 — RestClient 빈 2개 중 처방 전용(PrescriptionWorkerConfig) 선택. */
    private final RestClient aiServerPrescriptionRestClient;

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
        } catch (RestClientException e) {
            log.warn("ai-server 처방 요청 실패(접속불가/타임아웃)", e);
            throw new CustomException(ErrorCode.P002);
        }
    }
}
