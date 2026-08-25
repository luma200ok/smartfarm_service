package com.smartfarm.service.controller;

import com.smartfarm.service.dto.PesticideAlertResponse;
import com.smartfarm.service.dto.PesticideReferenceResponse;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.service.PesticideReferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 농약 참조정보 API(이슈 #128) — {@code NutrientPresetController}와 동일 패턴(전역 조회, 인증만,
 * farm-scoped 아님). 응답의 {@code source}는 항상 이 데이터가 참고용 샘플임을 드러낸다(실제
 * 농촌진흥청 연동 아님 — {@link PesticideReferenceResponse} 참고).
 */
@Tag(name = "PesticideReference", description = "농약 참조정보 API")
@RestController
@RequestMapping("/api/pesticide-references")
@RequiredArgsConstructor
public class PesticideReferenceController {

    private final PesticideReferenceService pesticideReferenceService;

    @Operation(summary = "작물×병해충별 농약 참조정보 조회 (인증만, 참고용 샘플 데이터)")
    @GetMapping
    public ResponseEntity<List<PesticideReferenceResponse>> findReferences(
            @AuthenticationPrincipal Long userId,
            @RequestParam CropType cropType,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(pesticideReferenceService.findReferences(cropType, q));
    }

    @Operation(summary = "작물별 병해충 발생주의 경보 조회 (인증만, 유효기간 내 경보만, 참고용 내부 샘플)")
    @GetMapping("/alerts")
    public ResponseEntity<List<PesticideAlertResponse>> findAlerts(
            @AuthenticationPrincipal Long userId,
            @RequestParam CropType cropType) {
        return ResponseEntity.ok(pesticideReferenceService.findActiveAlerts(cropType));
    }
}
