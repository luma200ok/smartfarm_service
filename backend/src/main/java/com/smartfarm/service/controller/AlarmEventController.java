package com.smartfarm.service.controller;

import com.smartfarm.service.dto.AlarmAcknowledgeAllResponse;
import com.smartfarm.service.dto.AlarmEventDetailResponse;
import com.smartfarm.service.dto.AlarmEventResponse;
import com.smartfarm.service.dto.AlarmMemoRequest;
import com.smartfarm.service.dto.AlarmStatsResponse;
import com.smartfarm.service.dto.AlarmUnacknowledgedCountResponse;
import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.entity.AlarmEventStatus;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.service.AlarmEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AlarmEvent", description = "알람 이벤트 API")
@RestController
@RequestMapping("/api/farms/{farmId}/alarm-events")
@RequiredArgsConstructor
public class AlarmEventController {

    private final AlarmEventService alarmEventService;

    @Operation(summary = "알람 이벤트 목록 조회 (멤버, status·severity 필터, 페이지네이션)")
    @GetMapping
    public ResponseEntity<PageResponse<AlarmEventResponse>> listAlarmEvents(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId,
            @RequestParam(required = false) AlarmEventStatus status,
            @RequestParam(required = false) AlarmSeverity severity,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(alarmEventService.list(farmId, userId, status, severity, pageable));
    }

    @Operation(summary = "알람 통계 — 최근 N일 severity별 건수 + 평균 처리시간(분)")
    @GetMapping("/stats")
    public ResponseEntity<AlarmStatsResponse> stats(@AuthenticationPrincipal Long userId,
                                                      @PathVariable Long farmId,
                                                      @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(alarmEventService.stats(farmId, userId, days));
    }

    @Operation(summary = "미확인 알람 건수 (TopBar 배지용 경량 조회)")
    @GetMapping("/unacknowledged-count")
    public ResponseEntity<AlarmUnacknowledgedCountResponse> unacknowledgedCount(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId) {
        return ResponseEntity.ok(alarmEventService.unacknowledgedCount(farmId, userId));
    }

    @Operation(summary = "미확인 알람 전체 확인 처리 (멤버)")
    @PostMapping("/acknowledge-all")
    public ResponseEntity<AlarmAcknowledgeAllResponse> acknowledgeAll(@AuthenticationPrincipal Long userId,
                                                                       @PathVariable Long farmId) {
        return ResponseEntity.ok(alarmEventService.acknowledgeAll(farmId, userId));
    }

    @Operation(summary = "알람 이벤트 상세 + 타임라인 조회 (멤버)")
    @GetMapping("/{alarmEventId}")
    public ResponseEntity<AlarmEventDetailResponse> getAlarmEvent(@AuthenticationPrincipal Long userId,
                                                                    @PathVariable Long farmId,
                                                                    @PathVariable Long alarmEventId) {
        return ResponseEntity.ok(alarmEventService.get(farmId, userId, alarmEventId));
    }

    @Operation(summary = "알람 확인 처리 (멤버) — UNACKNOWLEDGED → ACKNOWLEDGED")
    @PatchMapping("/{alarmEventId}/acknowledge")
    public ResponseEntity<AlarmEventResponse> acknowledge(@AuthenticationPrincipal Long userId,
                                                            @PathVariable Long farmId,
                                                            @PathVariable Long alarmEventId) {
        return ResponseEntity.ok(alarmEventService.acknowledge(farmId, userId, alarmEventId));
    }

    @Operation(summary = "알람 조치 완료 처리 (멤버) — ACKNOWLEDGED → RESOLVED")
    @PostMapping("/{alarmEventId}/resolve")
    public ResponseEntity<AlarmEventResponse> resolve(@AuthenticationPrincipal Long userId,
                                                        @PathVariable Long farmId,
                                                        @PathVariable Long alarmEventId) {
        return ResponseEntity.ok(alarmEventService.resolve(farmId, userId, alarmEventId));
    }

    @Operation(summary = "알람 메모 추가 (멤버) — 상태 전이 없이 타임라인에만 기록")
    @PostMapping("/{alarmEventId}/memo")
    public ResponseEntity<AlarmEventDetailResponse> addMemo(@AuthenticationPrincipal Long userId,
                                                              @PathVariable Long farmId,
                                                              @PathVariable Long alarmEventId,
                                                              @Valid @RequestBody AlarmMemoRequest request) {
        return ResponseEntity.ok(alarmEventService.addMemo(farmId, userId, alarmEventId, request.note()));
    }
}
