package com.smartfarm.service.controller;

import com.smartfarm.service.dto.AlarmRuleRequest;
import com.smartfarm.service.dto.AlarmRuleResponse;
import com.smartfarm.service.dto.AlarmRuleUpdateRequest;
import com.smartfarm.service.service.AlarmRuleService;
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
 * 알람 규칙 API(이슈 #118) — 농장당 규칙 상한이 작아(50건) 페이지네이션을 두지 않는다.
 */
@Tag(name = "AlarmRule", description = "알람 규칙 API")
@RestController
@RequestMapping("/api/farms/{farmId}/alarm-rules")
@RequiredArgsConstructor
public class AlarmRuleController {

    private final AlarmRuleService alarmRuleService;

    @Operation(summary = "알람 규칙 목록 조회 (멤버)")
    @GetMapping
    public ResponseEntity<List<AlarmRuleResponse>> findRules(@AuthenticationPrincipal Long userId,
                                                              @PathVariable Long farmId) {
        return ResponseEntity.ok(alarmRuleService.findRules(farmId, userId));
    }

    @Operation(summary = "알람 규칙 단건 조회 (멤버)")
    @GetMapping("/{ruleId}")
    public ResponseEntity<AlarmRuleResponse> findRule(@AuthenticationPrincipal Long userId,
                                                       @PathVariable Long farmId,
                                                       @PathVariable Long ruleId) {
        return ResponseEntity.ok(alarmRuleService.findRule(farmId, userId, ruleId));
    }

    @Operation(summary = "알람 규칙 생성 (ADMIN, 데모 차단)")
    @PostMapping
    public ResponseEntity<AlarmRuleResponse> createRule(@AuthenticationPrincipal Long userId,
                                                         @PathVariable Long farmId,
                                                         @Valid @RequestBody AlarmRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(alarmRuleService.createRule(farmId, userId, request));
    }

    @Operation(summary = "알람 규칙 부분 수정 (ADMIN, 데모 차단) — 소스·지표·비교조건·스코프는 변경 불가")
    @PatchMapping("/{ruleId}")
    public ResponseEntity<AlarmRuleResponse> updateRule(@AuthenticationPrincipal Long userId,
                                                         @PathVariable Long farmId,
                                                         @PathVariable Long ruleId,
                                                         @Valid @RequestBody AlarmRuleUpdateRequest request) {
        return ResponseEntity.ok(alarmRuleService.updateRule(farmId, userId, ruleId, request));
    }

    @Operation(summary = "알람 규칙 삭제 (ADMIN, 데모 차단) — 열려 있던 알람은 자동 해소")
    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRule(@AuthenticationPrincipal Long userId,
                                            @PathVariable Long farmId,
                                            @PathVariable Long ruleId) {
        alarmRuleService.deleteRule(farmId, userId, ruleId);
        return ResponseEntity.noContent().build();
    }
}
