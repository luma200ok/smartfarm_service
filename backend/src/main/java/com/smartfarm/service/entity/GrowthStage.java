package com.smartfarm.service.entity;

/**
 * 토마토 생육 단계(contract §4.9, 이슈 #64) — 1차 범위는 TOMATO 단일이라 작물별 분기 없이
 * 공용 enum으로 둔다(CropType과 동일하게 확장 대비).
 */
public enum GrowthStage {
    SEEDLING, VEGETATIVE, FRUITING, HARVEST
}
