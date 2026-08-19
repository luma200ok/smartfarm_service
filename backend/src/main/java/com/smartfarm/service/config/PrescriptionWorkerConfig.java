package com.smartfarm.service.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 처방 비동기 job 인프라 — 전용 RestClient + 단일 스레드 워커 executor.
 *
 * <p><b>왜 단일 스레드인가</b>: ai-server 뒤 Ollama가 처방 LLM을 서빙하는데, 동시 요청이 들어오면
 * 모델 동시 로드/컨텍스트 경합으로 전체가 느려지거나 OOM 위험이 있다(arm1 -Xmx512m·공유 서버).
 * backend에서 처방 job을 단일 스레드로 직렬화해 ai-server에는 항상 최대 1건만 흘러가게 한다
 * (contract §3 "backend 내 단일 스레드 executor가 순차 처리").
 */
@Configuration
@RequiredArgsConstructor
public class PrescriptionWorkerConfig {

    private final AiServerProperties aiServerProperties;
    private final PrescriptionProperties prescriptionProperties;

    /**
     * 처방 전용 RestClient — 진단용({@link AiServerClientConfig})과 base-url·연결 타임아웃은 같지만
     * 응답 타임아웃이 다르다(진단 30s vs 처방 120s — LLM 웜업 포함). 타임아웃이 요청 팩토리 수준
     * 설정이라 클라이언트를 분리한다(진단 경로가 처방 타임아웃 120s를 물려받으면 ai-server 장애 시
     * 요청 스레드가 2분씩 잠기는 역효과).
     */
    @Bean
    public RestClient aiServerPrescriptionRestClient(RestClient.Builder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(aiServerProperties.connectTimeout())
                .withReadTimeout(prescriptionProperties.readTimeout());
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return builder.baseUrl(aiServerProperties.url()).requestFactory(factory).build();
    }

    /**
     * 단일 워커 executor — 명시적 ExecutorService 빈(@Async 대신: 스레드 수 1 보장을 설정 파일이 아닌
     * 코드로 고정하고, 테스트에서 동일 빈을 그대로 쓰기 위함). 큐는 무제한(LinkedBlockingQueue) —
     * job당 수십 초가 걸려도 접수는 202로 즉시 받고 큐에 쌓는 설계이며, 유실분은 재기동 복구가 흡수한다.
     *
     * <p><b>종료 정책</b>: 데몬 스레드 + shutdownNow — 셧다운 시 in-flight LLM 호출(최대 120s)을
     * 기다리지 않고 즉시 중단한다. 중단된 job은 PROCESSING으로 남고, 재기동 복구가 FAILED(P002)로
     * 정리한다(우아한 대기보다 배포 속도·확실한 수렴을 택함 — 처방은 사용자가 재요청 가능한 작업).
     */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService prescriptionWorkerExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "prescription-worker");
            thread.setDaemon(true);
            return thread;
        });
    }
}
