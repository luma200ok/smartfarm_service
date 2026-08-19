package com.smartfarm.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 매핑 절단(truncate) 경계 — 서로게이트 쌍(이모지)이 cap 경계에 걸려도 짝 잃은 문자가 남지 않는다. */
class PrescriptionResultTest {

    @Test
    @DisplayName("cap 경계가 이모지(서로게이트 쌍) 한가운데면 1자 후퇴해 절단한다 — 짝 잃은 high surrogate 금지")
    void truncateBacksOffAtSurrogateBoundary() {
        // "가"×1,999 + 😀(2 char) = 2,001자 — cap 2,000이 😀 한가운데(고서로게이트 뒤)를 자른다
        String summary = "가".repeat(1_999) + "😀";
        PrescriptionResult result = PrescriptionResult.from(
                new AiPrescriptionResponse(summary, null, null, null, null, List.of()));

        assertThat(result.summary()).hasSize(1_999); // 쌍을 통째로 버리고 1자 후퇴
        assertThat(Character.isHighSurrogate(result.summary().charAt(result.summary().length() - 1)))
                .isFalse();
        // 경계에 안 걸리면 그대로 cap 절단
        String plain = "가".repeat(2_500);
        assertThat(PrescriptionResult.from(
                new AiPrescriptionResponse(plain, null, null, null, null, List.of()))
                .summary()).hasSize(2_000);
    }
}
