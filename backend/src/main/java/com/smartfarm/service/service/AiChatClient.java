package com.smartfarm.service.service;

import com.smartfarm.service.dto.AiChatResponse;
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
 * ai-server(FastAPI) {@code POST /api/chat} 클라이언트(contract §4.7 — multipart form: question,
 * caller_ref). 오류 매핑: 429(혼잡)는 CH002, 그 외 오류·접속불가·타임아웃은 CH001.
 * ai-server가 LLM 실패 시에도 200 + {@code fallback=true}를 줄 수 있는데, 이는 정상 응답으로
 * 그대로 반환한다(성공 수용 — 처방과 동일 트레이드오프, ChatService가 저장한다).
 */
@Slf4j
@Component
public class AiChatClient {

    private final RestClient aiServerChatRestClient;

    public AiChatClient(@Qualifier("aiServerChatRestClient") RestClient aiServerChatRestClient) {
        this.aiServerChatRestClient = aiServerChatRestClient;
    }

    public AiChatResponse chat(String question, String callerRef) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("question", question);
        body.add("caller_ref", callerRef);

        try {
            AiChatResponse response = aiServerChatRestClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.value() == 429,
                            (req, res) -> {
                                throw new CustomException(ErrorCode.CH002);
                            })
                    .onStatus(status -> status.isError(),
                            (req, res) -> {
                                throw new CustomException(ErrorCode.CH001);
                            })
                    .body(AiChatResponse.class);
            if (response == null) {
                throw new CustomException(ErrorCode.CH001);
            }
            return response;
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException | CancellationException e) {
            // JDK HttpClient 기반 요청 팩토리는 읽기 타임아웃 시 future를 cancel해 RestClientException이
            // 아닌 CancellationException으로 표면화될 수 있다(이슈 #80, 운영 실측). 예외 본문에 요청
            // URL·응답 조각이 실릴 수 있어 WARN에는 클래스명만, 상세는 DEBUG로(AiPrescriptionClient 선례).
            log.warn("ai-server 챗 요청 실패(접속불가/타임아웃): {}", e.getClass().getSimpleName());
            log.debug("ai-server 챗 요청 실패 상세", e);
            throw new CustomException(ErrorCode.CH001);
        }
    }
}
