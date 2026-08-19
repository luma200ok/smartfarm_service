package com.smartfarm.service.dto;

import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;

/**
 * 처방 결과 구조화 JSON(contract §4: result{summary, actions[], caution, sources[]}).
 * JSONB 저장 직렬화와 API 응답 노출에 공통으로 쓴다. ai-server 원 응답은 한글 키
 * ({@link AiPrescriptionResponse})라 {@link #from(AiPrescriptionResponse)}에서 계약 스키마로 매핑한다.
 *
 * <p><b>매핑 규칙(contract §3 연동 표)</b>:
 * <ul>
 *   <li>summary ← 진단요약 — <b>필수</b>: null/blank면 P002(빈 성공 금지 — 미매핑 응답의
 *       "전 필드 null COMPLETED" fail-open 차단)</li>
 *   <li>actions ← [즉시조치, 예방] — 빈 항목 제외</li>
 *   <li>caution ← "원인: …" + "재촬영 시점: …" 결합(개행 구분, 모두 빈값이면 null)</li>
 *   <li>sources ← 근거출처 — 빈 항목 제외</li>
 * </ul>
 * <b>크기 캡</b>(초과 절단): summary/caution 2,000자 · actions/sources 각 20개 · 목록 원소 500자.
 */
public record PrescriptionResult(
        String summary,
        List<String> actions,
        String caution,
        List<String> sources
) {

    private static final int TEXT_CAP = 2_000;
    private static final int LIST_CAP = 20;
    private static final int ITEM_CAP = 500;

    public static PrescriptionResult from(AiPrescriptionResponse ai) {
        if (isBlank(ai.diagnosisSummary())) {
            // 필수 필드 누락 = 스키마 미매핑(영문/이상 응답 포함) — COMPLETED 빈 처방 대신 P002 FAILED
            throw new CustomException(ErrorCode.P002);
        }
        return new PrescriptionResult(
                truncate(ai.diagnosisSummary().trim(), TEXT_CAP),
                capList(nonBlankOf(ai.immediateAction(), ai.prevention())),
                buildCaution(ai.cause(), ai.reshootGuide()),
                capList(ai.sources()));
    }

    private static String buildCaution(String cause, String reshootGuide) {
        List<String> parts = new ArrayList<>();
        if (!isBlank(cause)) {
            parts.add("원인: " + cause.trim());
        }
        if (!isBlank(reshootGuide)) {
            parts.add("재촬영 시점: " + reshootGuide.trim());
        }
        return parts.isEmpty() ? null : truncate(String.join("\n", parts), TEXT_CAP);
    }

    private static List<String> nonBlankOf(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (!isBlank(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private static List<String> capList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> !isBlank(value))
                .limit(LIST_CAP)
                .map(value -> truncate(value.trim(), ITEM_CAP))
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int cap) {
        return value.length() <= cap ? value : value.substring(0, cap);
    }
}
