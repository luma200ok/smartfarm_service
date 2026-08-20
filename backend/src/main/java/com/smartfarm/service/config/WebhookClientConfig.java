package com.smartfarm.service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 디스코드 웹훅 발송 전용 RestClient(contract §3 handoff — 타임아웃 5s, 처방용 120s 클라이언트 재사용 금지).
 *
 * <p>baseUrl을 두지 않는다 — 농장마다 다른 절대 URL({@code farm.webhookUrl})로 직접 POST하기 때문
 * (ai-server 클라이언트들과 달리 단일 호스트가 아님).
 */
@Configuration
@RequiredArgsConstructor
public class WebhookClientConfig {

    private final WebhookProperties webhookProperties;

    @Bean
    public RestClient webhookRestClient(RestClient.Builder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(webhookProperties.timeout())
                .withReadTimeout(webhookProperties.timeout());
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return builder.requestFactory(factory).build();
    }
}
