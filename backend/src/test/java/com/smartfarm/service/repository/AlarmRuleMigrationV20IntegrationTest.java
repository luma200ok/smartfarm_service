package com.smartfarm.service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.AlarmComparator;
import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventStatus;
import com.smartfarm.service.entity.AlarmRule;
import com.smartfarm.service.entity.AlarmRuleSource;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.entity.FarmEnvThreshold;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V20 이관 DML 검증(이슈 #118) — <b>마이그레이션 파일의 SQL을 그대로 읽어</b> 레거시 데이터에 대고
 * 실행한다.
 *
 * <p>왜 필요한가: 테스트 환경의 Flyway는 빈 DB에서 도므로 V20의 {@code INSERT ... SELECT}(기존
 * {@code farm_env_thresholds} → 파생 규칙)와 {@code UPDATE alarm_events}(미해결 이벤트의
 * {@code metric_key} 재매핑)가 <b>대상 행 0건으로 통과</b>한다. 즉 정상 배포에서만 처음 실행되는
 * 코드가 되어, 틀려도 아무 테스트가 잡지 못한다. 특히 재매핑이 빠지거나 조인 조건이 틀리면
 * #116~#117 시절 열려 있던 알람이 새 평가 경로({@code RULE_{id}} 키)에서 영영 조회되지 않아
 * <b>자동 해소가 불가능한 유령 알람</b>으로 고착된다 — 조용한 실패라 운영에서도 늦게 발견된다.
 *
 * <p>SQL을 문자열로 재작성하지 않고 파일에서 잘라 쓰는 이유는 드리프트 방지다(테스트만 고쳐 놓고
 * 마이그레이션은 틀린 채로 남는 것을 막는다).
 */
@Transactional
class AlarmRuleMigrationV20IntegrationTest extends FarmTestSupport {

    private static final Path V20_PATH =
            Path.of("src/main/resources/db/migration/V20__alarm_rules.sql");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FarmEnvThresholdRepository farmEnvThresholdRepository;

    @Autowired
    private AlarmRuleRepository alarmRuleRepository;

    @Autowired
    private AlarmEventRepository alarmEventRepository;

    /** V20 파일에서 이관 DML 2건(파생 규칙 INSERT · alarm_events 재매핑 UPDATE)만 골라낸다. */
    private List<String> migrationDml() {
        String sql;
        try {
            sql = Files.readString(V20_PATH, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("V20 마이그레이션 파일을 읽을 수 없습니다: " + V20_PATH, e);
        }
        List<String> statements = Arrays.stream(sql.split(";"))
                .map(String::trim)
                .filter(statement -> Stream.of("INSERT INTO alarm_rules", "UPDATE alarm_events")
                        .anyMatch(statement::contains))
                .toList();
        assertThat(statements)
                .as("V20에서 이관 DML 2건(파생 규칙 INSERT · alarm_events metric_key 재매핑 UPDATE)을 "
                        + "찾지 못했다 — 마이그레이션에서 이관이 통째로 빠졌거나 이 테스트의 추출 "
                        + "조건이 낡았다")
                .hasSize(2);
        return statements;
    }

    @Test
    @DisplayName("V20 이관 DML은 기존 임계치 설정을 파생 규칙 4개로 옮기고, 그 설정으로 열려 있던 "
            + "미해결 알람의 metric_key를 새 규칙 키로 재매핑한다(RESOLVED 이력은 건드리지 않는다)")
    void migrationConvertsLegacyThresholdsAndRemapsOpenEvents() throws Exception {
        String token = signupAndLogin("이관농부");
        long farmId = createFarm(token, "이관농장");

        // ── #118 이전 상태 재현: 임계치 설정 1행 + 옛 형식 키의 알람 이벤트 2건 ──────────
        FarmEnvThreshold threshold = farmEnvThresholdRepository.save(FarmEnvThreshold.builder()
                .farmId(farmId)
                .enabled(true)
                .indoorTempMin(18.0)
                .indoorTempMax(28.0)
                .indoorHumidityMin(40.0)
                .indoorHumidityMax(80.0)
                .build());
        AlarmEvent openEvent = alarmEventRepository.save(legacyEvent(farmId, "INDOOR_TEMP_HIGH"));
        AlarmEvent resolvedEvent = alarmEventRepository.save(legacyEvent(farmId, "INDOOR_HUMIDITY_LOW"));
        resolvedEvent.resolveAutomatically();
        alarmEventRepository.saveAndFlush(resolvedEvent);

        // ── V20 이관 직전 상태 재현: alarm_rules는 아직 존재하지 않던 테이블이므로 비어 있다 ──
        // 싱글턴 컨테이너를 전 테스트가 공유하므로, 앞서 실행된 테스트 클래스들이 커밋해 둔 파생
        // 규칙이 남아 있다. 그 상태로 이관 SQL을 돌리면 같은 (threshold_id, metric, comparator)에
        // 대해 ux_alarm_rules_derived가 걸려, 검증하려던 이관 로직 대신 픽스처 오염이 관측된다.
        // 이 클래스는 @Transactional이라 이 정리도 테스트 종료 시 함께 롤백된다.
        jdbcTemplate.update("UPDATE alarm_events SET rule_id = NULL WHERE rule_id IS NOT NULL");
        jdbcTemplate.update("DELETE FROM alarm_rules");

        // ── V20 이관 DML 실행 ────────────────────────────────────────────────────────
        // 마이그레이션은 JPA 밖(raw SQL)에서 도는 것이 실제 동작이므로 그대로 재현한다 — 대신 실행
        // 전에 영속성 컨텍스트를 flush해 준비 데이터를 DB에 확정시키고, 실행 후 clear해 1차 캐시의
        // 낡은 사본이 아니라 DB의 실제 결과를 다시 읽는다.
        entityManager.flush();
        migrationDml().forEach(jdbcTemplate::execute);
        entityManager.clear();

        // ── ① 파생 규칙 4개가 이관 명세대로 만들어졌다 ────────────────────────────────
        List<AlarmRule> rules = alarmRuleRepository.findByFarmIdOrderByIdAsc(farmId);
        assertThat(rules).hasSize(4);
        assertThat(rules).allSatisfy(rule -> {
            assertThat(rule.getSource()).isEqualTo(AlarmRuleSource.ENV_SNAPSHOT);
            assertThat(rule.getScopeType()).isEqualTo(AlarmScopeType.FARM);
            assertThat(rule.getDurationSeconds()).isEqualTo(120);
            assertThat(rule.getSeverity()).isEqualTo(AlarmSeverity.WARNING);
            assertThat(rule.isEnabled()).isTrue();
            assertThat(rule.getThresholdId()).isEqualTo(threshold.getId());
        });

        // ── ② 이관된 규칙은 평가 대상에 들어온다(soft delete 필터를 통과) ─────────────
        assertThat(alarmRuleRepository.findEnabled())
                .filteredOn(rule -> rule.getFarmId().equals(farmId))
                .hasSize(4);

        // ── ③ 미해결 이벤트의 metric_key가 대응 규칙의 키로 재매핑됐다 ────────────────
        AlarmRule tempMaxRule = rules.stream()
                .filter(r -> r.getMetric().equals("INDOOR_TEMP") && r.getComparator() == AlarmComparator.GT)
                .findFirst().orElseThrow();
        AlarmEvent remapped = alarmEventRepository.findById(openEvent.getId()).orElseThrow();
        assertThat(remapped.getMetricKey()).isEqualTo(tempMaxRule.metricKey());
        assertThat(remapped.getRuleId()).isEqualTo(tempMaxRule.getId());
        assertThat(remapped.getScopeType()).isEqualTo(AlarmScopeType.FARM);

        // 재매핑된 키로 멱등성 조회가 성립해야 자동 해소가 다시 동작한다(이 검증이 이 테스트의 핵심).
        assertThat(alarmEventRepository.findOpenEventByFarmAndMetric(farmId, tempMaxRule.metricKey()))
                .isPresent();

        // ── ④ 이미 RESOLVED된 과거 이력은 옛 키 그대로 둔다(감사 이력·unique index 대상 아님) ──
        AlarmEvent untouched = alarmEventRepository.findById(resolvedEvent.getId()).orElseThrow();
        assertThat(untouched.getMetricKey()).isEqualTo("INDOOR_HUMIDITY_LOW");
        assertThat(untouched.getRuleId()).isNull();
        assertThat(untouched.getStatus()).isEqualTo(AlarmEventStatus.RESOLVED);
    }

    /** #116~#117 형식({@code {EnvMetric}_{EnvDirection}})의 알람 이벤트. */
    private AlarmEvent legacyEvent(long farmId, String legacyMetricKey) {
        return AlarmEvent.builder()
                .farmId(farmId)
                .severity(AlarmSeverity.WARNING)
                .sourceType(AlarmSourceType.ENV_THRESHOLD)
                .metricKey(legacyMetricKey)
                .message(legacyMetricKey + " 이탈")
                .occurredAt(java.time.LocalDateTime.now())
                .build();
    }
}
