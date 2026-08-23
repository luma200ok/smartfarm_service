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
 * 방지 배경은 {@link AiChatClientUnitTest} 클래스 주석 참고.
 */
class AiPrescriptionClientUnitTest {

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

    private AiPrescriptionClient client() {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getPort())
                .build();
        return new AiPrescriptionClient(restClient);
    }

    private AiPrescriptionClient clientThatThrows(RuntimeException toThrow) {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getPort())
                .requestInterceptor((request, body, execution) -> {
                    throw toThrow;
                })
                .build();
        return new AiPrescriptionClient(restClient);
    }

    @Test
    @DisplayName("정상 응답이면 그대로 반환한다")
    void returnsResponseOnSuccess() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"진단요약": "요약", "원인": "원인", "즉시조치": "조치", "예방": "예방", "재촬영시점": "3일 후", "근거출처": []}
                        """));

        var response = client().prescribe("질문", null);

        assertThat(response.diagnosisSummary()).isEqualTo("요약");
    }

    @Test
    @DisplayName("429는 P003으로 매핑한다")
    void tooManyRequestsMapsToP003() {
        server.enqueue(new MockResponse().setResponseCode(429));

        assertThatThrownBy(() -> client().prescribe("질문", null))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.P003));
    }

    @Test
    @DisplayName("CancellationException(JDK HttpClient 타임아웃 표면화)이 P002로 매핑된다(이슈 #80 회귀 방지)")
    void cancellationExceptionMapsToPrescriptionDomainError() {
        AiPrescriptionClient client = clientThatThrows(new CancellationException());

        assertThatThrownBy(() -> client.prescribe("질문", null))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.P002));
    }
}
