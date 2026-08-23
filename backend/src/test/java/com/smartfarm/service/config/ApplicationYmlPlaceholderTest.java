package com.smartfarm.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * application.yml의 환경변수 placeholder에 기본값이 있는지 검증한다(핫픽스 #73 회귀 방지).
 *
 * <p>배경: KMA 예보(#56·#67)를 넣으면서 {@code ${KMA_GRID_NX}}처럼 기본값 없는 placeholder를 추가했는데,
 * 운영 env에 그 값이 없어 **backend 컨테이너가 기동 실패 → 사이트 전체 502**가 됐다. 테스트 프로필
 * (application-test.yml)이 kma 값을 정의하고 있어 전체 테스트는 그대로 통과했다 — 즉 기존 테스트로는
 * 이 부류를 절대 잡을 수 없다.
 *
 * <p>규칙: 기본값 없는 placeholder는 "없으면 서비스가 뜨면 안 되는 필수 시크릿"만 허용한다(아래 화이트
 * 리스트). 그 외 부가 기능용 설정은 반드시 기본값을 둬서, env 누락이 기능 degrade에 그치고 기동 실패로
 * 번지지 않게 한다.
 */
class ApplicationYmlPlaceholderTest {

    /** 미설정 시 기동을 막는 게 옳은 필수 항목 — 추가할 땐 "없으면 서비스가 뜨면 안 되는가"를 자문할 것. */
    private static final Set<String> REQUIRED_WITHOUT_DEFAULT = Set.of(
            "SPRING_DATASOURCE_USERNAME",
            "SPRING_DATASOURCE_PASSWORD",
            "JWT_SECRET",
            "IMAGE_STORAGE_DIR"
    );

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z0-9_]+)(:[^}]*)?}");

    @Test
    @DisplayName("application.yml의 부가 기능 설정은 env 미설정 시에도 기동되도록 기본값을 가진다")
    void optionalPlaceholdersMustHaveDefaults() throws IOException {
        Path yml = Path.of("src/main/resources/application.yml");
        assertThat(yml).exists();

        List<String> missingDefaults = Files.readAllLines(yml, StandardCharsets.UTF_8).stream()
                .flatMap(line -> {
                    Matcher matcher = PLACEHOLDER.matcher(line);
                    return matcher.results().map(result -> result.group(1) + (result.group(2) == null ? "" : "|has-default"));
                })
                .filter(entry -> !entry.contains("|has-default"))
                .filter(name -> !REQUIRED_WITHOUT_DEFAULT.contains(name))
                .toList();

        assertThat(missingDefaults)
                .as("기본값 없는 placeholder는 필수 시크릿만 허용된다. "
                        + "부가 기능이면 ${VAR:기본값} 형태로 두고, 정말 필수면 REQUIRED_WITHOUT_DEFAULT에 근거와 함께 추가하라")
                .isEmpty();
    }
}
