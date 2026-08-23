package com.smartfarm.service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * KMA 단기예보(getVilageFcst) 호출 전용 RestClient(contract §4.8, 이슈 #56) — EnvironmentClientConfig와
 * 동일 원칙("타임아웃이 요청 팩토리 수준 설정이라 클라이언트를 분리")을 따른다.
 */
@Configuration
@RequiredArgsConstructor
public class KmaClientConfig {

    private final KmaProperties kmaProperties;

    @Bean
    public RestClient kmaRestClient(RestClient.Builder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(kmaProperties.connectTimeout())
                .withReadTimeout(kmaProperties.readTimeout());
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return builder.baseUrl(kmaProperties.baseUrl()).requestFactory(factory).build();
    }
}
