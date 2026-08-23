package com.smartfarm.service.dto;

/** KMA 단기예보 SKY 카테고리 매핑(contract §4.8) — 코드 1=맑음, 3=구름많음, 4=흐림(2는 미사용). */
public enum SkyCondition {
    SUNNY,
    CLOUDY,
    OVERCAST
}
