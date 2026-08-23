package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.util.concurrent.CancellationException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Spring 컨텍스트 없이 RestClient만으로 오류 매핑을 검증한다(이슈 #80).
 *
 * <p>핵심 회귀 테스트는 {@link #cancellationExceptionMapsToChatDomainError()} — JDK HttpClient 기반
 * 요청 팩토리는 읽기 타임아웃을 {@code RestClientException}이 아니라
 * {@code java.util.concurrent.CancellationException}으로 표면화할 수 있는데(운영 실측,
 * {@code JdkClientHttpRequest$TimeoutHandler}), 예전 코드는 이를 잡지 못해 GlobalExceptionHandler
 * catch-all로 빠져 계약(CH001, 502) 대신 일반 500(C002)이 나갔다. 실제 네트워크 타임아웃은 레이스
 * 컨디션이라(같은 호출이 ResourceAccessException으로 나올 때도 있음) 결정적 재현을 위해 요청
 * 인터셉터로 CancellationException을 직접 주입한다.
 */
class AiChatClientUnitTest {

    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.shutdown();
    }

    private AiChatClient client() {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getPort())
                .build();
        return new AiChatClient(restClient);
    }

    private AiChatClient clientThatThrows(RuntimeException toThrow) {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getPort())
                .requestInterceptor((request, body, execution) -> {
                    throw toThrow;
                })
                .build();
        return new AiChatClient(restClient);
    }

    @Test
    @DisplayName("정상 응답이면 그대로 반환한다(fallback=true도 성공으로 수용)")
    void returnsResponseOnSuccess() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"answer": "안내문", "sources": [], "fallback": true}
                        """));

        var response = client().chat("질문", "svc:farm:1");

        assertThat(response.answer()).isEqualTo("안내문");
        assertThat(response.fallback()).isTrue();
    }

    @Test
    @DisplayName("429는 CH002로 매핑한다")
    void tooManyRequestsMapsToCh002() {
        server.enqueue(new MockResponse().setResponseCode(429));

        assertThatThrownBy(() -> client().chat("질문", "svc:farm:1"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.CH002));
    }

    @Test
    @DisplayName("CancellationException(JDK HttpClient 타임아웃 표면화)이 CH001로 매핑된다(이슈 #80 회귀 방지)")
    void cancellationExceptionMapsToChatDomainError() {
        AiChatClient client = clientThatThrows(new CancellationException());

        assertThatThrownBy(() -> client.chat("질문", "svc:farm:1"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.CH001));
    }
}
