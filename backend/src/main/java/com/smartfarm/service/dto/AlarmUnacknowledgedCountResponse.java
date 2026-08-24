package com.smartfarm.service.dto;

/** TopBar "미확인 알람 N건" 배지용 경량 응답(이슈 #116). */
public record AlarmUnacknowledgedCountResponse(long count) {
}
