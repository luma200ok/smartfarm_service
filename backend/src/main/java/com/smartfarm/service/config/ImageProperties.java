package com.smartfarm.service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 진단 이미지 원본 저장 base 디렉터리(contract §3 Phase 3, 이슈 #20).
 * 기본값 없음 — 운영은 IMAGE_STORAGE_DIR 필수, 로컬/테스트는 임시 디렉터리를 명시 주입한다
 * (handoff — {@code @Validated}로 키 누락 시 기동 실패해 조기 검출).
 *
 * @param storageDir base 디렉터리 — {@code {storageDir}/{farmId}/{diagnosisId}.{ext}}로 저장
 */
@Validated
@ConfigurationProperties(prefix = "image")
public record ImageProperties(
        @NotBlank String storageDir
) {
}
