package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.PrescriptionApiTestSupport;
import com.smartfarm.service.entity.Prescription;
import com.smartfarm.service.entity.PrescriptionStatus;
import com.smartfarm.service.repository.PrescriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 워커 멱등 픽업 통합 검증 — 조건부 UPDATE(claimForProcessing) 가드를 실제 DB로 확인한다. */
class PrescriptionWorkerIntegrationTest extends PrescriptionApiTestSupport {

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private PrescriptionJobWorker worker;

    @Test
    @DisplayName("같은 id를 중복 제출해도 정확히 1회만 처리된다(멱등 픽업 — ai-server 호출 1회)")
    void duplicateSubmitProcessesExactlyOnce() throws Exception {
        String token = signupAndLogin("멱등농부");
        long farmId = createFarm(token, "멱등 픽업 농장");
        long id = prescriptionRepository.save(Prescription.builder()
                .farmId(farmId).createdBy(myUserId(token)).question("중복 제출 질문").build()).getId();
        int before = AI_SERVER.getRequestCount();
        enqueuePrescriptionOk(); // 응답은 1건만 준비 — 2회 호출되면 두 번째가 블록돼 테스트가 실패한다

        worker.submit(id); // 정상 제출과 재큐잉(복구·스위퍼)이 같은 id로 겹치는 상황 재현
        worker.submit(id);

        awaitPrescriptionTerminal(token, farmId, id);
        Thread.sleep(300); // 두 번째 제출분이 처리됐다면 관측되도록 여유
        Prescription processed = prescriptionRepository.findById(id).orElseThrow();
        assertThat(processed.getStatus()).isEqualTo(PrescriptionStatus.COMPLETED);
        assertThat(AI_SERVER.getRequestCount() - before).isEqualTo(1);
    }
}
