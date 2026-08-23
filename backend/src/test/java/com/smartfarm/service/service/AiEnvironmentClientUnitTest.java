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
 * Spring 컨텍스트 없이 RestClient만으로 오류 매핑을 검증한다(이슈 #80). CancellationException 회귀
 * 방지 배경은 {@link AiChatClientUnitTest} 클래스 주석 참고 — 환경 대시보드는 타임아웃 시 stale 폴백이
 * 동작해야 하는데, 매핑이 새면 500이 나가 폴백조차 동작하지 않는다(이슈 #80 보고 항목).
 */
class AiEnvironmentClientUnitTest {

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

    private AiEnvironmentClient client() {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getPort())
                .build();
        return new AiEnvironmentClient(restClient);
    }

    private AiEnvironmentClient clientThatThrows(RuntimeException toThrow) {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getPort())
                .requestInterceptor((request, body, execution) -> {
                    throw toThrow;
                })
                .build();
        return new AiEnvironmentClient(restClient);
    }

    @Test
    @DisplayName("정상 응답이면 그대로 반환한다")
    void returnsResponseOnSuccess() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"demo": true, "outdoor": {"temp": 20.5, "humidity": 50.0}}
                        """));

        var response = client().fetchToday();

        assertThat(response.demo()).isTrue();
        assertThat(response.outdoor().temp()).isEqualTo(20.5);
    }

    @Test
    @DisplayName("5xx는 D003으로 매핑한다")
    void serverErrorMapsToD003() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> client().fetchToday())
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.D003));
    }

    @Test
    @DisplayName("CancellationException(JDK HttpClient 타임아웃 표면화)이 D003으로 매핑된다(이슈 #80 회귀 방지)")
    void cancellationExceptionMapsToEnvironmentDomainError() {
        AiEnvironmentClient client = clientThatThrows(new CancellationException());

        assertThatThrownBy(client::fetchToday)
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.D003));
    }
}
