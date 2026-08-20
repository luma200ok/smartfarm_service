package com.smartfarm.service.dto;

import jakarta.validation.constraints.NotBlank;

/** 회원 탈퇴 요청 — 비밀번호 재확인 필수(contract §3: 토큰 탈취 단독으로 비가역 삭제 불가). */
public record WithdrawRequest(
        @NotBlank String password
) {
}
