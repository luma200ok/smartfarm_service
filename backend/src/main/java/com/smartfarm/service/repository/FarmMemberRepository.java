package com.smartfarm.service.repository;

import com.smartfarm.service.dto.FarmSummaryResponse;
import com.smartfarm.service.dto.MemberResponse;
import com.smartfarm.service.entity.FarmMember;
import com.smartfarm.service.entity.FarmRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FarmMemberRepository extends JpaRepository<FarmMember, Long> {

    Optional<FarmMember> findByFarmIdAndUserId(Long farmId, Long userId);

    /** farm 스코프 필수 — memberId 단독 조회 금지(cross-tenant IDOR 차단) */
    Optional<FarmMember> findByIdAndFarmId(Long id, Long farmId);

    boolean existsByFarmIdAndUserId(Long farmId, Long userId);

    long countByFarmId(Long farmId);

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

    /** 회원 탈퇴 시 전체 멤버십 삭제용 — soft delete된 농장의 잔존 행도 함께 정리한다. */
    List<FarmMember> findAllByUserId(Long userId);
}
