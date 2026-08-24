package com.smartfarm.service.service;

import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.RackLevelRepository;
import com.smartfarm.service.repository.RackRepository;
import com.smartfarm.service.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알람 규칙 스코프({@code scope_type}+{@code scope_id})가 <b>지금도 그 농장에 살아 있는가</b>를
 * 판정하는 단일 지점(이슈 #118 리뷰 P2-3).
 *
 * <p>{@code alarm_rules.scope_id}는 zone/rack/rack_level을 가리키는 <b>다형 참조</b>라 FK를 걸 수
 * 없고, 세 대상은 전부 {@code @SQLRestriction} soft delete다. 따라서 "그 랙이 아직 있는가"를 DB
 * 제약이 대신 지켜 주지 않는다 — 이 클래스가 유일한 방어선이다. 조회는 전부
 * {@code findByIdAndFarmId}라 <b>농장 경계 재확인</b>(cross-tenant IDOR 차단)도 겸한다.
 *
 * <p>두 호출측이 같은 매핑을 쓰되 실패 처리만 다르다:
 * <ul>
 *   <li>{@link #requireExists} — API 생성 경로. 미소속·미존재는 존재를 유추당하지 않도록
 *       404(R001~R003, §4.10 규약)로 거부한다.</li>
 *   <li>{@link #exists} — 평가 경로. 스케줄러에는 사용자가 없으므로 예외 대신 boolean을 주고,
 *       호출측이 "감시 대상이 사라진 규칙"으로 처리한다.</li>
 * </ul>
 * 매핑을 한 곳에 모으는 이유는 두 경로가 갈라져 <b>API는 막는데 평가는 통과하는</b>(혹은 그 반대)
 * 상태가 생기지 않게 하기 위함이다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmScopeResolver {

    private final ZoneRepository zoneRepository;
    private final RackRepository rackRepository;
    private final RackLevelRepository rackLevelRepository;

    /**
     * 스코프 대상이 그 농장에 살아 있으면 true. {@code FARM} 스코프는 항상 true다 — 농장 자체의
     * soft delete는 평가 대상 조회({@code AlarmRuleRepository#findEnabled}의 Farm 서브쿼리)가 이미
     * 걸러내므로 여기서 중복 확인하지 않는다.
     */
    public boolean exists(Long farmId, AlarmScopeType scopeType, Long scopeId) {
        if (scopeType == AlarmScopeType.FARM) {
            return true;
        }
        if (scopeId == null) {
            return false; // FARM이 아닌데 대상이 없다 — V20 CHECK 제약상 도달 불가하나 방어적으로 거짓
        }
        return switch (scopeType) {
            case ZONE -> zoneRepository.findByIdAndFarmId(scopeId, farmId).isPresent();
            case RACK -> rackRepository.findByIdAndFarmId(scopeId, farmId).isPresent();
            case LEVEL -> rackLevelRepository.findByIdAndFarmId(scopeId, farmId).isPresent();
            case FARM -> true;
        };
    }

    /** 스코프 대상이 없으면 계층별 404(R001 존 · R002 랙 · R003 층)로 거부한다. */
    public void requireExists(Long farmId, AlarmScopeType scopeType, Long scopeId) {
        if (exists(farmId, scopeType, scopeId)) {
            return;
        }
        throw new CustomException(switch (scopeType) {
            case ZONE -> ErrorCode.R001;
            case RACK -> ErrorCode.R002;
            case LEVEL -> ErrorCode.R003;
            case FARM -> ErrorCode.C001; // 도달 불가(FARM은 exists가 항상 true)
        });
    }
}
