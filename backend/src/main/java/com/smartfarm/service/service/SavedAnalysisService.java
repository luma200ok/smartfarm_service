package com.smartfarm.service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfarm.service.dto.SavedAnalysisRequest;
import com.smartfarm.service.dto.SavedAnalysisResponse;
import com.smartfarm.service.dto.SavedAnalysisUpdateRequest;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.entity.SavedAnalysis;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.FarmRepository;
import com.smartfarm.service.repository.SavedAnalysisRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장한 분석 CRUD(이슈 #126) — 데이터 화면 필터(metrics/range/scope)를 이름 붙여 저장한다.
 * "실행"(그 필터로 다시 조회)은 기존 {@code GET /readings/series}를 그대로 쓰므로 이 서비스의
 * 범위가 아니다 — FE가 저장된 값을 그 API의 쿼리 파라미터로 재사용한다.
 *
 * <p>⚠️ <b>작성은 OPERATOR 이상</b>(이슈 #122 원칙, {@code FarmLogService} 선례 재사용) — 조회는
 * requireMember 그대로다. VIEWER가 author가 되면 아래 삭제 규칙(author OR ADMIN)을 통해 삭제
 * 권한까지 갖게 되어 "조회전용"이라는 역할 정의가 작성 경로로 우회된다.
 *
 * <p>{@code DemoAccountGuard}는 의도적으로 적용하지 않는다 — 그 가드의 차단 대상(회원 탈퇴·농장
 * 생성/수정/삭제·웹훅 설정·초대코드 발급/수락·멤버 제거)은 전부 구조적/파괴적 작업이고, 저장한
 * 분석은 FarmLog·NutrientRecipe와 같은 <b>콘텐츠 저장물</b>(북마크에 가깝다)이라 데모 체험에서도
 * 계속 쓸 수 있어야 한다.
 *
 * <p><b>metrics 저장 방식(배열/JSON/조인 테이블) 선택 근거</b>: JSON 문자열(JSONB) 컬럼을 택했다.
 * 조인 테이블(saved_analysis_metrics)은 최대 4개짜리 목록 하나를 위해 별도 테이블·N+1 조회 방지용
 * fetch join·삭제 캐스케이드를 새로 설계해야 하는데, 이 목록은 <b>개별 metric으로 검색·집계되지
 * 않는다</b>(단건 조회 시 그대로 통째로 반환될 뿐 — series/export처럼 "이 지표를 포함하는 저장
 * 항목 찾기" 같은 쿼리가 이 기능 범위에 없다). 반대로 Postgres 네이티브 배열(TEXT[])은 프로젝트에
 * hibernate-types 등 배열 매핑 의존성이 없어 새 의존성을 추가해야 한다. JSON 문자열은
 * {@code ChatMessage#sources}·{@code NutrientRecipe#calculationSnapshot}이 이미 쓰는 선례라
 * 추가 의존성 없이 Hibernate 6 {@code @JdbcTypeCode(SqlTypes.JSON)}만으로 표현된다 — 이 프로젝트의
 * "선택지 목록은 JSONB" 관용구를 그대로 따른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SavedAnalysisService {

    /**
     * 농장당 저장한 분석 상한(초과 시 SA002) — {@code AlarmRuleService.MAX_RULES_PER_FARM}(50)과
     * 동일한 #91 리소스 생성 상한 정책을 따른다. 이 상한 자체가 무거운 반복 쿼리를 유발하지는
     * 않지만(단순 CRUD 목록, 알람 규칙처럼 매 틱 재평가되지 않는다), 무제한 저장을 허용하면 목록
     * UI·DB 행이 사용자 입력에 비례해 무한정 늘어나므로 다른 리소스 상한들과 동일한 방어선을 둔다.
     */
    static final int MAX_ANALYSES_PER_FARM = 50;

    private final SavedAnalysisRepository savedAnalysisRepository;
    private final FarmRepository farmRepository;
    private final AlarmScopeResolver alarmScopeResolver;
    private final FarmAccessGuard farmAccessGuard;
    private final ObjectMapper objectMapper;

    @Transactional
    public SavedAnalysisResponse create(Long farmId, Long userId, SavedAnalysisRequest request) {
        farmAccessGuard.requireOperator(farmId, userId);

        List<SensorMetric> metrics = normalizeMetrics(request.metrics());
        String range = validateRange(request.range());
        validateScopeShape(request.scopeType(), request.scopeId());

        // ⚠️ 상한 판정은 농장 행을 잠근 뒤에 한다(AlarmRuleService.createRule 관용구 재사용 —
        // #91 TOCTOU 교훈). "세어 보고 → 저장"은 check-then-act라 잠금이 없으면 병렬 POST가 전부
        // 검사를 통과해 상한을 넘긴다.
        farmRepository.findByIdForUpdate(farmId).orElseThrow(() -> new CustomException(ErrorCode.F001));
        if (savedAnalysisRepository.countByFarmId(farmId) >= MAX_ANALYSES_PER_FARM) {
            throw new CustomException(ErrorCode.SA002,
                    "농장당 저장한 분석은 최대 " + MAX_ANALYSES_PER_FARM + "개까지 등록할 수 있습니다.");
        }

        // 소속·생존 확인은 알람 규칙 생성 경로와 같은 매핑을 쓴다(AlarmScopeResolver, 이슈 #118) —
        // 두 도메인이 갈라지면 한쪽은 막는데 한쪽은 통과하는 상태가 생긴다. FARM 스코프는
        // exists()가 scopeId 유무와 무관하게 항상 true라 이 호출이 무조건 안전하다.
        alarmScopeResolver.requireExists(farmId, request.scopeType(), request.scopeId());

        SavedAnalysis saved = savedAnalysisRepository.save(SavedAnalysis.builder()
                .farmId(farmId)
                .name(request.name().trim())
                .metrics(writeMetricsJson(metrics))
                .range(range)
                .scopeType(request.scopeType())
                .scopeId(request.scopeId())
                .createdBy(userId)
                .build());

        return toResponse(saved);
    }

    public List<SavedAnalysisResponse> findAll(Long farmId, Long userId) {
        farmAccessGuard.requireMember(farmId, userId);
        return savedAnalysisRepository.findByFarmIdOrderByCreatedAtDescIdDesc(farmId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SavedAnalysisResponse rename(Long farmId, Long userId, Long analysisId,
                                         SavedAnalysisUpdateRequest request) {
        // 삭제와 동일한 "작성자 OR ADMIN" 규칙 — 한쪽만 작성자 전용이면 ADMIN이 팀원의 오타 난
        // 분석 이름을 고칠 수 없으면서 지울 수는 있는 비대칭이 된다(#126 보안 리뷰 P2).
        FarmAccessGuard.FarmAccess access = farmAccessGuard.requireOperator(farmId, userId);
        SavedAnalysis analysis = findOrThrow(farmId, analysisId);
        boolean isAuthor = analysis.getCreatedBy().equals(userId);
        boolean isAdmin = access.membership().getRole().atLeast(FarmRole.ADMIN);
        if (!isAuthor && !isAdmin) {
            throw new CustomException(ErrorCode.SA004);
        }
        analysis.rename(request.name().trim());
        return toResponse(analysis);
    }

    @Transactional
    public void delete(Long farmId, Long userId, Long analysisId) {
        FarmAccessGuard.FarmAccess access = farmAccessGuard.requireOperator(farmId, userId);
        SavedAnalysis analysis = findOrThrow(farmId, analysisId);
        boolean isAuthor = analysis.getCreatedBy().equals(userId);
        boolean isAdmin = access.membership().getRole().atLeast(FarmRole.ADMIN);
        if (!isAuthor && !isAdmin) {
            throw new CustomException(ErrorCode.SA004);
        }
        savedAnalysisRepository.delete(analysis);
    }

    private SavedAnalysis findOrThrow(Long farmId, Long analysisId) {
        return savedAnalysisRepository.findByIdAndFarmId(analysisId, farmId)
                .orElseThrow(() -> new CustomException(ErrorCode.SA001));
    }

    private SavedAnalysisResponse toResponse(SavedAnalysis analysis) {
        return SavedAnalysisResponse.from(analysis, parseMetricsJson(analysis.getMetrics()));
    }

    /**
     * 중복 지표를 정리한다(series의 {@code ReadingService.MAX_SERIES_METRICS} 상한과 동일 원칙 —
     * DTO의 {@code @Size(max=4)}가 이미 원소 5개 이상을 막으므로, 여기서는 저장 데이터 품질을 위해
     * ["TEMPERATURE","TEMPERATURE"] 같은 중복만 접는다. dedup 후에는 정의상 4개를 넘을 수 없어
     * 별도 상한 재검사가 필요 없다).
     */
    private List<SensorMetric> normalizeMetrics(List<SensorMetric> metrics) {
        return new ArrayList<>(new LinkedHashSet<>(metrics));
    }

    /** range 형식 검증 — series의 {@code EnvironmentHistoryRange} 파싱을 그대로 재사용(C001). */
    private String validateRange(String rangeParam) {
        return EnvironmentHistoryRange.from(rangeParam).queryValue();
    }

    /** FARM↔scopeId 유무 형식 검증(소속 검증은 AlarmScopeResolver가 별도로 한다). */
    private void validateScopeShape(AlarmScopeType scopeType, Long scopeId) {
        if (scopeType == AlarmScopeType.FARM) {
            if (scopeId != null) {
                throw new CustomException(ErrorCode.C001, "농장 단위 스코프에는 scopeId를 지정할 수 없습니다.");
            }
            return;
        }
        if (scopeId == null) {
            throw new CustomException(ErrorCode.C001, "이 스코프에는 scopeId가 필요합니다.");
        }
    }

    /** 저장 JSON은 이 서비스가 직접 쓴 것 — 파싱 실패는 코딩 오류 수준(C002, ChatService 선례). */
    private List<SensorMetric> parseMetricsJson(String metricsJson) {
        try {
            return objectMapper.readValue(metricsJson, new TypeReference<List<SensorMetric>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("저장된 분석 metrics JSON 파싱 실패", e);
            throw new CustomException(ErrorCode.C002);
        }
    }

    private String writeMetricsJson(List<SensorMetric> metrics) {
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (JsonProcessingException e) {
            log.error("저장된 분석 metrics JSON 직렬화 실패", e);
            throw new CustomException(ErrorCode.C002);
        }
    }
}
