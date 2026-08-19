package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.PrescriptionApiTestSupport;
import com.smartfarm.service.entity.Prescription;
import com.smartfarm.service.entity.PrescriptionStatus;
import com.smartfarm.service.repository.PrescriptionRepository;
import com.smartfarm.service.scheduler.PrescriptionSweeper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 스위퍼 검증 — 시간 경과는 created_at을 SQL로 backdate해 재현하고 sweep()을 직접 호출한다
 * (@Scheduled 자체는 initialDelay 5분이라 테스트 실행 중 자동 구동되지 않는다).
 */
class PrescriptionSweeperTest extends PrescriptionApiTestSupport {

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private PrescriptionSweeper sweeper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("스위퍼: 정체 PENDING(재큐잉 컷 경과·만료 전)은 재큐잉되어 COMPLETED로 수렴한다")
    void sweepRequeuesStalePending() throws Exception {
        String token = signupAndLogin("스위퍼농부");
        long farmId = createFarm(token, "재큐잉 스위퍼 농장");
        long id = savePendingWithoutSubmit(token, farmId, "큐에 못 들어간 질문");
        backdate(id, LocalDateTime.now().minusMinutes(10)); // 재큐잉 컷(5분) 경과, 만료(30분) 전
        enqueuePrescriptionOk();

        sweeper.sweep();

        awaitPrescriptionTerminal(token, farmId, id);
        Prescription swept = prescriptionRepository.findById(id).orElseThrow();
        assertThat(swept.getStatus()).isEqualTo(PrescriptionStatus.COMPLETED);
        assertThat(swept.getResult()).contains("잎곰팡이병");
    }

    @Test
    @DisplayName("스위퍼: 연령 초과(30분) PENDING은 재큐잉 대신 FAILED P002로 컷오프된다")
    void sweepFailsExpiredPending() throws Exception {
        String token = signupAndLogin("스위퍼농부");
        long farmId = createFarm(token, "만료 스위퍼 농장");
        long id = savePendingWithoutSubmit(token, farmId, "너무 늙은 접수");
        backdate(id, LocalDateTime.now().minusMinutes(31));

        sweeper.sweep();

        Prescription swept = prescriptionRepository.findById(id).orElseThrow();
        assertThat(swept.getStatus()).isEqualTo(PrescriptionStatus.FAILED);
        assertThat(swept.getErrorCode()).isEqualTo("P002");
        assertThat(swept.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("스위퍼: 처리 상한을 넘긴 고아 PROCESSING은 FAILED P002로 정리된다")
    void sweepFailsOrphanedProcessing() throws Exception {
        String token = signupAndLogin("스위퍼농부");
        long farmId = createFarm(token, "고아 스위퍼 농장");
        long id = saveProcessing(token, farmId, "워커가 손을 뗀 질문");
        // 테스트 처리 상한 = 2s×3 + 0.15s + 5분 여유 ≈ 5분 7초 — 10분이면 확실히 초과
        backdate(id, LocalDateTime.now().minusMinutes(10));

        sweeper.sweep();

        Prescription swept = prescriptionRepository.findById(id).orElseThrow();
        assertThat(swept.getStatus()).isEqualTo(PrescriptionStatus.FAILED);
        assertThat(swept.getErrorCode()).isEqualTo("P002");
    }

    @Test
    @DisplayName("스위퍼: 처리 상한 이내의 PROCESSING과 신선한 PENDING은 건드리지 않는다")
    void sweepLeavesFreshRowsUntouched() throws Exception {
        String token = signupAndLogin("스위퍼농부");
        long farmId = createFarm(token, "신선 스위퍼 농장");
        long processingId = saveProcessing(token, farmId, "지금 처리 중인 질문");
        long pendingId = savePendingWithoutSubmit(token, farmId, "방금 접수된 질문");

        sweeper.sweep();

        assertThat(prescriptionRepository.findById(processingId).orElseThrow().getStatus())
                .isEqualTo(PrescriptionStatus.PROCESSING);
        assertThat(prescriptionRepository.findById(pendingId).orElseThrow().getStatus())
                .isEqualTo(PrescriptionStatus.PENDING);

        // 뒷정리 — 비종료 잔존 행이 이후 테스트(재기동 복구 등)의 스캔에 섞이지 않게 제거
        prescriptionRepository.deleteById(processingId);
        prescriptionRepository.deleteById(pendingId);
    }

    /** 워커 제출 없이 PENDING 행만 저장 — "큐에 못 들어간 접수" 상황 재현. */
    private long savePendingWithoutSubmit(String token, long farmId, String question) throws Exception {
        return prescriptionRepository.save(Prescription.builder()
                .farmId(farmId).createdBy(myUserId(token)).question(question).build()).getId();
    }

    private long saveProcessing(String token, long farmId, String question) throws Exception {
        Prescription prescription = Prescription.builder()
                .farmId(farmId).createdBy(myUserId(token)).question(question).build();
        prescription.startProcessing();
        return prescriptionRepository.save(prescription).getId();
    }

    private void backdate(long prescriptionId, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE prescriptions SET created_at = ? WHERE id = ?",
                createdAt, prescriptionId);
    }

}
