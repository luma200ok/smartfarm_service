package com.smartfarm.service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * ai-server(FastAPI, /api/diagnoses 등) 호출 전용 RestClient — 연결/응답 타임아웃 분리 설정
 * (contract §3 handoff: 연결 3s/응답 30s 수준, {@link AiServerProperties}로 조정 가능·테스트에서 단축).
 *
 * <p>Boot 자동구성 {@link RestClient.Builder}를 그대로 써서 앱 공용 ObjectMapper를 상속한다
 * (FAIL_ON_UNKNOWN_PROPERTIES 비활성 — ai-server 응답에 우리가 안 쓰는 필드가 섞여 있어도 역직렬화가 깨지지 않는다).
 */
@Configuration
@RequiredArgsConstructor
public class AiServerClientConfig {

    private final AiServerProperties aiServerProperties;

    @Bean
    public RestClient aiServerRestClient(RestClient.Builder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(aiServerProperties.connectTimeout())
                .withReadTimeout(aiServerProperties.readTimeout());
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return builder.baseUrl(aiServerProperties.url()).requestFactory(factory).build();
    }
}
