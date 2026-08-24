package com.smartfarm.service.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AlarmEvent} 상태 전이 캡슐화 단위 테스트(이슈 #116) — DB 없이 순수 엔티티 로직만
 * 검증한다(FarmLog·ControlChange 등 다른 도메인의 엔티티 단위 테스트 선례와 동일 원칙).
 */
class AlarmEventTest {

    private final User user = User.builder().email("u@example.com").password("pw").nickname("사용자")
            .isDemo(false).build();

    private AlarmEvent newEvent() {
        AlarmEvent event = AlarmEvent.builder()
                .farmId(1L)
                .severity(AlarmSeverity.WARNING)
                .sourceType(AlarmSourceType.ENV_THRESHOLD)
                .metricKey("INDOOR_TEMP_HIGH")
                .message("실내 온도 상한 초과")
                .occurredAt(LocalDateTime.of(2026, 8, 24, 10, 0))
                .thresholdId(10L)
                .build();
        setId(user, 100L);
        return event;
    }

    /** @GeneratedValue라 저장 전에는 id가 null이다 — 테스트에서 리플렉션으로 채워준다(FarmLog 선례 없음, 신규). */
    private void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("생성 직후 상태는 UNACKNOWLEDGED다")
    void createdAsUnacknowledged() {
        AlarmEvent event = newEvent();

        assertThat(event.getStatus()).isEqualTo(AlarmEventStatus.UNACKNOWLEDGED);
    }

    @Test
    @DisplayName("UNACKNOWLEDGED 상태에서 acknowledge하면 ACKNOWLEDGED로 전이하고 확인자·시각을 남긴다")
    void acknowledgeTransitionsToAcknowledged() {
        AlarmEvent event = newEvent();

        event.acknowledge(user);

        assertThat(event.getStatus()).isEqualTo(AlarmEventStatus.ACKNOWLEDGED);
        assertThat(event.getAcknowledgedBy()).isEqualTo(user.getId());
        assertThat(event.getAcknowledgedAt()).isNotNull();
    }

    @Test
    @DisplayName("ACKNOWLEDGED 상태에서 resolve하면 RESOLVED로 전이하고 처리자·시각을 남긴다")
    void resolveTransitionsToResolved() {
        AlarmEvent event = newEvent();
        event.acknowledge(user);

        event.resolve(user);

        assertThat(event.getStatus()).isEqualTo(AlarmEventStatus.RESOLVED);
        assertThat(event.getResolvedBy()).isEqualTo(user.getId());
        assertThat(event.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("RESOLVED 상태에서 acknowledge를 시도하면 AL002 예외를 던진다(상태전이 가드)")
    void acknowledgeOnResolvedThrowsAl002() {
        AlarmEvent event = newEvent();
        event.acknowledge(user);
        event.resolve(user);

        assertThatThrownBy(() -> event.acknowledge(user))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.AL002);
    }

    @Test
    @DisplayName("UNACKNOWLEDGED 상태에서 resolve를 시도하면(확인 건너뛰기) AL002 예외를 던진다")
    void resolveWithoutAcknowledgeThrowsAl002() {
        AlarmEvent event = newEvent();

        assertThatThrownBy(() -> event.resolve(user))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.AL002);
    }

    @Test
    @DisplayName("이미 ACKNOWLEDGED인 이벤트를 다시 acknowledge하면 AL002 예외를 던진다(이중 처리 방지)")
    void doubleAcknowledgeThrowsAl002() {
        AlarmEvent event = newEvent();
        event.acknowledge(user);

        assertThatThrownBy(() -> event.acknowledge(user))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.AL002);
    }

    @Test
    @DisplayName("UNACKNOWLEDGED 상태에서 resolveAutomatically하면 RESOLVED로 전이하고 resolvedBy는 null이다")
    void resolveAutomaticallyFromUnacknowledged() {
        AlarmEvent event = newEvent();

        event.resolveAutomatically();

        assertThat(event.getStatus()).isEqualTo(AlarmEventStatus.RESOLVED);
        assertThat(event.getResolvedBy()).isNull();
        assertThat(event.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("ACKNOWLEDGED 상태에서도 resolveAutomatically로 자동 해소할 수 있다")
    void resolveAutomaticallyFromAcknowledged() {
        AlarmEvent event = newEvent();
        event.acknowledge(user);

        event.resolveAutomatically();

        assertThat(event.getStatus()).isEqualTo(AlarmEventStatus.RESOLVED);
        assertThat(event.getResolvedBy()).isNull();
    }

    @Test
    @DisplayName("이미 RESOLVED인 이벤트를 다시 resolveAutomatically하면 AL002 예외를 던진다")
    void resolveAutomaticallyOnResolvedThrowsAl002() {
        AlarmEvent event = newEvent();
        event.resolveAutomatically();

        assertThatThrownBy(event::resolveAutomatically)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.AL002);
    }
}
