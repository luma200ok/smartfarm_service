package com.smartfarm.service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.AlarmComparator;
import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmEventLog;
import com.smartfarm.service.entity.AlarmEventLogAction;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V20 이관 DML 검증(이슈 #118) — <b>마이그레이션 파일의 SQL을 그대로 읽어</b> 레거시 데이터에 대고
 * 실행한다.
 *
 * <p>왜 필요한가: 테스트 환경의 Flyway는 빈 DB에서 도므로 V20의 이관 DML이 <b>대상 행 0건으로
 * 통과</b>한다. 즉 정상 배포에서만 처음 실행되는 코드가 되어, 틀려도 아무 테스트가 잡지 못한다.
 * 특히 재매핑이 빠지거나 조인 조건이 틀리면 #116~#117 시절 열려 있던 알람이 새 평가 경로
 * ({@code RULE_{id}} 키)에서 영영 조회되지 않아 <b>자동 해소가 불가능한 유령 알람</b>으로
 * 고착된다 — 조용한 실패라 운영에서도 늦게 발견된다.
 *
 * <p>픽스처는 3종이다(#118 리뷰 P2-2 — 초판은 행복 경로 하나뿐이라 아래 ②③ 경계를 한 줄도 지나지
 * 않았다):
 * <ol>
 *   <li><b>전 경계 채움 + enabled</b> → 규칙 4개, 열린 알람은 새 키로 <b>재매핑</b></li>
 *   <li><b>경계 하나가 null</b> → 규칙 3개, 그 방향의 열린 알람은 대응 규칙이 없어 <b>자동 해소</b></li>
 *   <li><b>enabled=false</b> → 규칙 0개, 열린 알람 전부 <b>자동 해소</b></li>
 * </ol>
 *
 * <p>SQL을 문자열로 재작성하지 않고 파일에서 잘라 쓰는 이유는 드리프트 방지다(테스트만 고쳐 놓고
 * 마이그레이션은 틀린 채로 남는 것을 막는다).
 */
@Transactional
class AlarmRuleMigrationV20IntegrationTest extends FarmTestSupport {

    private static final Path V20_PATH =
            Path.of("src/main/resources/db/migration/V20__alarm_rules.sql");

    /** V20이 담고 있는 이관 DML 개수 — 파생 규칙 INSERT · 재매핑 UPDATE · 해소 로그 INSERT · 해소 UPDATE. */
    private static final int EXPECTED_DML_COUNT = 4;

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

    @Autowired
    private AlarmEventLogRepository alarmEventLogRepository;

    /**
     * V20 파일에서 이관 DML만 <b>파일에 적힌 순서 그대로</b> 골라낸다(DDL 제외). 순서가 중요하다 —
     * 해소 로그 INSERT는 상태를 보고 대상을 고르므로 해소 UPDATE보다 먼저 실행돼야 한다.
     */
    private List<String> migrationDml() {
        String sql;
        try {
            sql = Files.readString(V20_PATH, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("V20 마이그레이션 파일을 읽을 수 없습니다: " + V20_PATH, e);
        }
        List<String> statements = Arrays.stream(sql.split(";"))
                .map(AlarmRuleMigrationV20IntegrationTest::stripLeadingComments)
                .filter(statement -> statement.startsWith("INSERT") || statement.startsWith("UPDATE"))
                .toList();
        assertThat(statements)
                .as("V20의 이관 DML %d건(파생 규칙 INSERT · metric_key 재매핑 UPDATE · 해소 로그 "
                        + "INSERT · 해소 UPDATE)을 찾지 못했다 — 이관이 통째로 빠졌거나 DML이 늘었다. "
                        + "늘었다면 이 테스트가 그 새 문장까지 검증하도록 확장할 것", EXPECTED_DML_COUNT)
                .hasSize(EXPECTED_DML_COUNT);
        return statements;
    }

    /** 문장 앞에 붙은 `--` 주석 줄을 걷어내 실제 SQL 키워드로 시작하게 만든다. */
    private static String stripLeadingComments(String chunk) {
        return chunk.lines()
                .dropWhile(line -> line.isBlank() || line.stripLeading().startsWith("--"))
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b)
                .trim();
    }

    @Test
    @DisplayName("V20 이관 DML: ①전 경계 설정은 규칙 4개 + 열린 알람 재매핑 ②경계 하나가 null이면 "
            + "규칙 3개 + 그 방향 열린 알람은 자동 해소 ③enabled=false면 규칙 0개 + 열린 알람 전부 "
            + "자동 해소(감사 로그 동반). RESOLVED 이력은 어느 경우에도 건드리지 않는다")
    void migrationConvertsLegacyThresholdsAndClosesUnmappableAlarms() throws Exception {
        String token = signupAndLogin("이관농부");

        // ── ① 전 경계 채움 + enabled=true ──────────────────────────────────────────
        long farmFull = createFarm(token, "이관농장-전경계");
        FarmEnvThreshold fullThreshold = saveThreshold(farmFull, true, 18.0, 28.0, 40.0, 80.0);
        AlarmEvent remappable = alarmEventRepository.save(legacyEvent(farmFull, "INDOOR_TEMP_HIGH"));
        AlarmEvent resolvedHistory = alarmEventRepository.save(legacyEvent(farmFull, "INDOOR_HUMIDITY_LOW"));
        resolvedHistory.resolveAutomatically();
        alarmEventRepository.saveAndFlush(resolvedHistory);

        // ── ② 상한 경계만 null(그 방향 감시 해제) + enabled=true ────────────────────
        long farmPartial = createFarm(token, "이관농장-부분경계");
        saveThreshold(farmPartial, true, 18.0, null, 40.0, 80.0);
        AlarmEvent orphanByNullBound = alarmEventRepository.save(legacyEvent(farmPartial, "INDOOR_TEMP_HIGH"));

        // ── ③ enabled=false(경계값은 남아 있음) ────────────────────────────────────
        long farmDisabled = createFarm(token, "이관농장-비활성");
        saveThreshold(farmDisabled, false, 18.0, 28.0, 40.0, 80.0);
        AlarmEvent orphanByDisabled = alarmEventRepository.save(legacyEvent(farmDisabled, "INDOOR_HUMIDITY_LOW"));

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

        // ── ① 규칙 4개 + 열린 알람이 대응 규칙 키로 재매핑됐다 ──────────────────────
        List<AlarmRule> fullRules = alarmRuleRepository.findByFarmIdOrderByIdAsc(farmFull);
        assertThat(fullRules).hasSize(4);
        assertThat(fullRules).allSatisfy(rule -> {
            assertThat(rule.getSource()).isEqualTo(AlarmRuleSource.ENV_SNAPSHOT);
            assertThat(rule.getScopeType()).isEqualTo(AlarmScopeType.FARM);
            assertThat(rule.getDurationSeconds()).isEqualTo(60);
            assertThat(rule.getSeverity()).isEqualTo(AlarmSeverity.WARNING);
            assertThat(rule.isEnabled()).isTrue();
            assertThat(rule.getThresholdId()).isEqualTo(fullThreshold.getId());
        });
        // 이관된 규칙은 평가 대상에 들어온다(soft delete 필터를 통과).
        assertThat(alarmRuleRepository.findEnabled())
                .filteredOn(rule -> rule.getFarmId().equals(farmFull))
                .hasSize(4);

        AlarmRule tempMaxRule = fullRules.stream()
                .filter(r -> r.getMetric().equals("INDOOR_TEMP") && r.getComparator() == AlarmComparator.GT)
                .findFirst().orElseThrow();
        AlarmEvent remapped = alarmEventRepository.findById(remappable.getId()).orElseThrow();
        assertThat(remapped.getMetricKey()).isEqualTo(tempMaxRule.metricKey());
        assertThat(remapped.getRuleId()).isEqualTo(tempMaxRule.getId());
        assertThat(remapped.getScopeType()).isEqualTo(AlarmScopeType.FARM);
        assertThat(remapped.getStatus()).isEqualTo(AlarmEventStatus.UNACKNOWLEDGED); // 열린 채 유지
        // 재매핑된 키로 멱등성 조회가 성립해야 자동 해소가 다시 동작한다(재매핑의 존재 이유).
        assertThat(alarmEventRepository.findOpenEventByFarmAndMetric(farmFull, tempMaxRule.metricKey()))
                .isPresent();

        // 이미 RESOLVED된 과거 이력은 옛 키 그대로 둔다(감사 이력·unique index 대상 아님).
        AlarmEvent untouched = alarmEventRepository.findById(resolvedHistory.getId()).orElseThrow();
        assertThat(untouched.getMetricKey()).isEqualTo("INDOOR_HUMIDITY_LOW");
        assertThat(untouched.getRuleId()).isNull();
        assertThat(alarmEventLogRepository.findByAlarmEventIdOrderByCreatedAtAscIdAsc(untouched.getId()))
                .isEmpty(); // 손대지 않았으므로 해소 로그도 남지 않는다

        // ── ② 경계가 null인 방향은 규칙이 안 생기고, 그 열린 알람은 자동 해소된다 ──────
        List<AlarmRule> partialRules = alarmRuleRepository.findByFarmIdOrderByIdAsc(farmPartial);
        assertThat(partialRules).hasSize(3);
        assertThat(partialRules)
                .noneMatch(r -> r.getMetric().equals("INDOOR_TEMP") && r.getComparator() == AlarmComparator.GT);
        assertResolvedByMigration(orphanByNullBound, "INDOOR_TEMP_HIGH");

        // ── ③ enabled=false면 규칙이 하나도 안 생기고, 열린 알람은 전부 자동 해소된다 ───
        assertThat(alarmRuleRepository.findByFarmIdOrderByIdAsc(farmDisabled)).isEmpty();
        assertResolvedByMigration(orphanByDisabled, "INDOOR_HUMIDITY_LOW");
    }

    /**
     * 대응 규칙이 없어 마이그레이션이 닫은 알람 — 옛 키를 유지한 채 시스템 자동 해소(resolvedBy=null)
     * 되고, 타임라인에 사유가 남아야 한다(감사 추적).
     */
    private void assertResolvedByMigration(AlarmEvent original, String expectedLegacyKey) {
        AlarmEvent event = alarmEventRepository.findById(original.getId()).orElseThrow();
        assertThat(event.getMetricKey()).isEqualTo(expectedLegacyKey);
        assertThat(event.getRuleId()).isNull();
        assertThat(event.getStatus()).isEqualTo(AlarmEventStatus.RESOLVED);
        assertThat(event.getResolvedBy()).isNull();
        assertThat(event.getResolvedAt()).isNotNull();

        List<AlarmEventLog> logs =
                alarmEventLogRepository.findByAlarmEventIdOrderByCreatedAtAscIdAsc(event.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(AlarmEventLogAction.RESOLVED);
        assertThat(logs.get(0).getActorId()).isNull();
        assertThat(logs.get(0).getNote()).contains("#118");
    }

    private FarmEnvThreshold saveThreshold(long farmId, boolean enabled, Double tempMin, Double tempMax,
                                            Double humidityMin, Double humidityMax) {
        return farmEnvThresholdRepository.save(FarmEnvThreshold.builder()
                .farmId(farmId)
                .enabled(enabled)
                .indoorTempMin(tempMin)
                .indoorTempMax(tempMax)
                .indoorHumidityMin(humidityMin)
                .indoorHumidityMax(humidityMax)
                .build());
    }

    /** #116~#117 형식({@code {EnvMetric}_{EnvDirection}})의 알람 이벤트. */
    private AlarmEvent legacyEvent(long farmId, String legacyMetricKey) {
        return AlarmEvent.builder()
                .farmId(farmId)
                .severity(AlarmSeverity.WARNING)
                .sourceType(AlarmSourceType.ENV_THRESHOLD)
                .metricKey(legacyMetricKey)
                .message(legacyMetricKey + " 이탈")
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
