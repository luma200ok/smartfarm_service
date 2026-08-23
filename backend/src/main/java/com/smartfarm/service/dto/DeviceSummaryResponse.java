package com.smartfarm.service.dto;

import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import java.util.List;

/**
 * 장비 KPI 요약(contract §4.10). {@code byModel}은 제품군(모델) 단위 집계 — 프리뷰 관리 화면의
 * "순환팬 A · 24 EA" 표기용(개체 저장·서버 집계 원칙, DeviceService#summary 참고).
 *
 * <p>{@code off}는 2026-08-24 사이클 3에서 추가됐다(§4.10 리뷰 반영) — 없으면 비상 정지 직후
 * {@code {total:60, normal:0, warning:0, faultOrOffline:0}}이 되어 <b>농장 전체가 멈췄는데 화면에는
 * "이상 없음"으로 읽힌다</b>(렌더 버그와 구분 불가). 불변식: {@code total = normal + warning +
 * faultOrOffline + off}.
 */
public record DeviceSummaryResponse(
        long total,
        long normal,
        long warning,
        long faultOrOffline,
        long off,
        long calibrationDueSoon,
        List<ByModel> byModel
) {

    /** 그룹 내 최악 상태(FAULT &gt; OFFLINE &gt; WARNING &gt; OFF &gt; NORMAL)를 status로 노출 — FE 뱃지 색상용. */
    public record ByModel(String name, DeviceKind kind, long count, DeviceStatus status) {
    }
}
