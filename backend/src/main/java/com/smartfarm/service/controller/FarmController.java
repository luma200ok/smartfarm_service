package com.smartfarm.service.controller;

import com.smartfarm.service.dto.FarmRequest;
import com.smartfarm.service.dto.FarmResponse;
import com.smartfarm.service.dto.FarmSummaryResponse;
import com.smartfarm.service.dto.FarmUpdateRequest;
import com.smartfarm.service.dto.WebhookRequest;
import com.smartfarm.service.service.FarmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Farm", description = "농장 API")
@RestController
@RequestMapping("/api/farms")
@RequiredArgsConstructor
public class FarmController {

    private final FarmService farmService;

    @Operation(summary = "농장 생성 (생성자=ADMIN)")
    @PostMapping
    public ResponseEntity<FarmResponse> createFarm(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody FarmRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(farmService.createFarm(userId, request));
    }

    @Operation(summary = "내 농장 목록 조회")
    @GetMapping
    public ResponseEntity<List<FarmSummaryResponse>> findMyFarms(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(farmService.findMyFarms(userId));
    }

    @Operation(summary = "농장 상세 조회 (멤버)")
    @GetMapping("/{farmId}")
    public ResponseEntity<FarmResponse> findFarm(@AuthenticationPrincipal Long userId,
                                                 @PathVariable Long farmId) {
        return ResponseEntity.ok(farmService.findFarm(farmId, userId));
    }

    @Operation(summary = "농장 부분 수정 (ADMIN)")
    @PatchMapping("/{farmId}")
    public ResponseEntity<FarmResponse> updateFarm(@AuthenticationPrincipal Long userId,
                                                   @PathVariable Long farmId,
                                                   @Valid @RequestBody FarmUpdateRequest request) {
        return ResponseEntity.ok(farmService.updateFarm(farmId, userId, request));
    }

    @Operation(summary = "농장 디스코드 웹훅 설정/해제 (ADMIN) — null=해제, URL 원문은 응답에 없음(webhookConfigured만)")
    @PatchMapping("/{farmId}/webhook")
    public ResponseEntity<FarmResponse> updateWebhook(@AuthenticationPrincipal Long userId,
                                                       @PathVariable Long farmId,
                                                       @Valid @RequestBody WebhookRequest request) {
        return ResponseEntity.ok(farmService.updateWebhook(farmId, userId, request));
    }

    @Operation(summary = "농장 삭제 — soft delete (ADMIN)")
    @DeleteMapping("/{farmId}")
    public ResponseEntity<Void> deleteFarm(@AuthenticationPrincipal Long userId,
                                           @PathVariable Long farmId) {
        farmService.deleteFarm(farmId, userId);
        return ResponseEntity.noContent().build();
    }
}
