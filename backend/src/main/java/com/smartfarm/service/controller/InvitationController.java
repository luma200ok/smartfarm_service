package com.smartfarm.service.controller;

import com.smartfarm.service.dto.AcceptInvitationRequest;
import com.smartfarm.service.dto.FarmResponse;
import com.smartfarm.service.dto.InvitationResponse;
import com.smartfarm.service.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Invitation", description = "초대 API")
@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @Operation(summary = "초대코드 발급 (OWNER, 만료 72h·재사용 가능)")
    @PostMapping("/api/farms/{farmId}/invitations")
    public ResponseEntity<InvitationResponse> createInvitation(@AuthenticationPrincipal Long userId,
                                                               @PathVariable Long farmId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invitationService.createInvitation(farmId, userId));
    }

    @Operation(summary = "초대 수락 (MEMBER로 합류)")
    @PostMapping("/api/invitations/accept")
    public ResponseEntity<FarmResponse> acceptInvitation(@AuthenticationPrincipal Long userId,
                                                         @Valid @RequestBody AcceptInvitationRequest request) {
        return ResponseEntity.ok(invitationService.acceptInvitation(userId, request));
    }
}
