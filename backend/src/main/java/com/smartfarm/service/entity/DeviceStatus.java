package com.smartfarm.service.entity;

/** 장비 통신·동작 상태(contract §4.10) — 프리뷰 statusTone ok/warning/critical + 통신두절(OFFLINE). */
public enum DeviceStatus {
    NORMAL,
    WARNING,
    FAULT,
    OFFLINE
}
