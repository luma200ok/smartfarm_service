package com.smartfarm.service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfarm.service.dto.AiChatResponse;
import com.smartfarm.service.dto.ChatMessageResponse;
import com.smartfarm.service.dto.ChatRequest;
import com.smartfarm.service.dto.PageResponse;
import com.smartfarm.service.entity.ChatMessage;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.ChatMessageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 챗봇 프록시 + 이력(contract §4.7, 이슈 #54). 데모 계정도 허용(체험 핵심 — DemoAccountGuard
 * 미적용, FarmLogService·PrescriptionService와 동일한 콘텐츠 생성 원칙).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final FarmAccessGuard farmAccessGuard;
    private final AiChatClient aiChatClient;
    private final ChatRateLimiter chatRateLimiter;
    private final ChatConcurrencyGuard chatConcurrencyGuard;
    private final ObjectMapper objectMapper;

    /**
     * 동기 질의(200) — ai-server 호출은 외부 I/O라 <b>트랜잭션 밖</b>에서 수행한다(PR #13 회귀 교훈,
     * PrescriptionService.createPrescription과 동일 원칙: 이 메서드가 트랜잭션에 감싸이면 최대 120s
     * 외부 호출 동안 DB 커넥션을 붙들게 된다). 가드 조회·이력 저장 각각의 원자성은 Spring Data JPA의
     * 메서드 단위 자체 트랜잭션이 보장한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatMessageResponse createChat(Long farmId, Long userId, ChatRequest request) {
        // OPERATOR 이상(이슈 #122 리뷰 P2-2) — VIEWER("조회 전용")가 농장 공유 자원을
        // 고갈시킬 수 있는 경로다. 데모 계정은 시드 농장의 ADMIN이라 계약 §4.5의
        // "조회·진단·처방은 데모 허용"에는 영향이 없다.
        // 여기서는 레이트리밋이 농장 단위라, VIEWER 한 명이 농장 전체 챗 쿼터를 소진할 수 있었다.
        farmAccessGuard.requireOperator(farmId, userId);

        // 레이트리밋(handoff #54 + 보안 리뷰 P1-B) — 농장 단위 + 사용자 단위 둘 다 통과해야 한다.
        // DB 왕복이 있는 처방 접수 상한(P004)과 달리 인메모리 카운터. ai-server 호출 전에 걸러
        // 무의미한 LLM 호출을 막는다.
        if (!chatRateLimiter.tryAcquire(farmId, userId)) {
            throw new CustomException(ErrorCode.CH002);
        }

        // 전역 동시성 가드(보안 리뷰 P1-A) — 농장·사용자 수와 무관하게 backend가 동시에 붙잡는
        // 챗 스레드를 상한선으로 묶는다. non-blocking tryAcquire만 사용(대기하면 스레드를 점유한
        // 채로 쌓여 풀 고갈 방어 의미가 없다). 슬롯은 ai-server 호출 구간에만 걸어 최대한 짧게 쥔다.
        if (!chatConcurrencyGuard.tryAcquire()) {
            throw new CustomException(ErrorCode.CH002);
        }
        AiChatResponse aiResponse;
        try {
            aiResponse = aiChatClient.chat(request.question(), "svc:farm:" + farmId);
        } finally {
            chatConcurrencyGuard.release();
        }

        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .farmId(farmId)
                .createdBy(userId)
                .question(request.question())
                .answer(aiResponse.answer())
                .sources(writeSourcesJson(aiResponse.sources()))
                .fallback(aiResponse.fallback())
                .build());

        return ChatMessageResponse.of(saved, aiResponse.sources());
    }

    public PageResponse<ChatMessageResponse> findChatMessages(Long farmId, Long userId, Pageable pageable) {
        farmAccessGuard.requireMember(farmId, userId);
        Page<ChatMessageResponse> page = chatMessageRepository
                .findByFarmIdOrderByCreatedAtDescIdDesc(farmId, pageable)
                .map(this::toResponse);
        return PageResponse.of(page);
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return ChatMessageResponse.of(message, parseSourcesJson(message.getSources()));
    }

    /** 저장 JSON은 이 서비스가 직접 쓴 것 — 파싱 실패는 코딩 오류 수준(C002, PrescriptionService 선례). */
    private List<String> parseSourcesJson(String sourcesJson) {
        try {
            return objectMapper.readValue(sourcesJson, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("저장된 챗 sources JSON 파싱 실패", e);
            throw new CustomException(ErrorCode.C002);
        }
    }

    private String writeSourcesJson(List<String> sources) {
        try {
            return objectMapper.writeValueAsString(sources == null ? List.of() : sources);
        } catch (JsonProcessingException e) {
            log.error("챗 sources JSON 직렬화 실패", e);
            throw new CustomException(ErrorCode.C002);
        }
    }
}
