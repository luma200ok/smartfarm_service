package com.smartfarm.service.controller;

import com.smartfarm.service.dto.MemberResponse;
import com.smartfarm.service.dto.MemberRoleUpdateRequest;
import com.smartfarm.service.service.FarmMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "FarmMember", description = "농장 멤버 API")
@RestController
@RequestMapping("/api/farms/{farmId}/members")
@RequiredArgsConstructor
public class FarmMemberController {

    private final FarmMemberService farmMemberService;

    @Operation(summary = "멤버 목록 조회 (승인된 멤버) — 승인 대기자는 role=PENDING·pending=true로 함께 노출")
    @GetMapping
    public ResponseEntity<List<MemberResponse>> findMembers(@AuthenticationPrincipal Long userId,
                                                            @PathVariable Long farmId) {
        return ResponseEntity.ok(farmMemberService.findMembers(farmId, userId));
    }

    @Operation(summary = "멤버 역할 변경 (ADMIN, 데모 차단) — 초대 수락자(PENDING) 승인도 이 경로. "
            + "마지막 ADMIN 강등은 F006")
    @PatchMapping("/{memberId}/role")
    public ResponseEntity<MemberResponse> changeMemberRole(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId,
            @PathVariable Long memberId,
            @Valid @RequestBody MemberRoleUpdateRequest request) {
        return ResponseEntity.ok(
                farmMemberService.changeMemberRole(farmId, userId, memberId, request.role()));
    }

    @Operation(summary = "멤버 제거 (ADMIN 또는 본인 — 마지막 ADMIN 제거는 F006, 승인 대기자의 본인 취소도 허용)")
    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal Long userId,
                                             @PathVariable Long farmId,
                                             @PathVariable Long memberId) {
        farmMemberService.removeMember(farmId, userId, memberId);
        return ResponseEntity.noContent().build();
    }
}
