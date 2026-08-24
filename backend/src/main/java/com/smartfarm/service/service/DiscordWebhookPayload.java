package com.smartfarm.service.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 디스코드 웹훅 payload — {@code content}와 <b>멘션 억제</b>만 담는 최소 스키마.
 *
 * <p>⚠️ {@code allowed_mentions.parse = []}가 이 타입의 존재 이유다(#118 보안 리뷰 P3-2).
 * 두 노티파이어의 content에는 <b>사용자 입력이 그대로 합류</b>한다 — 알람 쪽은 규칙 이름(50자)과
 * 장비 이름, 처방 쪽은 농장 이름과 질문 원문이다. 디스코드는 content 안의 {@code @everyone}/
 * {@code @here}/역할 멘션을 <b>기본으로 해석</b>하므로, 규칙 이름에 그걸 적어 두면 이탈이 날 때마다
 * 그 농장 채널 전원에게 알림이 울리는 멘션 폭탄이 된다. 빈 {@code parse} 배열은 "어떤 멘션도
 * 해석하지 말라"는 뜻이라, 문자열은 그대로 보이되 알림은 울리지 않는다.
 *
 * <p>{@code EnvThresholdWebhookNotifier}와 {@code PrescriptionWebhookNotifier}는 (기존 테스트
 * 회귀를 피하려고) 발송 로직을 공유하지 않지만, <b>이 보안 통제만은 한 곳에 둔다</b> — 각자 복사해
 * 두면 한쪽만 고쳐지는 순간 조용히 뚫린다.
 */
record DiscordWebhookPayload(
        String content,
        @JsonProperty("allowed_mentions") AllowedMentions allowedMentions
) {

    private static final AllowedMentions SUPPRESS_ALL = new AllowedMentions(List.of());

    /** 멘션을 전혀 해석하지 않는 payload. */
    static DiscordWebhookPayload of(String content) {
        return new DiscordWebhookPayload(content, SUPPRESS_ALL);
    }

    /** {@code parse: []} = @everyone·@here·역할·유저 멘션 전부 미해석. */
    record AllowedMentions(List<String> parse) {
    }
}
