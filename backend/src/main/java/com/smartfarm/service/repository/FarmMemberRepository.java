package com.smartfarm.service.repository;

import com.smartfarm.service.dto.FarmSummaryResponse;
import com.smartfarm.service.dto.MemberResponse;
import com.smartfarm.service.entity.FarmMember;
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
}
