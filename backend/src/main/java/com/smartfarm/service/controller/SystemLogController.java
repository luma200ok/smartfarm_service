package com.smartfarm.service.controller;

import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.dto.SystemLogResponse;
import com.smartfarm.service.entity.SystemLogCategory;
import com.smartfarm.service.service.SystemLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 시스템 로그 API(V24, 이슈 #129-A) — append-only, 조회 전용(수정·삭제 없음). */
@Tag(name = "SystemLog", description = "시스템 로그 API")
@RestController
@RequestMapping("/api/farms/{farmId}/system-logs")
@RequiredArgsConstructor
public class SystemLogController {

    private final SystemLogService systemLogService;

    @Operation(summary = "시스템 로그 목록 조회 (멤버, category 필터, 페이지네이션) — append-only")
    @GetMapping
    public ResponseEntity<PageResponse<SystemLogResponse>> list(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId,
            @RequestParam(required = false) SystemLogCategory category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(systemLogService.list(farmId, userId, category, pageable));
    }
}
