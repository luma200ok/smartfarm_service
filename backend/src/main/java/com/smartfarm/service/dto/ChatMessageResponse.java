package com.smartfarm.service.dto;

import com.smartfarm.service.entity.ChatMessage;
import java.time.LocalDateTime;
import java.util.List;

/** contract §4.7 — sources는 저장된 JSONB를 파싱한 값(ChatService가 채워 넣는다). */
public record ChatMessageResponse(
        Long id,
        String question,
        String answer,
        List<String> sources,
        boolean fallback,
        Long createdBy,
        LocalDateTime createdAt
) {

    public static ChatMessageResponse of(ChatMessage message, List<String> sources) {
        return new ChatMessageResponse(
                message.getId(),
                message.getQuestion(),
                message.getAnswer(),
                sources,
                message.isFallback(),
                message.getCreatedBy(),
                message.getCreatedAt()
        );
    }
}
