package com.smartfarm.service.service;

/** 임계치 이탈 방향(contract §4.6 — "농장×항목×방향별 쿨다운"의 방향). */
public enum EnvDirection {
    LOW("하한 미만"),
    HIGH("상한 초과");

    private final String label;

    EnvDirection(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
