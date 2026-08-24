package com.smartfarm.service.service;

import com.smartfarm.service.entity.Farm;
import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmMemberRepository;
import com.smartfarm.service.repository.FarmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 테넌트 가드 — path param {farmId}는 입력값 취급, 매 요청 멤버십 재검증(cross-tenant IDOR 차단).
 *
 * <p>검사 순서(존재 유추 채널 차단):
 * <ol>
 *   <li>멤버십 없음 → 403 F002. 농장 존재 여부와 무관하게 통일 — 미멤버(미존재 farmId 포함)는
 *       항상 같은 응답을 받으므로 farmId 열거로 농장 존재를 유추할 수 없다.</li>
 *   <li>멤버십이 PENDING(승인 대기) → 403 F008. <b>농장 조회보다 먼저</b> 판정한다 — PENDING은
 *       스스로 초대를 수락한 사람이라 농장 존재를 이미 알고 있어 유추 채널이 아니고, 농장 상태와
 *       무관하게 같은 응답을 주는 편이 "승인 대기 중"이라는 사실을 흐리지 않는다.</li>
 *   <li>멤버십은 있으나 농장 soft delete → 404 F001. 전(前) 멤버에게만 노출되므로 유추 채널 아님.</li>
 * </ol>
 *
 * <p><b>3단 가드</b>(이슈 #122) — 아래로 갈수록 강한 권한을 요구하며, 전부 {@link #requireMember}를
 * 먼저 통과시킨다(멤버십·유저 생존·PENDING·농장 생존 검사를 한 곳에 모아 둔다):
 * <ul>
 *   <li>{@link #requireMember} — 승인된 멤버 전원(ADMIN·OPERATOR·VIEWER). 조회 표면.</li>
 *   <li>{@link #requireOperator} — OPERATOR 이상. 제어·비상 정지·알람 확인/처리·콘텐츠 작성.</li>
 *   <li>{@link #requireAdmin} — ADMIN. 구조 CRUD·초대 발급·멤버 관리/역할 변경·농장 삭제.</li>
 * </ul>
 *
 * <p>멤버십 조회는 User join(@SQLRestriction) 기반 — 탈퇴(soft delete) 유저는 잔존 access
 * 토큰·잔존 멤버십 행이 있어도 이 가드를 타는 전 farm-scoped 표면에서 F002로 차단된다
 * (contract 탈퇴 봉쇄 ①). 가드 밖 farm 표면인 내 농장 목록(findMyFarms)도 동일한
 * User join으로 빈 목록을 반환해, farm 표면 전체에서 성립한다.
 */
@Component
@RequiredArgsConstructor
public class FarmAccessGuard {

    private final FarmMemberRepository farmMemberRepository;
    private final FarmRepository farmRepository;

    public record FarmAccess(Farm farm, FarmMember membership) {
    }

    /**
     * 승인된 멤버 전원(ADMIN·OPERATOR·VIEWER) — farm-scoped 표면의 최소 자격.
     *
     * <p>⚠️ {@code PENDING} 거부(F008)는 이 한 곳에만 있고 <b>모든 farm-scoped 엔드포인트에
     * 파급</b>된다(이슈 #122). 강한 가드들이 전부 이 메서드를 먼저 통과하므로, PENDING 차단을
     * 여기서 한 번 하면 조회·제어·구조 변경 전 표면에서 성립한다.
     */
    public FarmAccess requireMember(Long farmId, Long userId) {
        FarmMember membership = farmMemberRepository.findLiveByFarmIdAndUserId(farmId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.F002));
        if (!membership.getRole().isActive()) {
            throw new CustomException(ErrorCode.F008);
        }
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.F001));
        return new FarmAccess(farm, membership);
    }

    /**
     * OPERATOR 이상(ADMIN 포함) — 제어(모드 변경·큐 적재/취소·적용) · 비상 정지 ·
     * 알람 확인/처리 · 콘텐츠 작성(작업일지·양액 레시피).
     *
     * <p>구 {@code MEMBER}가 하던 제어를 그대로 이어받는 자리다 — V21이 MEMBER를 OPERATOR로
     * 이관하므로 기존 사용자의 제어 권한은 유지된다.
     */
    public FarmAccess requireOperator(Long farmId, Long userId) {
        FarmAccess access = requireMember(farmId, userId);
        if (!access.membership().getRole().atLeast(FarmRole.OPERATOR)) {
            throw new CustomException(ErrorCode.F007);
        }
        return access;
    }

    /**
     * ADMIN 전용 — 구조 CRUD(농장·존·랙·장비·임계값·알람규칙·웹훅) · 초대 발급 ·
     * 멤버 관리/역할 변경 · 농장 삭제. 구 {@code requireOwner}의 후신(F003 의미 재정의).
     */
    public FarmAccess requireAdmin(Long farmId, Long userId) {
        FarmAccess access = requireMember(farmId, userId);
        if (!access.membership().getRole().atLeast(FarmRole.ADMIN)) {
            throw new CustomException(ErrorCode.F003);
        }
        return access;
    }
}
