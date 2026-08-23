package com.smartfarm.service.controller;

import com.smartfarm.service.dto.ControlApplyRequest;
import com.smartfarm.service.dto.ControlApplyResponse;
import com.smartfarm.service.dto.ControlChangeRequest;
import com.smartfarm.service.dto.ControlChangeResponse;
import com.smartfarm.service.dto.ControlModeRequest;
import com.smartfarm.service.dto.ControlStateResponse;
import com.smartfarm.service.dto.EmergencyStopResponse;
import com.smartfarm.service.service.ControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제어 도메인 API(contract §4.12, 이슈 #100) — 존 단위 제어(모드·목표값·장비 조작·대기 큐·적용)와
 * 농장 단위 비상 정지. 존 스코프와 농장 스코프가 섞여 클래스 매핑은 {@code /api/farms/{farmId}}까지만
 * 잡고 나머지는 메서드 경로로 둔다.
 *
 * <p>모든 응답에 {@code simulated: true}가 실린다 — 실기기가 없어 제어는 §4.11 가상 장비
 * 시뮬레이터에만 작용한다(실제 기기를 제어하는 척하지 않는다).
 */
@Tag(name = "Control", description = "제어 도메인 API — 목표값·장비 조작·적용 대기 큐·비상정지")
@RestController
@RequestMapping("/api/farms/{farmId}")
@RequiredArgsConstructor
public class ControlController {

    private final ControlService controlService;

    @Operation(summary = "존 제어 상태 조회 (멤버) — 모드+목표값 4종+장비 상태+대기 큐+최근 이력")
    @GetMapping("/zones/{zoneId}/control")
    public ResponseEntity<ControlStateResponse> findControlState(@AuthenticationPrincipal Long userId,
                                                                  @PathVariable Long farmId,
                                                                  @PathVariable Long zoneId) {
        return ResponseEntity.ok(controlService.findControlState(farmId, userId, zoneId));
    }

    @Operation(summary = "운전 모드 변경 (멤버, 데모 차단) — 새 모드에서 허용되지 않는 대기 항목은 함께 폐기")
    @PutMapping("/zones/{zoneId}/control/mode")
    public ResponseEntity<ControlStateResponse> changeMode(@AuthenticationPrincipal Long userId,
                                                            @PathVariable Long farmId,
                                                            @PathVariable Long zoneId,
                                                            @Valid @RequestBody ControlModeRequest request) {
        return ResponseEntity.ok(controlService.changeMode(farmId, userId, zoneId, request));
    }

    @Operation(summary = "대기 큐 적재 (멤버, 데모 차단) — 큐에 쌓기만 하고 장비에 즉시 반영하지 않는다")
    @PostMapping("/zones/{zoneId}/control/changes")
    public ResponseEntity<ControlChangeResponse> enqueueChange(@AuthenticationPrincipal Long userId,
                                                                @PathVariable Long farmId,
                                                                @PathVariable Long zoneId,
                                                                @Valid @RequestBody ControlChangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(controlService.enqueueChange(farmId, userId, zoneId, request));
    }

    @Operation(summary = "대기 항목 개별 취소 (작성자 본인 또는 OWNER, 데모 차단)")
    @DeleteMapping("/zones/{zoneId}/control/changes/{changeId}")
    public ResponseEntity<Void> cancelChange(@AuthenticationPrincipal Long userId,
                                              @PathVariable Long farmId,
                                              @PathVariable Long zoneId,
                                              @PathVariable Long changeId) {
        controlService.cancelChange(farmId, userId, zoneId, changeId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "대기 큐 전체 되돌리기 (멤버, 데모 차단)")
    @DeleteMapping("/zones/{zoneId}/control/changes")
    public ResponseEntity<Void> cancelAllChanges(@AuthenticationPrincipal Long userId,
                                                  @PathVariable Long farmId,
                                                  @PathVariable Long zoneId) {
        controlService.cancelAllChanges(farmId, userId, zoneId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "대기 큐 일괄 적용 (멤버, 데모 차단) — expectedChangeIds 낙관적 검증(불일치 시 CT005)")
    @PostMapping("/zones/{zoneId}/control/apply")
    public ResponseEntity<ControlApplyResponse> apply(@AuthenticationPrincipal Long userId,
                                                       @PathVariable Long farmId,
                                                       @PathVariable Long zoneId,
                                                       @Valid @RequestBody ControlApplyRequest request) {
        return ResponseEntity.ok(controlService.apply(farmId, userId, zoneId, request));
    }

    @Operation(summary = "비상 정지 (OWNER, 데모 차단) — 농장 전체 장비 OFF + MANUAL + 대기 큐 전량 폐기")
    @PostMapping("/control/emergency-stop")
    public ResponseEntity<EmergencyStopResponse> emergencyStop(@AuthenticationPrincipal Long userId,
                                                                @PathVariable Long farmId) {
        return ResponseEntity.ok(controlService.emergencyStop(farmId, userId));
    }
}
