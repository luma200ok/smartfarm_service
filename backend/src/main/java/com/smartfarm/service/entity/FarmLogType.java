package com.smartfarm.service.entity;

/** 작업일지 작업 유형(contract §4.8) — FE 라벨은 constants에서 매핑. */
public enum FarmLogType {
    WATERING,
    FERTILIZING,
    PRUNING,
    HARVEST,
    PEST_CONTROL,
    ETC
}
