package com.smartfarm.service.repository;

import com.smartfarm.service.entity.Farm;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FarmRepository extends JpaRepository<Farm, Long> {

    /**
     * 농장 단위 직렬화 잠금(SELECT ... FOR UPDATE — #91 TOCTOU 교훈, {@code ControlModeRepository}의
     * 존 단위 잠금과 같은 관용구).
     *
     * <p>쓰는 경로는 두 갈래다. ① 농장당 리소스 <b>생성 상한</b>을 강제하는 쓰기 경로.
     * ② 농장의 <b>관리자 수</b>를 바꾸는 경로(이슈 #122 — 역할 변경·멤버 제거). 둘 다 아래와 같은
     * check-then-act이고, 같은 농장 행을 잡으므로 서로에 대해서도 직렬화된다.
     *
     * <p>①의 근거: 상한 판정이 "세어 보고 → 저장"인
     * check-then-act라, 잠금 없이는 병렬 요청이 전부 검사를 통과해 상한을 넘긴다. 알람 규칙 상한은
     * UX 제한이 아니라 <b>자원 방어선</b>이다 — 규칙 1개가 매 틱 조회 1~2개를 유발하고 스케줄러는 전
     * 농장의 규칙을 한 루프로 돌기 때문에, 한 농장의 초과가 다른 농장의 알람 지연으로 번진다.
     *
     * <p>{@code @SQLRestriction}이 적용되므로 soft delete된 농장은 여기서 빈 Optional이다.
     *
     * <p><b>데드락</b>: 이 잠금을 쓰는 경로는 농장 행 <b>하나</b>만 명시적으로 잡는다(제어 도메인은
     * {@code control_modes} 행을 잡는 별개 경로다). 다만 이슈 #122 이후
     * {@code FarmMemberService#removeMember}는 이 잠금을 <b>보유한 채</b> {@code farm_members}
     * DELETE와 {@code invitations} UPDATE로 행 잠금을 추가 취득한다 — "농장 행 하나만 잡는다"는
     * 더 이상 정확한 서술이 아니다.
     *
     * <p>그럼에도 순환 대기는 성립하지 않는다: 취득 순서가 항상 <b>farms → (farm_members,
     * invitations)</b> 한 방향이고, 역방향으로 가는 경로가 없기 때문이다. {@code invitations}를
     * 건드리는 다른 경로인 회원 탈퇴({@code UserService#withdraw})는 <b>users</b> 행을 잡을 뿐
     * farms 행을 잡지 않으므로, farms 잠금을 사이에 둔 사이클이 만들어지지 않는다(동시 탈퇴 간의
     * 교차 대기는 {@code findFarmIdsByUserId}의 farmId ASC 정렬이 이미 막고 있다).
     * <b>farms 잠금을 쓰는 경로에 users 행 잠금을 추가하면 이 성질이 깨진다</b> — 그때는 취득
     * 순서를 전역으로 다시 정해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Farm f WHERE f.id = :farmId")
    Optional<Farm> findByIdForUpdate(@Param("farmId") Long farmId);
}
