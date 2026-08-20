package com.smartfarm.service;

import com.smartfarm.service.service.EnvironmentCache;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 환경 대시보드 통합 테스트 공통 지원 — ai-server를 MockWebServer로 대체한다
 * (DiagnosisApiTestSupport와 동일 패턴: static 초기화 블록에서 먼저 기동해야
 * {@code @DynamicPropertySource}가 컨텍스트 준비 시점에 포트를 읽을 수 있다).
 *
 * <p>{@link EnvironmentCache}는 서버 전역 싱글턴 빈이라 같은 Spring 컨텍스트를 공유하는 테스트
 * 클래스 내 여러 테스트 메서드 사이에서 캐시 상태가 새어나갈 수 있다 — 매 테스트 전 초기화한다.
 */
public abstract class EnvironmentApiTestSupport extends FarmTestSupport {

    protected static final MockWebServer AI_SERVER = new MockWebServer();

    static {
        try {
            AI_SERVER.start();
        } catch (IOException e) {
            throw new IllegalStateException("MockWebServer(ai-server) 기동 실패", e);
        }
    }

    @DynamicPropertySource
    static void aiServerProperties(DynamicPropertyRegistry registry) {
        registry.add("ai-server.url", () -> "http://localhost:" + AI_SERVER.getPort());
    }

    @Autowired
    private EnvironmentCache environmentCache;

    @BeforeEach
    void resetEnvironmentCache() {
        environmentCache.evict();
    }

    /** contract §3 Phase 3 확정 스키마의 실응답 예시(snake_case) — 정상 케이스. */
    protected void enqueueOkEnvironmentResponse() {
        AI_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "demo": true,
                          "updated_at": "2026-08-20T09:00:00",
                          "outdoor": {"temp": 28.5, "humidity": 62.0},
                          "indoor": {"temp": 24.1, "humidity": 55.3, "controlled": true},
                          "devices": [
                            {"name": "dehumidifier", "on": false},
                            {"name": "cooling_fan", "on": true}
                          ],
                          "alerts": []
                        }
                        """));
    }

    /** ai-server 상류 장애(KMA 등)를 흡수한 부분 응답 — 필드 누락(null)을 그대로 수용하는지 검증용. */
    protected void enqueuePartialEnvironmentResponse() {
        AI_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "demo": true,
                          "updated_at": "2026-08-20T09:00:00",
                          "outdoor": null,
                          "indoor": {"temp": 24.1, "humidity": 55.3, "controlled": true},
                          "devices": [],
                          "alerts": ["외기 데이터 조회 실패(KMA 응답 없음)"]
                        }
                        """));
    }
}
