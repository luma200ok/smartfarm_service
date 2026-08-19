package com.smartfarm.service;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 진단(Diagnosis) 통합 테스트 공통 지원 — ai-server를 MockWebServer로 대체한다.
 * (Testcontainers POSTGRES와 동일 패턴: static 초기화 블록에서 먼저 기동해야
 * {@code @DynamicPropertySource}가 컨텍스트 준비 시점에 포트를 읽을 수 있다)
 */
public abstract class DiagnosisApiTestSupport extends FarmTestSupport {

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

    protected void enqueueOkResponse() {
        AI_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "ood_blocked": false,
                          "reason": null,
                          "label": "early_blight",
                          "label_kr": "잎마름병",
                          "prob": 0.93,
                          "probs": {"early_blight": 0.93, "healthy": 0.05},
                          "part": "leaf",
                          "plant_score": 0.98,
                          "part_prob": 0.97,
                          "cam_png_base64": "aGVsbG8=",
                          "yolo": null
                        }
                        """));
    }

    protected void enqueueOodBlockedResponse() {
        AI_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "ood_blocked": true,
                          "reason": "식물·잎으로 보이지 않는 사진(OOD)",
                          "label": null,
                          "label_kr": null,
                          "prob": null,
                          "probs": null,
                          "part": null,
                          "plant_score": 0.12,
                          "part_prob": null,
                          "cam_png_base64": null,
                          "yolo": null
                        }
                        """));
    }
}
