package com.smartfarm.service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * ai-server(FastAPI) {@code POST /api/chat} 호출 전용 RestClient(contract §4.7, 이슈 #54) —
 * 진단({@link AiServerClientConfig})·처방({@link PrescriptionWorkerConfig})·환경 대시보드
 * ({@link EnvironmentClientConfig})와 동일하게 "타임아웃이 요청 팩토리 수준 설정이라 클라이언트를
 * 분리" 원칙을 따른다.
 */
@Configuration
@RequiredArgsConstructor
public class ChatClientConfig {

    private final AiServerProperties aiServerProperties;
    private final ChatProperties chatProperties;

    @Bean
    public RestClient aiServerChatRestClient(RestClient.Builder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(aiServerProperties.connectTimeout())
                .withReadTimeout(chatProperties.readTimeout());
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return builder.baseUrl(aiServerProperties.url()).requestFactory(factory).build();
    }
}
