package com.smartfarm.service.service;

import com.smartfarm.service.dto.AiDiagnosisResponse;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

/**
 * ai-server(FastAPI) {@code POST /api/diagnoses} 프록시(contract §3 handoff).
 * ai-server의 400/413/415(이미지 검증 실패)는 D002로, 5xx·접속불가·타임아웃은 D003으로 매핑한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final RestClient aiServerRestClient;

    public AiDiagnosisResponse diagnose(MultipartFile file) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", toResource(file));

        try {
            AiDiagnosisResponse response = aiServerRestClient.post()
                    .uri("/api/diagnoses")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.value() == 400 || status.value() == 413 || status.value() == 415,
                            (req, res) -> {
                                throw new CustomException(ErrorCode.D002);
                            })
                    .onStatus(status -> status.isError(),
                            (req, res) -> {
                                throw new CustomException(ErrorCode.D003);
                            })
                    .body(AiDiagnosisResponse.class);
            if (response == null) {
                throw new CustomException(ErrorCode.D003);
            }
            return response;
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("ai-server 진단 요청 실패(접속불가/타임아웃)", e);
            throw new CustomException(ErrorCode.D003);
        }
    }

    private ByteArrayResource toResource(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
        } catch (IOException e) {
            throw new CustomException(ErrorCode.D002);
        }
    }
}
