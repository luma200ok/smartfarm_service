package com.smartfarm.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI 챗봇 질의응답 이력(contract §4.7, 이슈 #54) — ai-server {@code POST /api/chat} 프록시 결과를
 * 농장 단위로 저장하는 기록. 처방(Prescription)과 달리 동기 호출 1건 = 1행이라 상태 기계가 없다
 * (soft delete 없음 — FarmLog와 동일하게 이력 가치가 낮아 단순 저장·조회만 지원).
 *
 * <p>sources는 {@link Prescription#getResult()}와 동일하게 JSONB 문자열로 저장한다(ai-server
 * 응답 {@code sources: string[]}를 그대로 직렬화 — ChatService가 ObjectMapper로 변환).
 */
@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long farmId;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false, length = 500)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    /** ai-server {@code sources: string[]} — JSON 배열 문자열(JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String sources;

    @Column(nullable = false)
    private boolean fallback;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ChatMessage(Long farmId, Long createdBy, String question, String answer, String sources,
                         boolean fallback) {
        this.farmId = farmId;
        this.createdBy = createdBy;
        this.question = question;
        this.answer = answer;
        this.sources = sources;
        this.fallback = fallback;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
