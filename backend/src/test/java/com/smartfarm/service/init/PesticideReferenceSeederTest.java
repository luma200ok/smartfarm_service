package com.smartfarm.service.init;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.repository.PesticideAlertRepository;
import com.smartfarm.service.repository.PesticideReferenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 농약 참조정보 시드 idempotency 검증(이슈 #128) — {@code PrescriptionRecoveryTest}와 동일 원칙:
 * 컨텍스트 재기동 대신 {@link PesticideReferenceSeeder#seed()}를 <b>직접 재호출</b>해 "재기동 시
 * 중복 생성되지 않아야 한다"를 검증한다({@code SmartLifecycle.start()}가 컨텍스트 기동 시 이미 1회
 * 실행했으므로, 이 재호출은 정확히 "재기동" 상황을 재현한다).
 */
class PesticideReferenceSeederTest extends IntegrationTestSupport {

    @Autowired
    private PesticideReferenceSeeder pesticideReferenceSeeder;

    @Autowired
    private PesticideReferenceRepository pesticideReferenceRepository;

    @Autowired
    private PesticideAlertRepository pesticideAlertRepository;

    @Test
    @DisplayName("기동 시 TOMATO 참조 6건·경보 2건이 정확히 1세트만 시드된다")
    void seedsExactlyOneSetOnBoot() {
        assertThat(pesticideReferenceRepository.count()).isEqualTo(6);
        assertThat(pesticideAlertRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("방어선: seed()를 재호출(재기동 재현)해도 행이 중복 생성되지 않는다")
    void reseedingDoesNotDuplicateRows() {
        long referenceCountBefore = pesticideReferenceRepository.count();
        long alertCountBefore = pesticideAlertRepository.count();

        pesticideReferenceSeeder.seed();
        pesticideReferenceSeeder.seed(); // 2회 연속 재호출 — 다중 인스턴스 순차 재기동까지 재현

        assertThat(pesticideReferenceRepository.count()).isEqualTo(referenceCountBefore);
        assertThat(pesticideAlertRepository.count()).isEqualTo(alertCountBefore);
    }
}
