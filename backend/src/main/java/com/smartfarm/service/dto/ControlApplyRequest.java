package com.smartfarm.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 대기 큐 일괄 적용 요청(contract §4.12 — POST /control/apply). {@code expectedChangeIds}는
 * <b>필수</b>다(낙관적 검증, 동시성 1): 클라이언트가 화면에서 보고 있던 큐의 id 집합을 그대로 보내고,
 * 서버가 잠금 하에서 읽은 현재 PENDING 집합과 다르면 CT005로 거부한다 — A가 큐를 보는 사이 B가
 * 항목을 추가·삭제했는데 A의 "적용"이 그것까지 함께 반영하는 사고를 막는다.
 *
 * <p>빈 리스트도 유효한 값이다(= "지금 큐가 비어 있다고 알고 있다") — 실제로 비어 있으면 0건 적용으로
 * 성공하고, 그 사이 누군가 항목을 넣었으면 CT005다. {@code null}만 거부한다.
 *
 * <p>⚠️ <b>크기 상한(리뷰 P1)</b>: PENDING은 존당 최대 {@code MAX_PENDING_PER_ZONE}(50)건이라 51개
 * 이상은 어떤 경우에도 낙관적 검증을 통과할 수 없다 — 즉 정상 사용에 영향 없이 막을 수 있다. 상한이
 * 없으면 인증된 멤버 1명이 nginx 본문 상한(16MB, 진단 이미지 때문에 크다)까지 id 배열을 채워 보낼 수
 * 있고, 서비스가 이를 전량 순회 + {@code LinkedHashSet} 복제하면서 박싱 {@code Long} 수백만 개를
 * 할당해(본문 대비 10배 이상 증폭) 힙을 고갈시킨다. 이 레포엔 범용 rate limit이 없다
 * ({@code ChatRateLimiter}는 챗 전용).
 */
public record ControlApplyRequest(

        @NotNull(message = "적용 대상 대기 항목 id 목록은 필수입니다.")
        @Size(max = 50, message = "적용 대상 대기 항목은 50건을 넘을 수 없습니다.")
        List<Long> expectedChangeIds
) {
}
