package com.smartfarm.service.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 대기 큐 일괄 적용 요청(contract §4.12 — POST /control/apply). {@code expectedChangeIds}는
 * <b>필수</b>다(낙관적 검증, 동시성 1): 클라이언트가 화면에서 보고 있던 큐의 id 집합을 그대로 보내고,
 * 서버가 잠금 하에서 읽은 현재 PENDING 집합과 다르면 CT005로 거부한다 — A가 큐를 보는 사이 B가
 * 항목을 추가·삭제했는데 A의 "적용"이 그것까지 함께 반영하는 사고를 막는다.
 *
 * <p>빈 리스트도 유효한 값이다(= "지금 큐가 비어 있다고 알고 있다") — 실제로 비어 있으면 0건 적용으로
 * 성공하고, 그 사이 누군가 항목을 넣었으면 CT005다. {@code null}만 거부한다.
 */
public record ControlApplyRequest(

        @NotNull(message = "적용 대상 대기 항목 id 목록은 필수입니다.")
        List<Long> expectedChangeIds
) {
}
