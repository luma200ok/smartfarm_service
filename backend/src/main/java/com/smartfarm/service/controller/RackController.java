package com.smartfarm.service.controller;

import com.smartfarm.service.dto.RackResponse;
import com.smartfarm.service.dto.RackUpdateRequest;
import com.smartfarm.service.service.RackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Rack", description = "랙 API")
@RestController
@RequestMapping("/api/farms/{farmId}/racks")
@RequiredArgsConstructor
public class RackController {

    private final RackService rackService;

    @Operation(summary = "랙 부분 수정 (ADMIN, 데모 차단) — levelCount 축소 시 하위 장비 잔존이면 409 R004")
    @PatchMapping("/{rackId}")
    public ResponseEntity<RackResponse> updateRack(@AuthenticationPrincipal Long userId,
                                                    @PathVariable Long farmId,
                                                    @PathVariable Long rackId,
                                                    @Valid @RequestBody RackUpdateRequest request) {
        return ResponseEntity.ok(rackService.updateRack(farmId, userId, rackId, request));
    }

    @Operation(summary = "랙 삭제 — soft delete, 하위 층 함께 (ADMIN, 데모 차단)")
    @DeleteMapping("/{rackId}")
    public ResponseEntity<Void> deleteRack(@AuthenticationPrincipal Long userId,
                                            @PathVariable Long farmId,
                                            @PathVariable Long rackId) {
        rackService.deleteRack(farmId, userId, rackId);
        return ResponseEntity.noContent().build();
    }
}
