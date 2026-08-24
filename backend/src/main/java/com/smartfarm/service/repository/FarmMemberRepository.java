package com.smartfarm.service.repository;

import com.smartfarm.service.dto.FarmSummaryResponse;
import com.smartfarm.service.dto.MemberResponse;
import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FarmMemberRepository extends JpaRepository<FarmMember, Long> {

    /**
     * FarmAccessGuard 전용 — 멤버십과 유저 생존을 한 쿼리로 검증한다(추가 라운드트립 0).
     * User를 join하면 @SQLRestriction(deleted_at IS NULL)이 적용되어, 탈퇴(soft delete)
     * 유저의 잔존 멤버십 행(탈퇴↔멤버십 생성 race 등)이 남아 있어도 조회되지 않는다 → F002.
     * (2026-08-20 보안 리뷰 P1: 잔존 access 토큰의 farm 표면 접근 봉쇄 — contract 탈퇴 봉쇄 ①)
     */
    @Query("SELECT fm FROM FarmMember fm JOIN User u ON u.id = fm.userId "
            + "WHERE fm.farmId = :farmId AND fm.userId = :userId")
    Optional<FarmMember> findLiveByFarmIdAndUserId(@Param("farmId") Long farmId,
                                                   @Param("userId") Long userId);

    /** farm 스코프 필수 — memberId 단독 조회 금지(cross-tenant IDOR 차단) */
    Optional<FarmMember> findByIdAndFarmId(Long id, Long farmId);

    /**
     * 역할만 <b>스칼라로</b> 재조회한다 — "마지막 ADMIN 보호"(F006) 판정 전용(이슈 #122 리뷰 P2-1).
     *
     * <p>⚠️ <b>왜 엔티티가 아니라 스칼라인가</b>: {@link #findByIdAndFarmId}는 SQL을 실제로 날려
     * fresh row를 읽어도, 그 id의 엔티티가 이미 영속성 컨텍스트에 관리 중이면 <b>기존 인스턴스를
     * 반환하고 방금 읽은 상태를 버린다</b>(JPA 엔티티 동일성 보장). 본인 탈퇴·본인 역할 변경처럼
     * 대상이 요청자 자신인 경로에서는 가드가 이미 그 행을 로드해 두었으므로, 잠금을 잡은 뒤
     * 다시 읽어도 <b>잠금 이전 스냅샷</b>이 나온다. {@code @Lock}을 붙여도 마찬가지다 — 버전 없는
     * 관리 엔티티는 잠금 SQL을 쏴도 상태가 갱신되지 않는다.
     *
     * <p>그 결과 다음 인터리빙에서 마지막 관리자가 빠져나갈 수 있었다(A=ADMIN, B=OPERATOR):
     * ① B가 본인 탈퇴 요청 → 가드가 B를 role=OPERATOR로 로드(잠금 전) ② A가 B를 ADMIN으로 승격
     * ③ A가 자신을 강등(잠금 안 count=2라 통과) ④ B의 트랜잭션이 락을 얻고 <b>stale OPERATOR</b>로
     * 판정 → F006을 건너뛰고 삭제 → <b>ADMIN 0명, 복구 불가</b>.
     *
     * <p>스칼라 프로젝션은 엔티티 캐시를 타지 않으므로 항상 DB의 현재 값을 돌려준다.
     * 반드시 농장 행 잠금을 잡은 뒤 호출한다.
     */
    @Query("SELECT fm.role FROM FarmMember fm WHERE fm.id = :id AND fm.farmId = :farmId")
    Optional<FarmRole> findRoleByIdAndFarmId(@Param("id") Long id, @Param("farmId") Long farmId);

    boolean existsByFarmIdAndUserId(Long farmId, Long userId);

    /**
     * 생존 유저 기준 멤버 수 — 목록(findMembersByFarmId)과 동일하게 User join으로 통일해
     * 탈퇴 유저의 잔존 멤버십 행이 memberCount를 부풀리지 않게 한다(유령 멤버 정합).
     */
    @Query("SELECT COUNT(fm) FROM FarmMember fm JOIN User u ON u.id = fm.userId "
            + "WHERE fm.farmId = :farmId")
    long countLiveMembersByFarmId(@Param("farmId") Long farmId);

    /**
     * 농장의 특정 역할 생존 멤버 수 — "마지막 ADMIN 보호"(F006) 판정용(이슈 #122).
     *
     * <p>{@code countLiveMembersByFarmId}와 같은 User join이다: 탈퇴(soft delete) 유저의 잔존
     * ADMIN 멤버십 행이 관리자 수를 부풀리면, 실제로는 관리 불능인 농장에서 마지막 관리자의
     * 강등·제거가 통과해 버린다.
     *
     * <p>⚠️ 호출자는 <b>농장 행을 잠근 뒤</b> 이 쿼리를 실행해야 한다. "세어 보고 → 바꾸기"는
     * check-then-act라, 잠금이 없으면 두 ADMIN이 서로를 동시에 강등할 때 양쪽 모두 "관리자 2명"을
     * 보고 통과해 관리자가 0명이 된다.
     */
    @Query("SELECT COUNT(fm) FROM FarmMember fm JOIN User u ON u.id = fm.userId "
            + "WHERE fm.farmId = :farmId AND fm.role = :role")
    long countLiveMembersByFarmIdAndRole(@Param("farmId") Long farmId, @Param("role") FarmRole role);

    /**
     * 내 농장 목록 — Farm/User 양쪽 @SQLRestriction으로 soft delete 농장·탈퇴 유저의
     * 잔존 멤버십 행은 join에서 제외됨(탈퇴 유저의 농장 요약 노출 차단, 가드 3곳과 동일 패턴).
     */
    @Query("SELECT new com.smartfarm.service.dto.FarmSummaryResponse(f.id, f.name, f.cropType, fm.role) "
            + "FROM FarmMember fm JOIN Farm f ON f.id = fm.farmId JOIN User u ON u.id = fm.userId "
            + "WHERE fm.userId = :userId ORDER BY f.id ASC")
    List<FarmSummaryResponse> findMyFarms(@Param("userId") Long userId);

    @Query("SELECT new com.smartfarm.service.dto.MemberResponse(fm.id, u.id, u.nickname, fm.role, fm.joinedAt) "
            + "FROM FarmMember fm JOIN User u ON u.id = fm.userId "
            + "WHERE fm.farmId = :farmId ORDER BY fm.joinedAt ASC, fm.id ASC")
    List<MemberResponse> findMembersByFarmId(@Param("farmId") Long farmId);

    /**
     * 살아있는 농장 기준 role 멤버십 존재 여부 — 회원 탈퇴 가드(A006)용.
     * farm soft delete는 farm_members 행을 지우지 않으므로, 단순 role 조회로 검사하면
     * "농장 삭제 후 탈퇴"가 영구히 막힌다 — Farm을 join해 @SQLRestriction으로
     * 삭제된 농장의 잔존 멤버십을 제외한다(findMyFarms와 동일 패턴).
     */
    @Query("SELECT COUNT(fm) > 0 FROM FarmMember fm JOIN Farm f ON f.id = fm.farmId "
            + "WHERE fm.userId = :userId AND fm.role = :role")
    boolean existsLiveFarmMembershipByUserIdAndRole(@Param("userId") Long userId,
                                                    @Param("role") FarmRole role);

    /**
     * 회원 탈퇴 시 초대 무효화 대상 농장 선조회용 프로젝션(soft delete 농장 포함 전체).
     * ORDER BY farmId — 동시 탈퇴 간 초대 무효화 UPDATE의 락 획득 순서를 결정화해
     * 교차 대기(데드락) 여지를 없앤다.
     */
    @Query("SELECT DISTINCT fm.farmId FROM FarmMember fm WHERE fm.userId = :userId "
            + "ORDER BY fm.farmId ASC")
    List<Long> findFarmIdsByUserId(@Param("userId") Long userId);

    /**
     * 회원 탈퇴 시 전체 멤버십 벌크 삭제 — soft delete된 농장의 잔존 행도 함께 정리한다.
     * select-then-delete 대신 벌크 DELETE라 동시 탈퇴의 패자도 0건 삭제로 조용히 수렴한다
     * (stale 엔티티 삭제 실패로 인한 500 여지 제거).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FarmMember fm WHERE fm.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    /** 테스트 검증용(탈퇴 후 잔존 행 0건 확인 등) */
    List<FarmMember> findAllByUserId(Long userId);
}
