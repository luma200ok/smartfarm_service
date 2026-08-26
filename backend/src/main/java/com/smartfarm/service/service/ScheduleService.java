package com.smartfarm.service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfarm.service.dto.ScheduleRequest;
import com.smartfarm.service.dto.ScheduleResponse;
import com.smartfarm.service.dto.ScheduleUpdateRequest;
import com.smartfarm.service.entity.Schedule;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.repository.ScheduleRepository;
import com.smartfarm.service.repository.ZoneRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스케줄·자동화 규칙 CRUD 골격(V25, 이슈 #129-C) — 조회는 멤버, 쓰기는 ADMIN(구조 변경 성격, §2
 * 권한 체계 — AlarmRuleService·DeviceService와 동일 원칙).
 *
 * <p>⚠️ <b>이 서비스는 "저장"만 한다 — 실행하지 않는다.</b> {@code @Scheduled} 트리거·액션 수행 경로는
 * 이 이슈의 범위 밖이다(자세한 근거는 {@link Schedule} 클래스 주석 참고). {@code cronExpression}은
 * 저장 시점에 형식만 검증한다 — 잘못된 식이 저장되면 나중에 스케줄러가 붙을 때 그제서야 터지기
 * 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    /**
     * 농장당 스케줄 상한(초과 시 SCH002) — {@code AlarmRuleService.MAX_RULES_PER_FARM}·
     * {@code SavedAnalysisService.MAX_ANALYSES_PER_FARM}(둘 다 50)과 동일한 #91 리소스 생성 상한
     * 정책을 따른다.
     */
    static final int MAX_SCHEDULES_PER_FARM = 50;

    private final ScheduleRepository scheduleRepository;
    private final FarmRepository farmRepository;
    private final ZoneRepository zoneRepository;
    private final FarmAccessGuard farmAccessGuard;
    private final DemoAccountGuard demoAccountGuard;
    private final ObjectMapper objectMapper;

    public List<ScheduleResponse> findAll(Long farmId, Long userId) {
        farmAccessGuard.requireMember(farmId, userId);
        return scheduleRepository.findByFarmIdOrderByIdAsc(farmId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ScheduleResponse findOne(Long farmId, Long userId, Long scheduleId) {
        farmAccessGuard.requireMember(farmId, userId);
        return toResponse(findOrThrow(farmId, scheduleId));
    }

    @Transactional
    public ScheduleResponse create(Long farmId, Long userId, ScheduleRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireAdmin(farmId, userId);

        validateCron(request.cronExpression());
        // zoneId 소속 검증 — 그 존이 이 농장 소속인지 재확인한다(cross-tenant IDOR 차단, 미소속은
        // 존재를 유추당하지 않도록 404 R001 — DeviceService#resolveLocation과 동일 원칙).
        validateZone(farmId, request.zoneId());

        // ⚠️ 상한 판정은 농장 행을 잠근 뒤에 한다(AlarmRuleService.createRule 관용구 재사용 —
        // #91 TOCTOU 교훈). "세어 보고 → 저장"은 check-then-act라 잠금이 없으면 병렬 POST가 전부
        // 검사를 통과해 상한을 넘긴다. 위 두 검증(cron 형식·zone 소속)은 이 race와 무관해 잠금 밖에서
        // 먼저 끝내 잠금 보유 시간을 최소화한다.
        farmRepository.findByIdForUpdate(farmId).orElseThrow(() -> new CustomException(ErrorCode.F001));
        if (scheduleRepository.countByFarmId(farmId) >= MAX_SCHEDULES_PER_FARM) {
            throw new CustomException(ErrorCode.SCH002,
                    "농장당 스케줄은 최대 " + MAX_SCHEDULES_PER_FARM + "개까지 등록할 수 있습니다.");
        }

        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .farmId(farmId)
                .zoneId(request.zoneId())
                .name(request.name().trim())
                .enabled(request.enabled() == null || request.enabled())
                .cronExpression(request.cronExpression())
                .actionType(request.actionType())
                .actionPayload(writePayloadJson(request.actionPayload()))
                .createdBy(userId)
                .build());

        return toResponse(schedule);
    }

    @Transactional
    public ScheduleResponse update(Long farmId, Long userId, Long scheduleId, ScheduleUpdateRequest request) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireAdmin(farmId, userId);
        Schedule schedule = findOrThrow(farmId, scheduleId);

        if (request.cronExpression() != null) {
            validateCron(request.cronExpression());
        }
        schedule.update(normalizedName(request.name()), request.enabled(), request.cronExpression(),
                writePayloadJson(request.actionPayload()));

        return toResponse(schedule);
    }

    @Transactional
    public void delete(Long farmId, Long userId, Long scheduleId) {
        demoAccountGuard.rejectDemoAccount(userId);
        farmAccessGuard.requireAdmin(farmId, userId);
        Schedule schedule = findOrThrow(farmId, scheduleId);
        scheduleRepository.delete(schedule);
    }

    /** PATCH의 {@code name} 정규화 — 보내면 공백일 수 없다(AlarmRuleService 선례와 동일 원칙). */
    private String normalizedName(String name) {
        if (name == null) {
            return null;
        }
        if (name.isBlank()) {
            throw new CustomException(ErrorCode.C001, "스케줄 이름은 공백일 수 없습니다.");
        }
        return name.trim();
    }

    /**
     * cron 표현식 형식 검증(SCH003) — Spring {@code CronExpression.parse}를 그대로 쓴다. 잘못된
     * 식이 저장되면 나중에 스케줄러가 붙을 때 그제서야 터지므로 저장 시점에 막는다.
     */
    private void validateCron(String cronExpression) {
        try {
            CronExpression.parse(cronExpression);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.SCH003);
        }
    }

    private void validateZone(Long farmId, Long zoneId) {
        if (zoneId == null) {
            return;
        }
        zoneRepository.findByIdAndFarmId(zoneId, farmId).orElseThrow(() -> new CustomException(ErrorCode.R001));
    }

    private Schedule findOrThrow(Long farmId, Long scheduleId) {
        return scheduleRepository.findByIdAndFarmId(scheduleId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCH001));
    }

    private ScheduleResponse toResponse(Schedule schedule) {
        return ScheduleResponse.from(schedule, parsePayloadJson(schedule.getActionPayload()));
    }

    private String writePayloadJson(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("스케줄 actionPayload JSON 직렬화 실패", e);
            throw new CustomException(ErrorCode.C002);
        }
    }

    private JsonNode parsePayloadJson(String payloadJson) {
        if (payloadJson == null) {
            return null;
        }
        try {
            return objectMapper.readTree(payloadJson);
        } catch (JsonProcessingException e) {
            log.error("스케줄 actionPayload JSON 파싱 실패", e);
            throw new CustomException(ErrorCode.C002);
        }
    }
}
