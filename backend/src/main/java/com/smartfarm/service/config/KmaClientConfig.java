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
        // baseUrl은 설정하지 않는다 — KmaForecastClient가 인증키 단일 인코딩을 위해 절대 URI를 직접
        // 만들어 넘기는데, RestClient는 절대 URI를 받으면 빌더의 baseUrl을 무시한다. 여기에 baseUrl을
        // 두면 "상대 경로만 넘기면 된다"는 오해를 부르는 죽은 설정이 된다(리뷰 P3).
        return builder.requestFactory(factory).build();
    }
}
