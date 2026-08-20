package com.smartfarm.service.controller;

import com.smartfarm.service.dto.DiagnosisResponse;
import com.smartfarm.service.dto.DiagnosisSummaryResponse;
import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.service.DiagnosisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Diagnosis", description = "작물 진단 API")
@RestController
@RequestMapping("/api/farms/{farmId}/diagnoses")
@RequiredArgsConstructor
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    @Operation(summary = "진단 요청 (멤버) — ai-server 프록시 후 이력 저장(ood_blocked도 정상 저장)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DiagnosisResponse> createDiagnosis(@AuthenticationPrincipal Long userId,
                                                              @PathVariable Long farmId,
                                                              @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(diagnosisService.createDiagnosis(farmId, userId, file));
    }

    @Operation(summary = "진단 이력 목록 조회 (멤버, 최신순)")
    @GetMapping
    public ResponseEntity<PageResponse<DiagnosisSummaryResponse>> findDiagnoses(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(diagnosisService.findDiagnoses(farmId, userId, pageable));
    }

    @Operation(summary = "진단 상세 조회 (멤버)")
    @GetMapping("/{diagnosisId}")
    public ResponseEntity<DiagnosisResponse> findDiagnosis(@AuthenticationPrincipal Long userId,
                                                            @PathVariable Long farmId,
                                                            @PathVariable Long diagnosisId) {
        return ResponseEntity.ok(diagnosisService.findDiagnosis(farmId, userId, diagnosisId));
    }

    @Operation(summary = "진단 원본 이미지 조회 (멤버) — 인가 스트리밍, 미저장/구 데이터는 404 D004")
    @GetMapping("/{diagnosisId}/image")
    public ResponseEntity<Resource> findDiagnosisImage(@AuthenticationPrincipal Long userId,
                                                         @PathVariable Long farmId,
                                                         @PathVariable Long diagnosisId) {
        DiagnosisService.DiagnosisImage image = diagnosisService.findDiagnosisImage(farmId, userId, diagnosisId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.resource());
    }
}
