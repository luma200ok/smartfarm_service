package com.smartfarm.service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.PesticideAlert;
import com.smartfarm.service.entity.PesticideAlertSeverity;
import com.smartfarm.service.repository.PesticideAlertRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 농약 참조정보 API 통합 테스트(이슈 #128) — {@code NutrientPresetApiIntegrationTest}와 동일 패턴
 * (인증만 필요, 농장 스코프 아님). {@code PesticideReferenceSeeder}가 앱 기동 시 이미 TOMATO 참조
 * 6건·경보 2건을 심어 둔 상태를 전제로 검증한다.
 *
 * <p>이 테이블들은 전 테스트 클래스가 공유하는 전역(비 farm-scoped) 데이터라, 이 클래스가 검증용으로
 * 추가 삽입한 행은 반드시 스스로 정리한다(다른 테스트 클래스의 전제를 깨지 않기 위함) — 결과 상한
 * 테스트는 {@code PesticideReferenceResultCapIntegrationTest}(service 패키지, 패키지 프라이빗 상한
 * 상수 접근용)에서 별도로 다루고 그쪽도 동일 원칙으로 정리한다.
 */
class PesticideReferenceApiIntegrationTest extends FarmTestSupport {

    @Autowired
    private PesticideAlertRepository pesticideAlertRepository;

    @Test
    @DisplayName("병해충명 부분 검색(대소문자 무시)으로 해당 항목만 반환한다")
    void searchByPestNameReturnsMatchingItemOnly() throws Exception {
        String token = signupAndLogin("농약조회자-검색");

        MvcResult result = mockMvc.perform(get("/api/pesticide-references")
                        .param("cropType", "TOMATO")
                        .param("q", "총채")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = readJson(result);
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode item : body) {
            assertThat(item.get("pestName").asText()).contains("총채");
        }
    }

    @Test
    @DisplayName("방어선: q에 '%'가 들어가도 SQL LIKE 와일드카드로 해석되지 않고 문자 그대로 검색된다"
            + "(이스케이프 제거 시 실패해야 하는 회귀 테스트 — 리뷰 P3)")
    void percentInQueryIsTreatedLiterallyNotAsWildcard() throws Exception {
        String token = signupAndLogin("농약조회자-와일드카드");

        // 시드된 병해충명 어디에도 '%' 문자가 없다 — 이스케이프가 없으면 LIKE '%%%'가 되어 전체가
        // 매칭되지만(회귀), 이스케이프가 되면 문자 그대로 '%'를 찾으므로 0건이어야 한다.
        MvcResult result = mockMvc.perform(get("/api/pesticide-references")
                        .param("cropType", "TOMATO")
                        .param("q", "%")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(readJson(result).size()).isZero();
    }

    @Test
    @DisplayName("q 생략 시 시드된 6개 병해충 전체가 포함된다")
    void withoutQueryReturnsAllSeededPests() throws Exception {
        String token = signupAndLogin("농약조회자-전체");

        MvcResult result = mockMvc.perform(get("/api/pesticide-references")
                        .param("cropType", "TOMATO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        List<String> pestNames = readJson(result).findValuesAsText("pestName");
        assertThat(pestNames).contains("담배가루이", "총채벌레", "진딧물", "잿빛곰팡이병", "흰가루병", "역병");
    }

    @Test
    @DisplayName("응답 출처(source)는 내부 샘플 데이터임을 명시하고 실제 농진청 연동을 암시하지 않는다"
            + "(안전 요구사항 — 거짓 표기 금지)")
    void sourceDisclosesInternalSampleDataHonestly() throws Exception {
        String token = signupAndLogin("농약조회자-출처");

        MvcResult result = mockMvc.perform(get("/api/pesticide-references")
                        .param("cropType", "TOMATO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = readJson(result);
        assertThat(body.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode item : body) {
            String source = item.get("source").asText();
            // "내부 시드/샘플" 사실이 드러나야 한다
            assertThat(source).contains("샘플");
            // "농촌진흥청과 실시간 연동되지 않으며" — 연동을 명시적으로 부정해야 한다(암시조차 금지)
            assertThat(source).contains("연동되지 않");
            // 프리뷰 mock의 거짓 문구 그대로는 나오면 안 된다
            assertThat(source).doesNotContain("농촌진흥청 농약안전정보시스템 연동 ·");
            assertThat(item.get("updatedAt").isNull()).isFalse();
        }
    }

    @Test
    @DisplayName("cropType 누락 시 400 C001을 반환한다")
    void missingCropTypeReturnsC001() throws Exception {
        String token = signupAndLogin("농약조회자-누락");

        mockMvc.perform(get("/api/pesticide-references")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("존재하지 않는 cropType 값이면 400 C001을 반환한다(enum 바인딩 실패)")
    void invalidCropTypeReturnsC001() throws Exception {
        String token = signupAndLogin("농약조회자-잘못된값");

        mockMvc.perform(get("/api/pesticide-references")
                        .param("cropType", "POTATO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("인증 없이 조회하면 401을 반환한다")
    void unauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/pesticide-references").param("cropType", "TOMATO"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("경보 목록은 시드된 유효 경보를 포함한다")
    void alertsIncludeSeededActiveAlerts() throws Exception {
        String token = signupAndLogin("농약조회자-경보");

        MvcResult result = mockMvc.perform(get("/api/pesticide-references/alerts")
                        .param("cropType", "TOMATO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        List<String> messages = readJson(result).findValuesAsText("message");
        assertThat(messages).anyMatch(m -> m.contains("총채벌레"));
    }

    @Test
    @DisplayName("경보 응답 출처(source)도 내부 샘플임을 명시하고 실제 농진청 예찰 연동을 암시하지 않는다"
            + "(안전 요구사항 — 거짓 표기 금지, 리뷰 P2)")
    void alertSourceDisclosesInternalSampleDataHonestly() throws Exception {
        String token = signupAndLogin("농약조회자-경보출처");

        MvcResult result = mockMvc.perform(get("/api/pesticide-references/alerts")
                        .param("cropType", "TOMATO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = readJson(result);
        assertThat(body.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode item : body) {
            String source = item.get("source").asText();
            // "내부 시드/샘플" 사실이 드러나야 한다
            assertThat(source).contains("샘플");
            // 연동을 명시적으로 부정해야 한다(암시조차 금지)
            assertThat(source).contains("연동되지 않");
            // 프리뷰 mock의 거짓 문구 그대로는 나오면 안 된다
            assertThat(source).doesNotContain("농촌진흥청 농약안전정보시스템 연동 ·");
        }
    }

    @Test
    @DisplayName("방어선: 유효기간이 지났거나 아직 시작 전인 경보는 응답에서 제외된다"
            + "(유효기간 필터 제거 시 실패해야 하는 회귀 테스트)")
    void expiredAndUpcomingAlertsAreExcluded() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String expiredMessage = "만료테스트-" + UUID.randomUUID();
        String upcomingMessage = "예정테스트-" + UUID.randomUUID();

        PesticideAlert expired = pesticideAlertRepository.save(PesticideAlert.builder()
                .cropType(CropType.TOMATO)
                .message(expiredMessage)
                .severity(PesticideAlertSeverity.WARNING)
                .validFrom(now.minusDays(10))
                .validUntil(now.minusDays(1))
                .source("만료 필터 테스트용 임시 출처")
                .build());
        PesticideAlert upcoming = pesticideAlertRepository.save(PesticideAlert.builder()
                .cropType(CropType.TOMATO)
                .message(upcomingMessage)
                .severity(PesticideAlertSeverity.WARNING)
                .validFrom(now.plusDays(1))
                .validUntil(now.plusDays(10))
                .source("만료 필터 테스트용 임시 출처")
                .build());

        try {
            String token = signupAndLogin("농약조회자-경보필터");

            MvcResult result = mockMvc.perform(get("/api/pesticide-references/alerts")
                            .param("cropType", "TOMATO")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            List<String> messages = readJson(result).findValuesAsText("message");
            assertThat(messages).doesNotContain(expiredMessage, upcomingMessage);
        } finally {
            pesticideAlertRepository.deleteAll(List.of(expired, upcoming));
        }
    }
}
