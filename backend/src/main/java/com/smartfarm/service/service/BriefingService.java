package com.smartfarm.service.service;

import com.smartfarm.service.dto.FarmBriefingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 화면 "오늘 할일" 브리핑(이슈 #129-B) — 새 쿼리를 만들지 않고 기존 서비스 메서드를 재사용해
 * 합산하는 집계 단일 엔드포인트. #126에서 {@code series()}를 재사용해 위험을 없앤 것과 같은 원칙.
 *
 * <p>{@code harvestDueSoon}(수확 예정)은 이 서비스의 응답에 없다 — 근거는
 * {@link FarmBriefingResponse} javadoc 참고(후속 이슈 #130).
 *
 * <p>⚠️ <b>farm 가드가 두 번(사실상 세 번) 호출되는 이유</b>: {@code requireMember}를 이 메서드에서
 * 직접 호출한 뒤, 재사용하는 {@link AlarmEventService#unacknowledgedCount}·{@link DeviceService#summary}가
 * 내부에서 각자 다시 호출한다. 완전히 없앨 수는 없다(재사용하는 두 메서드는 이미 배포된 공개 API의
 * 가드를 그대로 유지해야 하고, 이 서비스만 따로 벗겨낼 수 없다) — 다만 이 클래스가 <b>직접</b>
 * {@link FarmAccessGuard}에 의존해야 {@code ArchitectureRulesTest} 규칙①(farmId 스코프 서비스는
 * FarmAccessGuard에 의존해야 한다)을 통과한다. 비용은 멤버십 조회 쿼리 1회 추가뿐이라 감내한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BriefingService {

    private final FarmAccessGuard farmAccessGuard;
    private final AlarmEventService alarmEventService;
    private final DeviceService deviceService;

    public FarmBriefingResponse briefing(Long farmId, Long userId) {
        farmAccessGuard.requireMember(farmId, userId);
        long actionRequiredCount = alarmEventService.unacknowledgedCount(farmId, userId).count();
        long calibrationDueSoonCount = deviceService.summary(farmId, userId).calibrationDueSoon();
        return new FarmBriefingResponse(actionRequiredCount, calibrationDueSoonCount);
    }
}
