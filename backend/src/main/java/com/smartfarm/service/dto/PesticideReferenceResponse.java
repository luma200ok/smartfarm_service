package com.smartfarm.service.dto;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.service.PesticideReferenceProvider;
import java.time.LocalDateTime;

/**
 * {@code GET /api/pesticide-references} 응답 1건(이슈 #128).
 *
 * <p>{@code source}는 항상 이 정보의 신뢰 수준을 정직하게 담는다 — 1차 구현(로컬 시드)에서는
 * "내부 샘플 데이터, 실제 등록 정보와 다를 수 있음"이 명시된 문구가 온다. <b>절대 "농촌진흥청
 * 연동"처럼 실제 연동을 암시하는 문구가 오지 않는다</b>(handoff 핵심 요구사항 — 사용자가 이 수치를
 * 실제 안전사용기준으로 믿고 살포하면 작물 피해·잔류농약 문제로 이어질 수 있다).
 */
public record PesticideReferenceResponse(CropType cropType, String pestName, int registeredProductCount,
                                          Integer preHarvestIntervalDays, String note, String source,
                                          LocalDateTime updatedAt) {

    public static PesticideReferenceResponse from(PesticideReferenceProvider.PesticideReferenceItem item) {
        return new PesticideReferenceResponse(item.cropType(), item.pestName(), item.registeredProductCount(),
                item.preHarvestIntervalDays(), item.note(), item.source(), item.updatedAt());
    }
}
