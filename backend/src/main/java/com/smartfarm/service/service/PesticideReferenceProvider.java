package com.smartfarm.service.service;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.PesticideAlertSeverity;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 농약 참조정보 조회의 교체 이음새(이슈 #128 handoff 핵심 요구사항).
 *
 * <p>1차 구현({@code LocalPesticideReferenceProvider})은 자체 시드 DB를 조회한다 — 농촌진흥청
 * 오픈API 키·스펙을 확보하지 못했기 때문이다. 나중에 실 API 키를 얻으면 이 인터페이스의 새 구현체
 * ({@code @Primary} 지정 또는 profile 스위치로 교체)만 추가하면 되고, {@code PesticideReferenceService}·
 * {@code PesticideReferenceController}·응답 DTO는 전혀 손대지 않는다.
 *
 * <p>결과 상한(limit)은 호출자(Service)가 정책값을 넘긴다 — 로컬 구현은 JPA {@code Pageable}로,
 * 실 API 구현체는 그 API의 페이지 크기 파라미터로 각각 번역하면 되므로 상한 정책 자체는
 * 구현 교체와 무관하게 유지된다.
 */
public interface PesticideReferenceProvider {

    /** cropType 필수, query는 병해충명 부분 검색(null/빈 문자열이면 전체). 최대 limit건. */
    List<PesticideReferenceItem> findReferences(CropType cropType, String query, int limit);

    /** cropType 필수, now 시각 기준으로 유효한(validFrom~validUntil) 경보만. 최대 limit건. */
    List<PesticideAlertItem> findActiveAlerts(CropType cropType, LocalDateTime now, int limit);

    /**
     * 참조정보 1건. {@code source}는 이 정보의 출처를 담는다 — <b>절대 "농촌진흥청 연동"처럼 실제
     * 연동을 뜻하는 문구를 담지 않는다.</b> 1차 구현(로컬 시드)에서는 "내부 샘플 데이터" 사실을
     * 명시하는 고정 문구가 오고, 실 API 구현체로 교체되면 그 API가 실제로 응답한 출처 문구가 온다 —
     * 즉 "얼마나 신뢰할 수 있는 데이터인가"를 이 필드 하나로 정직하게 전달하는 것이 계약이다.
     */
    record PesticideReferenceItem(CropType cropType, String pestName, int registeredProductCount,
                                   Integer preHarvestIntervalDays, String note, String source,
                                   LocalDateTime updatedAt) {
    }

    /**
     * 경보 1건. {@code source}는 {@link PesticideReferenceItem#source()}와 동일한 계약을 진다(리뷰
     * P2) — 경보 문구가 "총채벌레 발생 밀도가 증가하고 있습니다"처럼 실제 관측 기반 공식 경보로
     * 읽히기 쉬워, 참조정보와 동일하게 신뢰 수준을 정직하게 밝혀야 한다.
     */
    record PesticideAlertItem(CropType cropType, String message, PesticideAlertSeverity severity,
                               LocalDateTime validFrom, LocalDateTime validUntil, String source) {
    }
}
