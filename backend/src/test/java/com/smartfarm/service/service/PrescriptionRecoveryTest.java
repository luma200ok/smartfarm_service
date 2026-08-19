package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.PrescriptionApiTestSupport;
import com.smartfarm.service.entity.Prescription;
import com.smartfarm.service.entity.PrescriptionStatus;
import com.smartfarm.service.init.PrescriptionRecoveryInitializer;
import com.smartfarm.service.repository.PrescriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 재기동 복구 검증 — Spring 컨텍스트 재기동 대신 이전 프로세스가 남긴 상태(PROCESSING/PENDING 행)를
 * repository로 재현하고 {@link PrescriptionRecoveryInitializer#recover()}를 직접 호출해 검증한다
 * (@PostConstruct는 컨텍스트 기동 시 이미 1회 실행됐고, recover()는 멱등이라 재호출이 안전).
 */
class PrescriptionRecoveryTest extends PrescriptionApiTestSupport {

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private PrescriptionRecoveryInitializer recoveryInitializer;

    @Test
    @DisplayName("재기동 복구: PROCESSING 잔존 건은 FAILED(P002)로 정리된다")
    void recoveryFailsStaleProcessing() throws Exception {
        String token = signupAndLogin("복구농부");
        long farmId = createFarm(token, "복구 농장");
        long userId = myUserId(token);

        // 이전 프로세스가 ai-server 호출 중 죽은 상황 재현 — PROCESSING으로 저장
        Prescription stale = Prescription.builder()
                .farmId(farmId).createdBy(userId).question("셧다운 중이던 질문").build();
        stale.startProcessing();
        stale = prescriptionRepository.save(stale);

        recoveryInitializer.recover();

        Prescription recovered = prescriptionRepository.findById(stale.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(PrescriptionStatus.FAILED);
        assertThat(recovered.getErrorCode()).isEqualTo("P002");
        assertThat(recovered.getCompletedAt()).isNotNull();
        assertThat(recovered.getResult()).isNull();
    }

    @Test
    @DisplayName("재기동 복구: PENDING 잔존 건은 재큐잉되어 워커가 처리(COMPLETED)한다")
    void recoveryRequeuesPending() throws Exception {
        String token = signupAndLogin("복구농부");
        long farmId = createFarm(token, "재큐잉 농장");
        long userId = myUserId(token);

        // 이전 프로세스가 접수만 하고(202) 처리 전에 죽은 상황 재현 — PENDING으로 저장(제출 없음)
        Prescription pending = prescriptionRepository.save(Prescription.builder()
                .farmId(farmId).createdBy(userId).question("접수만 된 질문").build());
        enqueuePrescriptionOk();

        recoveryInitializer.recover();

        // 재큐잉된 job이 워커에서 비동기 완료될 때까지 폴링
        awaitPrescriptionTerminal(token, farmId, pending.getId());
        Prescription completed = prescriptionRepository.findById(pending.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(PrescriptionStatus.COMPLETED);
        assertThat(completed.getResult()).contains("잎곰팡이병");
        assertThat(completed.getErrorCode()).isNull();
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("재기동 복구는 종료 상태(COMPLETED/FAILED) 건을 건드리지 않는다(멱등)")
    void recoveryDoesNotTouchTerminalRows() throws Exception {
        String token = signupAndLogin("복구농부");
        long farmId = createFarm(token, "멱등 농장");
        enqueuePrescriptionOk();
        long completedId = createPrescription(token, farmId, "정상 완료 질문", null);
        awaitPrescriptionTerminal(token, farmId, completedId);

        recoveryInitializer.recover();

        Prescription untouched = prescriptionRepository.findById(completedId).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(PrescriptionStatus.COMPLETED);
        assertThat(untouched.getErrorCode()).isNull();
        assertThat(untouched.getResult()).contains("잎곰팡이병");
    }

    private long myUserId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("id").asLong();
    }
}
