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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * Spring 컨텍스트 없이 RestClient만으로 오류 매핑을 검증한다(이슈 #80). CancellationException 회귀
 * 방지 배경은 {@link AiChatClientUnitTest} 클래스 주석 참고.
 */
class AiServerClientUnitTest {

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

    private MultipartFile leafImage() {
        return new MockMultipartFile("file", "leaf.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }

    private AiServerClient client() {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getPort())
                .build();
        return new AiServerClient(restClient);
    }

    private AiServerClient clientThatThrows(RuntimeException toThrow) {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getPort())
                .requestInterceptor((request, body, execution) -> {
                    throw toThrow;
                })
                .build();
        return new AiServerClient(restClient);
    }

    @Test
    @DisplayName("정상 응답이면 그대로 반환한다")
    void returnsResponseOnSuccess() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"ood_blocked": false, "label": "healthy", "label_kr": "정상", "prob": 0.9}
                        """));

        var response = client().diagnose(leafImage());

        assertThat(response.label()).isEqualTo("healthy");
    }

    @Test
    @DisplayName("400은 D002로 매핑한다")
    void badRequestMapsToD002() {
        server.enqueue(new MockResponse().setResponseCode(400));

        assertThatThrownBy(() -> client().diagnose(leafImage()))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.D002));
    }

    @Test
    @DisplayName("CancellationException(JDK HttpClient 타임아웃 표면화)이 D003으로 매핑된다(이슈 #80 회귀 방지)")
    void cancellationExceptionMapsToDiagnosisDomainError() {
        AiServerClient client = clientThatThrows(new CancellationException());

        assertThatThrownBy(() -> client.diagnose(leafImage()))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.D003));
    }
}
