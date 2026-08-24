package com.smartfarm.service.entity;

/**
 * 농장 멤버 역할 4단계 (이슈 #122, contract §2).
 *
 * <ul>
 *   <li>{@link #ADMIN} — 구조 CRUD(농장·존·랙·장비·임계값·알람규칙·웹훅) · 초대 발급 ·
 *       멤버 관리/역할 변경 · 농장 삭제. 구 {@code OWNER}의 권한 전량을 승계한다.</li>
 *   <li>{@link #OPERATOR} — 조회 전량 + 제어(모드 변경·큐 적재/취소·적용) + 비상 정지 +
 *       알람 확인/처리 + 콘텐츠 작성(작업일지·양액 레시피). 구 {@code MEMBER}를 승계한다.</li>
 *   <li>{@link #VIEWER} — 조회만. 제어·구조 변경·작성 전부 차단.</li>
 *   <li>{@link #PENDING} — 가입 승인 대기. farm-scoped 접근이 <b>전부</b> 차단된다
 *       (멤버 목록에는 대기자로 보인다).</li>
 * </ul>
 *
 * <p><b>서열은 {@link #rank}로 명시한다 — {@code ordinal()}에 기대지 않는다.</b> enum 상수 선언
 * 순서는 리팩터링으로 언제든 바뀔 수 있는데, 그 순간 권한 비교가 조용히 뒤집혀 인가 구멍이 된다.
 *
 * <p><b>V21 이관</b>: 구 {@code OWNER}→{@code ADMIN}, 구 {@code MEMBER}→{@code OPERATOR}.
 * MEMBER를 VIEWER로 내리면 <b>기능 회귀</b>다 — 현행 MEMBER는 제어(changeMode·enqueueChange·
 * apply)를 할 수 있었다.
 */
public enum FarmRole {

    ADMIN(3),
    OPERATOR(2),
    VIEWER(1),
    PENDING(0);

    /** 권한 서열(클수록 강함) — 선언 순서와 무관하게 비교 기준을 고정한다. */
    private final int rank;

    FarmRole(int rank) {
        this.rank = rank;
    }

    /** 이 역할이 {@code required} 이상인가(ADMIN은 OPERATOR 요구를 통과한다). */
    public boolean atLeast(FarmRole required) {
        return this.rank >= required.rank;
    }

    /**
     * 승인된 멤버인가 — farm-scoped 표면의 최소 자격이다({@code FarmAccessGuard#requireMember}).
     *
     * <p>{@code != PENDING}이 아니라 <b>{@code atLeast(VIEWER)}로 표현한다</b>(이슈 #122 리뷰).
     * 전자는 "PENDING만 아니면 통과"라, 나중에 PENDING보다 더 낮은 상태(예: 정지·차단)가 추가되면
     * 그 역할이 <b>조용히 활성 멤버로 취급된다</b> — 서열 기반으로 쓰면 새 하위 역할이 기본적으로
     * 차단되는 쪽(fail-closed)으로 붙는다.
     */
    public boolean isActive() {
        return atLeast(VIEWER);
    }
}
