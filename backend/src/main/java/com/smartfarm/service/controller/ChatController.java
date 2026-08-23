package com.smartfarm.service.controller;

import com.smartfarm.service.dto.ChatMessageResponse;
import com.smartfarm.service.dto.ChatRequest;
import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "AI 챗봇 API (동기 프록시 + 이력, 데모 계정도 허용)")
@RestController
@RequestMapping("/api/farms/{farmId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "챗봇 질의 (멤버, 동기 — 타임아웃 30s)")
    @PostMapping
    public ResponseEntity<ChatMessageResponse> createChat(@AuthenticationPrincipal Long userId,
                                                            @PathVariable Long farmId,
                                                            @Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.createChat(farmId, userId, request));
    }

    @Operation(summary = "챗 이력 목록 조회 (멤버, 최신순)")
    @GetMapping
    public ResponseEntity<PageResponse<ChatMessageResponse>> findChatMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(chatService.findChatMessages(farmId, userId, pageable));
    }
}
