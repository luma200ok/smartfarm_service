package com.smartfarm.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * KMA 단기예보 엔드포인트 경로 오타 회귀 방지(이슈 #84).
 *
 * <p>배경: `VilageFcstInfoService2.0`처럼 **언더스코어가 빠진 경로**로 배포되어 예보가 전면 실패했다.
 * 공공데이터포털은 이 경우 키와 무관하게 HTTP 400 `NO_OPENAPI_SERVICE_ERROR`("해당 오픈API 서비스가
 * 없거나 폐기됨")를 준다 — 정식 경로(`VilageFcstInfoService_2.0`)로 보내면 같은 더미 키에도
 * `SERVICE_KEY_IS_NOT_REGISTERED_ERROR`가 와서 경로/키 문제가 구분된다(실측 대조로 확인).
 *
 * <p>통합 테스트는 MockWebServer로 외부 호출을 대체하므로 실제 경로 오타를 잡지 못한다(외부 호출
 * 금지 원칙상 그게 맞다). 그래서 설정값 자체를 문자열로 고정하는 이 가드를 둔다.
 */
class KmaBaseUrlTest {

    /** 공공데이터포털 기상청 단기예보조회서비스 2.0 정식 경로(언더스코어 포함). */
    private static final String OFFICIAL_PATH = "VilageFcstInfoService_2.0";

    @Test
    @DisplayName("application.yml의 KMA base-url은 정식 엔드포인트 경로를 가리킨다")
    void kmaBaseUrlPointsToOfficialEndpoint() throws IOException {
        Path yml = Path.of("src/main/resources/application.yml");
        assertThat(yml).exists();

        String baseUrl = Files.readAllLines(yml, StandardCharsets.UTF_8).stream()
                .map(String::strip)
                .filter(line -> line.startsWith("base-url:") && line.contains("data.go.kr"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("KMA base-url 설정을 찾지 못했다 — 키 이름이 바뀌었는지 확인하라"));

        assertThat(baseUrl)
                .as("언더스코어가 빠지면 공공데이터포털이 키와 무관하게 HTTP 400(NO_OPENAPI_SERVICE_ERROR)을 준다")
                .contains(OFFICIAL_PATH);
    }
}
