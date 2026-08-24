package com.smartfarm.service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.PrescriptionApiTestSupport;
import com.smartfarm.service.dto.WithdrawRequest;
import com.smartfarm.service.entity.FarmRole;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 탈퇴 × 처방 비동기 job 경계 — PENDING 처방을 남긴 채 작성자가 탈퇴해도 워커가 500 없이
 * 종료 상태(COMPLETED/FAILED)로 처리해야 한다(contract: createdBy는 탈퇴 후에도 원 userId
 * 유지 — 팀 이력 보존, join 없어 PII 미노출).
 */
class WithdrawalPrescriptionIntegrationTest extends PrescriptionApiTestSupport {

    @Test
    @DisplayName("PENDING 처방을 남긴 채 작성자가 탈퇴해도 워커는 정상 종료 상태로 처리한다")
    void withdrawWithPendingPrescriptionDoesNotBreakWorker() throws Exception {
        String owner = signupAndLogin("처방탈퇴오너");
        long farmId = createFarm(owner, "처방탈퇴농장");
        String member = signupAndLogin("처방탈퇴멤버");
        joinFarmAs(owner, farmId, member, FarmRole.OPERATOR);

        String marker = "탈퇴검증질문-" + UUID.randomUUID();
        enqueuePrescriptionOk();
        long prescriptionId = createPrescription(member, farmId, marker, null);

        // 처방이 큐에 있는 사이(또는 직후) 작성자 탈퇴
        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WithdrawRequest("password123"))))
                .andExpect(status().isNoContent());

        // 워커는 500 없이 종료 상태로 전이 — 남은 멤버(오너)가 정상 폴링 가능
        JsonNode terminal = awaitPrescriptionTerminal(owner, farmId, prescriptionId);
        assertThat(terminal.get("status").asText()).isIn("COMPLETED", "FAILED");
        assertThat(terminal.get("createdBy").isNumber()).isTrue();
    }
}
