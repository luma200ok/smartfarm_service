package com.smartfarm.service.dto;

import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import java.util.List;

/**
 * 장비 KPI 요약(contract §4.10). {@code byModel}은 제품군(모델) 단위 집계 — 프리뷰 관리 화면의
 * "순환팬 A · 24 EA" 표기용(개체 저장·서버 집계 원칙, DeviceService#summary 참고).
 */
public record DeviceSummaryResponse(
        long total,
        long normal,
        long warning,
        long faultOrOffline,
        long calibrationDueSoon,
        List<ByModel> byModel
) {

    /** 그룹 내 최악 상태(FAULT &gt; OFFLINE &gt; WARNING &gt; NORMAL)를 status로 노출 — FE 뱃지 색상용. */
    public record ByModel(String name, DeviceKind kind, long count, DeviceStatus status) {
    }
}
