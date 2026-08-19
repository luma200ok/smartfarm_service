package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartfarm.service.entity.Prescription;
import com.smartfarm.service.entity.PrescriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 엔티티 상태 기계 가드 — 불법 전이는 IllegalStateException(캡슐화된 전이 메서드가 유일한 경로). */
class PrescriptionTransitionGuardTest {

    private Prescription newPrescription() {
        return Prescription.builder()
                .farmId(1L).createdBy(1L).question("전이 가드 질문").build();
    }

    @Test
    @DisplayName("PENDING에서 complete()는 불가 — PROCESSING을 거쳐야 한다")
    void completeFromPendingIsIllegal() {
        Prescription prescription = newPrescription();

        assertThatThrownBy(() -> prescription.complete("{}"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.PENDING);
    }

    @Test
    @DisplayName("startProcessing() 중복 호출은 불가 — 두 번째는 IllegalState")
    void startProcessingTwiceIsIllegal() {
        Prescription prescription = newPrescription();
        prescription.startProcessing();

        assertThatThrownBy(prescription::startProcessing)
                .isInstanceOf(IllegalStateException.class);
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.PROCESSING);
    }

    @Test
    @DisplayName("종료 상태(COMPLETED)에서 fail()은 불가 — 결과 덮어쓰기 차단")
    void failAfterCompletedIsIllegal() {
        Prescription prescription = newPrescription();
        prescription.startProcessing();
        prescription.complete("{\"summary\":\"ok\"}");

        assertThatThrownBy(() -> prescription.fail("P002"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.COMPLETED);
        assertThat(prescription.getErrorCode()).isNull();
    }
}
