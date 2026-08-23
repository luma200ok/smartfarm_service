package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.smartfarm.service.config.KmaProperties;
import com.smartfarm.service.dto.ForecastResponse;
import com.smartfarm.service.dto.SkyCondition;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

/**
 * Spring 컨텍스트 없이 RestClient + MockWebServer + 고정 Clock으로 base_date/base_time 슬롯 역산,
 * 응답 파싱(SKY 매핑·시각 필터), 서비스키 단일 인코딩, 오류 매핑(W001)을 검증한다
 * (EnvironmentServiceUnitTest와 동일하게 결정적 시각 제어가 필요한 로직은 통합 테스트보다 여기가 적합).
 */
class KmaForecastClientUnitTest {

    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private KmaForecastClient client(Clock clock) {
        return client(clock, "test-service-key");
    }

    private KmaForecastClient client(Clock clock, String serviceKey) {
        // 타임아웃은 요청 팩토리 수준 설정이라 KmaClientConfig와 동일하게 팩토리에 주입해야 한다.
        // 맨손 RestClient.builder()는 읽기 타임아웃이 없어 NO_RESPONSE 소켓을 무한 대기한다(스위트 정지).
        KmaProperties properties = new KmaProperties(
                "http://localhost:" + server.getPort(), serviceKey, 60, 127,
                Duration.ofMillis(300), Duration.ofMillis(300));
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getPort())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
        return new KmaForecastClient(restClient, properties, clock);
    }

    private void enqueueOk(String baseDate, String baseTime) {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "response": {
                            "header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
                            "body": {
                              "dataType": "JSON",
                              "items": {
                                "item": [
                                  {"baseDate": "%1$s", "baseTime": "%2$s", "category": "TMP", "fcstDate": "20260823", "fcstTime": "0800", "fcstValue": "23", "nx": 60, "ny": 127},
                                  {"baseDate": "%1$s", "baseTime": "%2$s", "category": "REH", "fcstDate": "20260823", "fcstTime": "0800", "fcstValue": "55", "nx": 60, "ny": 127},
                                  {"baseDate": "%1$s", "baseTime": "%2$s", "category": "SKY", "fcstDate": "20260823", "fcstTime": "0800", "fcstValue": "1", "nx": 60, "ny": 127},
                                  {"baseDate": "%1$s", "baseTime": "%2$s", "category": "POP", "fcstDate": "20260823", "fcstTime": "0800", "fcstValue": "20", "nx": 60, "ny": 127},
                                  {"baseDate": "%1$s", "baseTime": "%2$s", "category": "TMP", "fcstDate": "20260823", "fcstTime": "0700", "fcstValue": "21", "nx": 60, "ny": 127},
                                  {"baseDate": "%1$s", "baseTime": "%2$s", "category": "SKY", "fcstDate": "20260823", "fcstTime": "0900", "fcstValue": "4", "nx": 60, "ny": 127},
                                  {"baseDate": "%1$s", "baseTime": "%2$s", "category": "PTY", "fcstDate": "20260823", "fcstTime": "0800", "fcstValue": "0", "nx": 60, "ny": 127}
                                ]
                              },
                              "pageNo": 1, "numOfRows": 1000, "totalCount": 7
                            }
                          }
                        }
                        """.formatted(baseDate, baseTime)));
    }

    // ── base_date/base_time 슬롯 역산 ──────────────────────────

    @Test
    @DisplayName("07:45(=참조 07:35, 10분 버퍼) → 05시 슬롯을 사용한다")
    void resolvesLatestPastSlotWithBuffer() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T07:45:00Z"), ZoneOffset.UTC);
        enqueueOk("20260823", "0500");

        client(clock).fetchForecast();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).contains("base_date=20260823");
        assertThat(request.getPath()).contains("base_time=0500");
    }

    @Test
    @DisplayName("자정 직후(00:05, 참조 00:55 이전 02시 미도달) → 전날 23시 슬롯으로 넘어간다")
    void resolvesPreviousDaySlotBeforeFirstAnnouncement() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T01:05:00Z"), ZoneOffset.UTC);
        enqueueOk("20260822", "2300");

        client(clock).fetchForecast();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).contains("base_date=20260822");
        assertThat(request.getPath()).contains("base_time=2300");
    }

    // ── 서비스키 단일 인코딩 ────────────────────────────────────

    @Test
    @DisplayName("서비스키에 +/=가 섞여 있어도 단일 인코딩된다(이중 인코딩 회귀 방지)")
    void encodesServiceKeyExactlyOnce() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T07:45:00Z"), ZoneOffset.UTC);
        enqueueOk("20260823", "0500");

        client(clock, "abc+def==").fetchForecast();

        RecordedRequest request = server.takeRequest();
        // 단일 인코딩: '+' → %2B, '=' → %3D. 이중 인코딩이면 %2B가 %252B로 나타난다.
        assertThat(request.getPath()).contains("serviceKey=abc%2Bdef%3D%3D");
        assertThat(request.getPath()).doesNotContain("%25");
    }

    // ── 응답 파싱(SKY 매핑·시각 필터) ────────────────────────────

    @Test
    @DisplayName("정상 응답 — 카테고리별 매핑, 현재 시각 이전 포인트는 제외, 관심 외 카테고리는 무시된다")
    void parsesPointsAndFiltersPastTimes() {
        // now(07:45) truncated to hour = 07:00 → 07:00 포인트도 포함(경계 포함)
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T07:45:00Z"), ZoneOffset.UTC);
        enqueueOk("20260823", "0500");

        ForecastResponse response = client(clock).fetchForecast();

        assertThat(response.points()).hasSize(3);
        ForecastResponse.Point sevenAm = response.points().get(0);
        assertThat(sevenAm.time()).isEqualTo(LocalDateTime.of(2026, 8, 23, 7, 0));
        assertThat(sevenAm.temp()).isEqualTo(21.0);

        ForecastResponse.Point eightAm = response.points().get(1);
        assertThat(eightAm.time()).isEqualTo(LocalDateTime.of(2026, 8, 23, 8, 0));
        assertThat(eightAm.temp()).isEqualTo(23.0);
        assertThat(eightAm.humidity()).isEqualTo(55.0);
        assertThat(eightAm.sky()).isEqualTo(SkyCondition.SUNNY);
        assertThat(eightAm.pop()).isEqualTo(20);

        ForecastResponse.Point nineAm = response.points().get(2);
        assertThat(nineAm.time()).isEqualTo(LocalDateTime.of(2026, 8, 23, 9, 0));
        assertThat(nineAm.sky()).isEqualTo(SkyCondition.OVERCAST);
        assertThat(nineAm.temp()).isNull();
    }

    @Test
    @DisplayName("현재 시각 이후로 이동하면 과거 포인트가 필터링된다")
    void filtersOutPointsBeforeNow() {
        // now(08:30) truncated to hour = 08:00 → 07:00 포인트는 제외
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T08:30:00Z"), ZoneOffset.UTC);
        enqueueOk("20260823", "0500");

        ForecastResponse response = client(clock).fetchForecast();

        assertThat(response.points()).extracting(ForecastResponse.Point::time)
                .containsExactly(LocalDateTime.of(2026, 8, 23, 8, 0), LocalDateTime.of(2026, 8, 23, 9, 0));
    }

    // ── 오류 매핑(W001) ──────────────────────────────────────

    @Test
    @DisplayName("resultCode가 00이 아니면(서비스키 오류 등) W001을 던진다")
    void nonSuccessResultCodeMapsToW001() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T07:45:00Z"), ZoneOffset.UTC);
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"response": {"header": {"resultCode": "30", "resultMsg": "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}}
                        """));

        assertThatThrownBy(() -> client(clock).fetchForecast())
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.W001));
    }

    @Test
    @DisplayName("KMA 5xx 응답 시 W001을 던진다")
    void serverErrorMapsToW001() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T07:45:00Z"), ZoneOffset.UTC);
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> client(clock).fetchForecast())
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.W001));
    }

    @Test
    @DisplayName("응답 없음(타임아웃)이면 W001을 던진다")
    void timeoutMapsToW001() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T07:45:00Z"), ZoneOffset.UTC);
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        assertThatThrownBy(() -> client(clock).fetchForecast())
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.W001));
    }

    // ── DEBUG 로그 서비스키 마스킹(이슈 #80 P1) ──────────────────────

    /**
     * 요청 인터셉터로 I/O 실패를 직접 주입 — Spring의 {@code ResourceAccessException}은
     * "I/O error on GET request for \"<쿼리 제거된 URI>\": <원인 IOException 메시지>" 형태로 메시지를
     * 조립하는데, 뒷부분(원인 메시지)은 그대로 이어붙이므로 원인 IOException 메시지에 서비스키가
     * 들어 있으면 그대로 노출된다 — 실제 리뷰에서 지적된 경로를 그대로 재현한다.
     */
    private KmaForecastClient clientWithIoFailure(Clock clock, String ioFailureMessage) {
        KmaProperties properties = new KmaProperties(
                "http://localhost:" + server.getPort(), "test-service-key", 60, 127,
                Duration.ofMillis(300), Duration.ofMillis(300));
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getPort())
                .requestInterceptor((request, body, execution) -> {
                    throw new IOException(ioFailureMessage);
                })
                .build();
        return new KmaForecastClient(restClient, properties, clock);
    }

    @Test
    @DisplayName("원인 예외 메시지에 서비스키가 섞여도 DEBUG 로그에 원문이 남지 않는다(이슈 #80 P1 회귀 방지)")
    void debugLogMasksServiceKeyOnIoFailure() {
        Logger logger = (Logger) LoggerFactory.getLogger(KmaForecastClient.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);

        String secretKey = "SUPER-SECRET-KMA-KEY-0000";
        Clock clock = Clock.fixed(Instant.parse("2026-08-23T07:45:00Z"), ZoneOffset.UTC);
        KmaForecastClient client = clientWithIoFailure(clock,
                "Connection reset while calling serviceKey=" + secretKey + "&pageNo=1");

        try {
            assertThatThrownBy(client::fetchForecast)
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.W001));

            String allLogs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + "\n" + b);
            assertThat(allLogs).doesNotContain(secretKey);
            assertThat(allLogs).contains("serviceKey=***");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }
}
