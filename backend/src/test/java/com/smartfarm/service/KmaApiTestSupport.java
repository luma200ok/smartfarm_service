package com.smartfarm.service;

import com.smartfarm.service.service.ForecastCache;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 날씨예보(KMA) 통합 테스트 공통 지원 — KMA를 MockWebServer로 대체한다
 * (EnvironmentApiTestSupport와 동일 패턴: static 초기화 블록에서 먼저 기동해야
 * {@code @DynamicPropertySource}가 컨텍스트 준비 시점에 포트를 읽을 수 있다).
 *
 * <p>{@link ForecastCache}는 서버 전역 싱글턴 빈이라 같은 Spring 컨텍스트를 공유하는 테스트 클래스 내
 * 여러 테스트 메서드 사이에서 캐시 상태가 새어나갈 수 있다 — 매 테스트 전 초기화한다.
 */
public abstract class KmaApiTestSupport extends FarmTestSupport {

    protected static final MockWebServer KMA_SERVER = new MockWebServer();

    static {
        try {
            KMA_SERVER.start();
        } catch (IOException e) {
            throw new IllegalStateException("MockWebServer(KMA) 기동 실패", e);
        }
    }

    @DynamicPropertySource
    static void kmaProperties(DynamicPropertyRegistry registry) {
        registry.add("kma.base-url", () -> "http://localhost:" + KMA_SERVER.getPort());
    }

    @Autowired
    private ForecastCache forecastCache;

    @BeforeEach
    void resetForecastCache() {
        forecastCache.evict();
    }

    private static final DateTimeFormatter FCST_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FCST_TIME = DateTimeFormatter.ofPattern("HHmm");

    /**
     * 실제 벽시계 기준 "1시간 뒤" 시각 포인트를 예보로 응답한다 — KmaForecastClient는 실 서비스
     * Clock(systemDefaultZone)으로 "현재 시각 이전 포인트"를 필터링하므로, 테스트 실행 시점과 무관하게
     * 항상 필터를 통과하도록 fcstDate/fcstTime을 하드코딩 대신 실행 시점 기준으로 동적 계산한다.
     */
    protected LocalDateTime forecastPointTime() {
        return LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);
    }

    /** 정상 KMA 응답 예시 — TMP/REH/SKY/POP 각 1개 시각 포인트({@link #forecastPointTime()}). */
    protected void enqueueOkForecastResponse() {
        String fcstDate = forecastPointTime().format(FCST_DATE);
        String fcstTime = forecastPointTime().format(FCST_TIME);
        KMA_SERVER.enqueue(new MockResponse()
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
                                  {"baseDate": "%1$s", "baseTime": "0500", "category": "TMP", "fcstDate": "%1$s", "fcstTime": "%2$s", "fcstValue": "26", "nx": 60, "ny": 127},
                                  {"baseDate": "%1$s", "baseTime": "0500", "category": "REH", "fcstDate": "%1$s", "fcstTime": "%2$s", "fcstValue": "60", "nx": 60, "ny": 127},
                                  {"baseDate": "%1$s", "baseTime": "0500", "category": "SKY", "fcstDate": "%1$s", "fcstTime": "%2$s", "fcstValue": "1", "nx": 60, "ny": 127},
                                  {"baseDate": "%1$s", "baseTime": "0500", "category": "POP", "fcstDate": "%1$s", "fcstTime": "%2$s", "fcstValue": "10", "nx": 60, "ny": 127}
                                ]
                              },
                              "pageNo": 1, "numOfRows": 1000, "totalCount": 4
                            }
                          }
                        }
                        """.formatted(fcstDate, fcstTime)));
    }

    /** KMA 자체 오류(서비스키 오류 등) — resultCode != "00". */
    protected void enqueueKmaErrorResultCode() {
        KMA_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"response": {"header": {"resultCode": "30", "resultMsg": "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}}
                        """));
    }
}
