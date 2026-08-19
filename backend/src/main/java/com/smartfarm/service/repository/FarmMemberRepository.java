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

    boolean existsByFarmIdAndUserId(Long farmId, Long userId);

    /**
     * 생존 유저 기준 멤버 수 — 목록(findMembersByFarmId)과 동일하게 User join으로 통일해
     * 탈퇴 유저의 잔존 멤버십 행이 memberCount를 부풀리지 않게 한다(유령 멤버 정합).
     */
    @Query("SELECT COUNT(fm) FROM FarmMember fm JOIN User u ON u.id = fm.userId "
            + "WHERE fm.farmId = :farmId")
    long countLiveMembersByFarmId(@Param("farmId") Long farmId);

    /** 내 농장 목록 — Farm @SQLRestriction으로 soft delete 농장은 join에서 제외됨 */
    @Query("SELECT new com.smartfarm.service.dto.FarmSummaryResponse(f.id, f.name, f.cropType, fm.role) "
            + "FROM FarmMember fm JOIN Farm f ON f.id = fm.farmId "
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

    /** 회원 탈퇴 시 초대 무효화 대상 농장 선조회용 프로젝션(soft delete 농장 포함 전체). */
    @Query("SELECT DISTINCT fm.farmId FROM FarmMember fm WHERE fm.userId = :userId")
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
