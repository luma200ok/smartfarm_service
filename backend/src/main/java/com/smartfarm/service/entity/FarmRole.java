package com.smartfarm.service.entity;

/**
 * 농장 멤버 역할 — OWNER(관리·초대·삭제) / MEMBER(조회·진단·처방). MANAGER는 후속 (contract §2).
 */
public enum FarmRole {
    OWNER,
    MEMBER
}
