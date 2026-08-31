package com.smartfarm.service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.RackLevel;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V26 백필 DML 검증(이슈 #145) — <b>마이그레이션 파일의 SQL을 그대로 읽어</b> label이 null인 레거시
 * 층 데이터에 대고 실행한다.
 *
 * <p>왜 필요한가: 테스트 환경의 Flyway는 빈 DB에서 돌고, 이 사이클부터 {@code RackService
 * #createLevels}가 label을 채우므로 새로 생성되는 층은 이미 label이 채워져 있다 — 즉 백필 UPDATE의
 * {@code WHERE label IS NULL} 대상이 테스트 DB에 자연히 존재하지 않는다. 이 테스트가 없으면 백필
 * SQL이 틀려도(예: 조건이나 표현식 오타) 아무 테스트도 잡지 못한다(V20 이관 DML 검증의 선례와 동일
 * 원칙 — {@code AlarmRuleMigrationV20IntegrationTest}).
 *
 * <p>SQL을 문자열로 재작성하지 않고 파일에서 잘라 쓰는 이유는 드리프트 방지다.
 */
@Transactional
class RackLevelMigrationV26IntegrationTest extends FarmTestSupport {

    private static final Path V26_PATH =
            Path.of("src/main/resources/db/migration/V26__rack_level_label_backfill.sql");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RackLevelRepository rackLevelRepository;

    /** V26 파일에서 UPDATE 문만 골라낸다(주석·DDL 없음 — 이 마이그레이션은 UPDATE 한 문장뿐). */
    private List<String> migrationDml() {
        String sql;
        try {
            sql = Files.readString(V26_PATH, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("V26 마이그레이션 파일을 읽을 수 없습니다: " + V26_PATH, e);
        }
        List<String> statements = Arrays.stream(sql.split(";"))
                .map(RackLevelMigrationV26IntegrationTest::stripLeadingComments)
                .filter(statement -> statement.startsWith("UPDATE"))
                .toList();
        assertThat(statements)
                .as("V26의 백필 UPDATE 1건을 찾지 못했다 — 백필이 통째로 빠졌거나 문장이 늘었다")
                .hasSize(1);
        return statements;
    }

    private static String stripLeadingComments(String chunk) {
        return chunk.lines()
                .dropWhile(line -> line.isBlank() || line.stripLeading().startsWith("--"))
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b)
                .trim();
    }

    @Test
    @DisplayName("V26 백필: label이 null인 레거시 층은 \"{levelNo}층\"으로 채워지고, "
            + "이미 라벨이 있는 층은 그대로 유지된다")
    void migrationBackfillsNullLabelsOnly() throws Exception {
        String token = signupAndLogin("백필농부");
        long farmId = createFarm(token, "백필 농장");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 1);
        long rackLevelId = findLevelId(farmId, rackId);

        // 이 사이클 이전 데이터를 재현: label을 강제로 null로 되돌린다(신규 생성 경로는 이미
        // label을 채우므로, 백필 대상인 "레거시 null 행"은 직접 만들어야 한다).
        jdbcTemplate.update("UPDATE rack_levels SET label = NULL WHERE id = ?", rackLevelId);

        // 이미 사용자가 편집해 둔 라벨이 있는 층도 하나 추가(향후 편집 API가 생겼을 때를 대비한
        // 회귀 방지 — 백필이 null이 아닌 기존 값을 덮어쓰면 안 된다).
        long customRackId = createRack(token, farmId, zoneId, "R2", 1);
        long customLevelId = findLevelId(farmId, customRackId);
        jdbcTemplate.update("UPDATE rack_levels SET label = ? WHERE id = ?", "커스텀라벨", customLevelId);

        entityManager.flush();
        migrationDml().forEach(jdbcTemplate::execute);
        entityManager.clear();

        RackLevel backfilled = rackLevelRepository.findById(rackLevelId).orElseThrow();
        assertThat(backfilled.getLabel()).isEqualTo("1층");

        RackLevel untouched = rackLevelRepository.findById(customLevelId).orElseThrow();
        assertThat(untouched.getLabel()).isEqualTo("커스텀라벨");
    }

    private long findLevelId(long farmId, long rackId) {
        return rackLevelRepository.findByRackIdOrderByLevelNoAsc(rackId).stream()
                .filter(level -> level.getRackId().equals(rackId))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
