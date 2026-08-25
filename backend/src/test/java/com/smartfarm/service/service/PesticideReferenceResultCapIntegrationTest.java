package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.PesticideAlert;
import com.smartfarm.service.entity.PesticideAlertSeverity;
import com.smartfarm.service.entity.PesticideReference;
import com.smartfarm.service.repository.PesticideAlertRepository;
import com.smartfarm.service.repository.PesticideReferenceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 결과 상한 방어선 검증(이슈 #128, handoff "결과 상한을 둘 것" — #91 정책과 일관) —
 * {@link PesticideReferenceService#MAX_REFERENCE_RESULTS}·{@link PesticideReferenceService#MAX_ALERT_RESULTS}
 * (패키지 프라이빗)에 직접 접근하기 위해 service 패키지에 둔다({@code ChatRateLimiterUnitTest}가
 * {@code ChatRateLimiter.LIMIT_PER_MINUTE}를 같은 방식으로 참조하는 선례와 동일).
 *
 * <p>이 클래스가 상한 검증용으로 추가 삽입한 행은 전역(비 farm-scoped) 테이블에 영구히 남지 않도록
 * 반드시 스스로 정리한다(다른 테스트 클래스의 "시드 6건/2건" 전제를 깨지 않기 위함).
 */
class PesticideReferenceResultCapIntegrationTest extends FarmTestSupport {

    @Autowired
    private PesticideReferenceRepository pesticideReferenceRepository;

    @Autowired
    private PesticideAlertRepository pesticideAlertRepository;

    @Test
    @DisplayName("방어선: 참조정보가 상한을 넘어도 응답은 MAX_REFERENCE_RESULTS건으로 잘린다"
            + "(상한 제거 시 실패해야 하는 회귀 테스트)")
    void referenceResultsAreCappedEvenWhenMoreExist() throws Exception {
        List<PesticideReference> extra = IntStream
                .range(0, PesticideReferenceService.MAX_REFERENCE_RESULTS + 10)
                .mapToObj(i -> PesticideReference.builder()
                        .cropType(CropType.TOMATO)
                        .pestName("상한테스트-" + UUID.randomUUID())
                        .registeredProductCount(1)
                        .preHarvestIntervalDays(1)
                        .note("상한 테스트용 임시 행")
                        .source("상한 테스트용 임시 출처")
                        .updatedAt(LocalDateTime.now())
                        .build())
                .toList();
        List<PesticideReference> saved = pesticideReferenceRepository.saveAll(extra);

        try {
            String token = signupAndLogin("상한테스트유저-참조");

            MvcResult result = mockMvc.perform(get("/api/pesticide-references")
                            .param("cropType", "TOMATO")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(readJson(result).size()).isEqualTo(PesticideReferenceService.MAX_REFERENCE_RESULTS);
        } finally {
            pesticideReferenceRepository.deleteAll(saved);
        }
    }

    @Test
    @DisplayName("방어선: 유효한 경보가 상한을 넘어도 응답은 MAX_ALERT_RESULTS건으로 잘린다"
            + "(상한 제거 시 실패해야 하는 회귀 테스트)")
    void alertResultsAreCappedEvenWhenMoreExist() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        List<PesticideAlert> extra = IntStream
                .range(0, PesticideReferenceService.MAX_ALERT_RESULTS + 10)
                .mapToObj(i -> PesticideAlert.builder()
                        .cropType(CropType.TOMATO)
                        .message("상한테스트경보-" + UUID.randomUUID())
                        .severity(PesticideAlertSeverity.INFO)
                        .validFrom(now.minusDays(1))
                        .validUntil(now.plusDays(1))
                        .build())
                .toList();
        List<PesticideAlert> saved = pesticideAlertRepository.saveAll(extra);

        try {
            String token = signupAndLogin("상한테스트유저-경보");

            MvcResult result = mockMvc.perform(get("/api/pesticide-references/alerts")
                            .param("cropType", "TOMATO")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(readJson(result).size()).isEqualTo(PesticideReferenceService.MAX_ALERT_RESULTS);
        } finally {
            pesticideAlertRepository.deleteAll(saved);
        }
    }
}
