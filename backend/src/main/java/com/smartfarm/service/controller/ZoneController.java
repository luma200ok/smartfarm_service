package com.smartfarm.service.controller;

import com.smartfarm.service.dto.RackRequest;
import com.smartfarm.service.dto.RackResponse;
import com.smartfarm.service.dto.ZoneRequest;
import com.smartfarm.service.dto.ZoneResponse;
import com.smartfarm.service.dto.ZoneTreeResponse;
import com.smartfarm.service.dto.ZoneUpdateRequest;
import com.smartfarm.service.service.RackService;
import com.smartfarm.service.service.ZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@Tag(name = "Zone", description = "존(동·구역)·랙 구조 API")
@RestController
@RequestMapping("/api/farms/{farmId}/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;
    private final RackService rackService;

    @Operation(summary = "존+랙+층 트리 조회 (멤버, 랙 도면 렌더용 1회 조회)")
    @GetMapping
    public ResponseEntity<ZoneTreeResponse> findZoneTree(@AuthenticationPrincipal Long userId,
                                                          @PathVariable Long farmId) {
        return ResponseEntity.ok(zoneService.findZoneTree(farmId, userId));
    }

    @Operation(summary = "존 생성 (OWNER, 데모 차단)")
    @PostMapping
    public ResponseEntity<ZoneResponse> createZone(@AuthenticationPrincipal Long userId,
                                                    @PathVariable Long farmId,
                                                    @Valid @RequestBody ZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(zoneService.createZone(farmId, userId, request));
    }

    @Operation(summary = "존 부분 수정 (OWNER, 데모 차단)")
    @PatchMapping("/{zoneId}")
    public ResponseEntity<ZoneResponse> updateZone(@AuthenticationPrincipal Long userId,
                                                    @PathVariable Long farmId,
                                                    @PathVariable Long zoneId,
                                                    @Valid @RequestBody ZoneUpdateRequest request) {
        return ResponseEntity.ok(zoneService.updateZone(farmId, userId, zoneId, request));
    }

    @Operation(summary = "존 삭제 — soft delete, 하위 랙·층 함께 (OWNER, 데모 차단)")
    @DeleteMapping("/{zoneId}")
    public ResponseEntity<Void> deleteZone(@AuthenticationPrincipal Long userId,
                                            @PathVariable Long farmId,
                                            @PathVariable Long zoneId) {
        zoneService.deleteZone(farmId, userId, zoneId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "랙 생성 (OWNER, 데모 차단) — levelCount만큼 층 자동 생성")
    @PostMapping("/{zoneId}/racks")
    public ResponseEntity<RackResponse> createRack(@AuthenticationPrincipal Long userId,
                                                    @PathVariable Long farmId,
                                                    @PathVariable Long zoneId,
                                                    @Valid @RequestBody RackRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rackService.createRack(farmId, userId, zoneId, request));
    }
}
