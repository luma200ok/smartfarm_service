package com.smartfarm.service.controller;

import com.smartfarm.service.dto.MemberResponse;
import com.smartfarm.service.service.FarmMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "FarmMember", description = "농장 멤버 API")
@RestController
@RequestMapping("/api/farms/{farmId}/members")
@RequiredArgsConstructor
public class FarmMemberController {

    private final FarmMemberService farmMemberService;

    @Operation(summary = "멤버 목록 조회 (멤버)")
    @GetMapping
    public ResponseEntity<List<MemberResponse>> findMembers(@AuthenticationPrincipal Long userId,
                                                            @PathVariable Long farmId) {
        return ResponseEntity.ok(farmMemberService.findMembers(farmId, userId));
    }

    @Operation(summary = "멤버 제거 (OWNER 또는 본인 — OWNER 본인 탈퇴는 F006)")
    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal Long userId,
                                             @PathVariable Long farmId,
                                             @PathVariable Long memberId) {
        farmMemberService.removeMember(farmId, userId, memberId);
        return ResponseEntity.noContent().build();
    }
}
