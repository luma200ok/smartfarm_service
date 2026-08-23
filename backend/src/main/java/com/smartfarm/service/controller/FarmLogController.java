package com.smartfarm.service.controller;

import com.smartfarm.service.dto.FarmLogRequest;
import com.smartfarm.service.dto.FarmLogResponse;
import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.service.FarmLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

@Tag(name = "FarmLog", description = "작업일지 API")
@RestController
@RequestMapping("/api/farms/{farmId}/logs")
@RequiredArgsConstructor
public class FarmLogController {

    private final FarmLogService farmLogService;

    @Operation(summary = "작업일지 작성 (멤버, 데모 계정도 허용)")
    @PostMapping
    public ResponseEntity<FarmLogResponse> createLog(@AuthenticationPrincipal Long userId,
                                                       @PathVariable Long farmId,
                                                       @Valid @RequestBody FarmLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(farmLogService.createLog(farmId, userId, request));
    }

    @Operation(summary = "작업일지 목록 조회 (멤버, logDate 내림차순)")
    @GetMapping
    public ResponseEntity<PageResponse<FarmLogResponse>> findLogs(@AuthenticationPrincipal Long userId,
                                                                    @PathVariable Long farmId,
                                                                    @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(farmLogService.findLogs(farmId, userId, pageable));
    }

    @Operation(summary = "작업일지 수정 (작성자 본인만)")
    @PatchMapping("/{logId}")
    public ResponseEntity<FarmLogResponse> updateLog(@AuthenticationPrincipal Long userId,
                                                        @PathVariable Long farmId,
                                                        @PathVariable Long logId,
                                                        @Valid @RequestBody FarmLogRequest request) {
        return ResponseEntity.ok(farmLogService.updateLog(farmId, userId, logId, request));
    }

    @Operation(summary = "작업일지 삭제 (작성자 본인 또는 OWNER)")
    @DeleteMapping("/{logId}")
    public ResponseEntity<Void> deleteLog(@AuthenticationPrincipal Long userId,
                                           @PathVariable Long farmId,
                                           @PathVariable Long logId) {
        farmLogService.deleteLog(farmId, userId, logId);
        return ResponseEntity.noContent().build();
    }
}
