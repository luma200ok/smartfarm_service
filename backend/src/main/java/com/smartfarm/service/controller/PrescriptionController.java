package com.smartfarm.service.controller;

import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.dto.PrescriptionRequest;
import com.smartfarm.service.dto.PrescriptionResponse;
import com.smartfarm.service.dto.PrescriptionSummaryResponse;
import com.smartfarm.service.service.PrescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Prescription", description = "AI 처방 API (비동기 job — 202 접수 후 폴링)")
@RestController
@RequestMapping("/api/farms/{farmId}/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @Operation(summary = "처방 요청 접수 (OPERATOR 이상) — PENDING 저장 후 202 즉시 반환, 상세 폴링으로 결과 확인")
    @PostMapping
    public ResponseEntity<PrescriptionResponse> createPrescription(@AuthenticationPrincipal Long userId,
                                                                    @PathVariable Long farmId,
                                                                    @Valid @RequestBody PrescriptionRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(prescriptionService.createPrescription(farmId, userId, request));
    }

    @Operation(summary = "처방 상세 조회 (멤버, 폴링용) — status COMPLETED/FAILED까지 2~3초 간격 재조회")
    @GetMapping("/{prescriptionId}")
    public ResponseEntity<PrescriptionResponse> findPrescription(@AuthenticationPrincipal Long userId,
                                                                  @PathVariable Long farmId,
                                                                  @PathVariable Long prescriptionId) {
        return ResponseEntity.ok(prescriptionService.findPrescription(farmId, userId, prescriptionId));
    }

    @Operation(summary = "처방 이력 목록 조회 (멤버, 최신순) — result 본문 제외 경량 응답")
    @GetMapping
    public ResponseEntity<PageResponse<PrescriptionSummaryResponse>> findPrescriptions(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(prescriptionService.findPrescriptions(farmId, userId, pageable));
    }
}
