package com.smartfarm.service.controller;

import com.smartfarm.service.dto.ScheduleRequest;
import com.smartfarm.service.dto.ScheduleResponse;
import com.smartfarm.service.dto.ScheduleUpdateRequest;
import com.smartfarm.service.service.ScheduleService;
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

/**
 * 스케줄·자동화 규칙 API 골격(이슈 #129-C) — 농장당 상한이 작아(50건) 페이지네이션을 두지 않는다.
 *
 * <p>⚠️ <b>저장만 한다 — 실행하지 않는다.</b> 이 API로 만든 스케줄은 어떤 트리거도 갖지 않는다
 * (실행 경로는 이 이슈의 범위 밖 — {@code Schedule} 엔티티 주석 참고). 디자인이 미확정인 상태에서
 * 실행까지 만들면 나중에 버려질 수 있어 골격만 둔다.
 */
@Tag(name = "Schedule", description = "스케줄·자동화 규칙 API — 저장만 하고 실행하지 않는다")
@RestController
@RequestMapping("/api/farms/{farmId}/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(summary = "스케줄 목록 조회 (멤버)")
    @GetMapping
    public ResponseEntity<List<ScheduleResponse>> findAll(@AuthenticationPrincipal Long userId,
                                                            @PathVariable Long farmId) {
        return ResponseEntity.ok(scheduleService.findAll(farmId, userId));
    }

    @Operation(summary = "스케줄 단건 조회 (멤버)")
    @GetMapping("/{scheduleId}")
    public ResponseEntity<ScheduleResponse> findOne(@AuthenticationPrincipal Long userId,
                                                      @PathVariable Long farmId,
                                                      @PathVariable Long scheduleId) {
        return ResponseEntity.ok(scheduleService.findOne(farmId, userId, scheduleId));
    }

    @Operation(summary = "스케줄 생성 (ADMIN, 데모 차단) — 저장만 하고 실행하지 않는다"
            + "(cron 형식 검증, 농장당 50건 상한)")
    @PostMapping
    public ResponseEntity<ScheduleResponse> create(@AuthenticationPrincipal Long userId,
                                                     @PathVariable Long farmId,
                                                     @Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.create(farmId, userId, request));
    }

    @Operation(summary = "스케줄 부분 수정 (ADMIN, 데모 차단) — zoneId·actionType은 변경 불가")
    @PatchMapping("/{scheduleId}")
    public ResponseEntity<ScheduleResponse> update(@AuthenticationPrincipal Long userId,
                                                     @PathVariable Long farmId,
                                                     @PathVariable Long scheduleId,
                                                     @Valid @RequestBody ScheduleUpdateRequest request) {
        return ResponseEntity.ok(scheduleService.update(farmId, userId, scheduleId, request));
    }

    @Operation(summary = "스케줄 삭제 (ADMIN, 데모 차단)")
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long farmId,
                                        @PathVariable Long scheduleId) {
        scheduleService.delete(farmId, userId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}
