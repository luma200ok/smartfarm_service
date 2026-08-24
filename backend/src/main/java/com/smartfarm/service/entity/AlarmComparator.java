package com.smartfarm.service.entity;

/**
 * 알람 규칙 비교 연산자(이슈 #118) — 어떤 threshold 컬럼을 쓰는지가 값마다 다르다.
 * 애플리케이션은 {@code ALR003}으로, DB는 V20의 {@code ck_alarm_rules_comparator}로 이중 검증한다.
 */
public enum AlarmComparator {
    /** 값 &gt; thresholdValue */
    GT("상한 초과"),
    /** 값 &lt; thresholdValue */
    LT("하한 미만"),
    /** 값 &lt; thresholdMin 또는 값 &gt; thresholdMax */
    OUTSIDE_RANGE("범위 이탈"),
    /** 값 비교가 아닌 부재 판정({@link AlarmRuleSource#DEVICE_HEARTBEAT} 전용) */
    ABSENT("무응답");

    private final String label;

    AlarmComparator(String label) {
        this.label = label;
    }

    /**
     * 알람 메시지·웹훅 본문에 쓰는 한국어 표기. #117까지는 {@code EnvDirection}(LOW "하한 미만" /
     * HIGH "상한 초과")이 이 역할을 했는데, 규칙이 방향 대신 comparator로 이탈을 표현하게 되면서
     * (#118) 그 enum이 겸하던 두 역할 — 표기와 {@code metricKey} 접미사 — 을 각각
     * 이 label과 {@link AlarmRule#metricKey()}가 이어받았다.
     */
    public String label() {
        return label;
    }
}
