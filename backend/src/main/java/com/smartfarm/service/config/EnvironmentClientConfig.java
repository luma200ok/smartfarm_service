package com.smartfarm.service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 환경 대시보드 ai-server {@code GET /api/environment/today} 프록시 전용 RestClient
 * (contract §3 Phase 3 handoff — 진단/처방과 base-url·연결 타임아웃은 같지만 응답 타임아웃을
 * 분리한다. {@link AiServerClientConfig}(진단, 30s)·{@link PrescriptionWorkerConfig}(처방, 120s)와
 * 동일하게 "타임아웃이 요청 팩토리 수준 설정이라 클라이언트를 분리" 원칙을 따른다).
 */
@Configuration
@RequiredArgsConstructor
public class EnvironmentClientConfig {

    private final AiServerProperties aiServerProperties;
    private final EnvironmentProperties environmentProperties;

    @Bean
    public RestClient aiServerEnvironmentRestClient(RestClient.Builder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(aiServerProperties.connectTimeout())
                .withReadTimeout(environmentProperties.readTimeout());
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return builder.baseUrl(aiServerProperties.url()).requestFactory(factory).build();
    }
}
