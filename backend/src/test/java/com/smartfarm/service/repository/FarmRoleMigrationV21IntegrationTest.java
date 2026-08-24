package com.smartfarm.service.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.ControlChangeRequest;
import com.smartfarm.service.dto.ControlModeRequest;
import com.smartfarm.service.dto.ZoneRequest;
import com.smartfarm.service.entity.ControlChangeKind;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.entity.OperationMode;
import com.smartfarm.service.entity.SensorMetric;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V21 역할 이관 DML 검증(이슈 #122) — <b>마이그레이션 파일의 SQL을 그대로 읽어</b> 레거시 데이터에
 * 대고 실행한다(V20 테스트와 같은 원칙: 테스트만 고쳐 놓고 마이그레이션은 틀린 채 남는 드리프트 방지).
 *
 * <p><b>왜 필요한가</b>: 테스트 환경의 Flyway는 빈 DB에서 도므로 V21의 이관 DML이 <b>대상 행 0건으로
 * 통과</b>한다. 즉 정상 배포에서만 처음 실행되는 코드가 되어, 틀려도 아무 테스트가 잡지 못한다.
 * 그런데 이 이관이 틀리면 결과가 조용하지 않다:
 * <ul>
 *   <li>이관이 <b>빠지면</b> 남은 {@code 'OWNER'}/{@code 'MEMBER'} 문자열이 enum에 매핑되지 않아
 *       그 사용자는 농장 접근 전체가 500으로 무너진다(가드의 멤버십 조회 단계에서 터진다).</li>
 *   <li>{@code MEMBER}를 {@code VIEWER}로 잘못 내리면 <b>조용한 기능 회귀</b>다 — 어제까지 장비를
 *       켜고 끄던 사용자가 오늘부터 403을 받는다. 500과 달리 로그로도 잘 드러나지 않는다.</li>
 * </ul>
 *
 * <p>그래서 "role 문자열이 바뀌었다"에서 멈추지 않고 <b>이관된 사용자가 실제로 무엇을 할 수 있는지</b>
 * 를 공개 API로 확인한다 — 이관의 목적이 "권한 보존"이기 때문이다.
 */
@Transactional
class FarmRoleMigrationV21IntegrationTest extends FarmTestSupport {

    private static final Path V21_PATH =
            Path.of("src/main/resources/db/migration/V21__farm_member_roles.sql");

    /** V21이 담고 있는 이관 DML 개수 — OWNER→ADMIN · MEMBER→OPERATOR. */
    private static final int EXPECTED_DML_COUNT = 2;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FarmMemberRepository farmMemberRepository;

    /** V21 파일에서 이관 UPDATE만 파일에 적힌 순서 그대로 골라낸다(DDL·검증 블록 제외). */
    private List<String> migrationDml() {
        String sql;
        try {
            sql = Files.readString(V21_PATH, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("V21 마이그레이션 파일을 읽을 수 없습니다: " + V21_PATH, e);
        }
        List<String> statements = Arrays.stream(sql.split(";"))
                .map(FarmRoleMigrationV21IntegrationTest::stripLeadingComments)
                .filter(statement -> statement.startsWith("UPDATE"))
                .toList();
        assertThat(statements)
                .as("V21의 이관 DML %d건(OWNER→ADMIN · MEMBER→OPERATOR)을 찾지 못했다 — 이관이 "
                        + "통째로 빠졌거나 DML이 늘었다. 늘었다면 이 테스트가 그 새 문장까지 "
                        + "검증하도록 확장할 것", EXPECTED_DML_COUNT)
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

    /**
     * ⚠️ JDBC로 행을 건드리기 <b>전에</b> 반드시 호출한다. 이 클래스는 {@code @Transactional}이라
     * 픽스처를 만든 MockMvc 호출들이 같은 트랜잭션을 공유하고, 그 영속성 컨텍스트에는 아직 flush되지
     * 않은 <b>더티 엔티티</b>가 남아 있다. 그대로 두면 이후 아무 JPA 조회나 flush 시점에 Hibernate가
     * 자기 스냅샷으로 {@code UPDATE farm_members SET role=...}를 날려 <b>방금 검증한 이관 결과를
     * 덮어쓴다</b> — 실제로 초판이 이 함정에 빠져, 이관을 VIEWER로 바꿔도 테스트가 초록이었다
     * (즉 회귀를 못 잡는 테스트였다). flush로 픽스처를 DB에 확정하고 clear로 전부 detach해,
     * 이후 JDBC 조작이 유일한 진실이 되게 한다.
     */
    private void syncAndDetach() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * V21 이전 상태를 재현한다 — 이미 적용된 CHECK 제약을 잠시 걷어내고 구 역할 문자열을 직접 써넣는다.
     * DDL도 트랜잭션 안이라 테스트 종료 시 제약과 함께 롤백된다(PostgreSQL 트랜잭션 DDL).
     */
    private void writeLegacyRole(long farmId, long userId, String legacyRole) {
        jdbcTemplate.execute("ALTER TABLE farm_members DROP CONSTRAINT IF EXISTS ck_farm_members_role");
        jdbcTemplate.update("UPDATE farm_members SET role = ? WHERE farm_id = ? AND user_id = ?",
                legacyRole, farmId, userId);
    }

    private String roleOf(long farmId, long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT role FROM farm_members WHERE farm_id = ? AND user_id = ?",
                String.class, farmId, userId);
    }

    @Test
    @DisplayName("V21 이관 DML: 구 OWNER→ADMIN · 구 MEMBER→OPERATOR로 바뀌고, 이관된 두 사용자의 "
            + "권한이 이관 전과 동일하게 유지된다 — 특히 구 MEMBER의 제어 권한(VIEWER로 내렸다면 "
            + "여기서 403 F007로 빨갛게 된다)")
    void migrationPreservesLegacyPermissions() throws Exception {
        String legacyOwnerToken = signupAndLogin("이관-구오너");
        String legacyMemberToken = signupAndLogin("이관-구멤버");
        long farmId = createFarm(legacyOwnerToken, "역할 이관 농장");
        long zoneId = createZone(legacyOwnerToken, farmId, "A동");
        joinFarmAs(legacyOwnerToken, farmId, legacyMemberToken, FarmRole.OPERATOR);

        long ownerUserId = myUserId(legacyOwnerToken);
        long memberUserId = myUserId(legacyMemberToken);

        // ── V21 직전 상태 재현: 두 행을 구 역할 문자열로 되돌린다 ──────────────────
        syncAndDetach();
        writeLegacyRole(farmId, ownerUserId, "OWNER");
        writeLegacyRole(farmId, memberUserId, "MEMBER");
        assertThat(roleOf(farmId, ownerUserId)).isEqualTo("OWNER");
        assertThat(roleOf(farmId, memberUserId)).isEqualTo("MEMBER");

        // ── 이관 실행 ────────────────────────────────────────────────────────────
        migrationDml().forEach(jdbcTemplate::execute);
        // JDBC로 바꾼 행을 JPA가 stale 1차 캐시로 읽지 않게 detach만 한다
        // (여기서 flush를 부르면 더티 엔티티가 이관 결과를 덮어쓴다 — syncAndDetach 주석 참고)
        entityManager.clear();

        assertThat(roleOf(farmId, ownerUserId))
                .as("구 OWNER는 ADMIN으로 이관돼야 한다")
                .isEqualTo("ADMIN");
        assertThat(roleOf(farmId, memberUserId))
                .as("구 MEMBER는 OPERATOR로 이관돼야 한다 — VIEWER로 내리면 제어 권한을 잃는 회귀다")
                .isEqualTo("OPERATOR");

        // ── 권한 보존 검증 ①: 구 MEMBER는 제어를 계속 할 수 있어야 한다 ─────────────
        String controlBase = "/api/farms/" + farmId + "/zones/" + zoneId + "/control";
        mockMvc.perform(put(controlBase + "/mode")
                        .header("Authorization", "Bearer " + legacyMemberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ControlModeRequest(OperationMode.AUTO))))
                .andExpect(status().isOk());
        mockMvc.perform(post(controlBase + "/changes")
                        .header("Authorization", "Bearer " + legacyMemberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ControlChangeRequest(
                                ControlChangeKind.SETPOINT, SensorMetric.TEMPERATURE, 23.0, null, null))))
                .andExpect(status().isCreated());

        // ── 권한 보존 검증 ②: 구 MEMBER는 예전처럼 구조 변경은 못 한다(F003) ─────────
        mockMvc.perform(post("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + legacyMemberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ZoneRequest("몰래 존", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));

        // ── 권한 보존 검증 ③: 구 OWNER는 구조 변경·초대 발급을 계속 할 수 있어야 한다 ──
        mockMvc.perform(post("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + legacyOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ZoneRequest("B동", null))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/farms/" + farmId + "/invitations")
                        .header("Authorization", "Bearer " + legacyOwnerToken))
                .andExpect(status().isCreated());

        // ── 권한 보존 검증 ④: 이관된 역할이 응답에도 그대로 드러난다 ─────────────────
        mockMvc.perform(get("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + legacyOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("ADMIN"));
        mockMvc.perform(get("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + legacyMemberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("OPERATOR"));

        // ── 이관 후에는 마지막 ADMIN 보호도 구 OWNER 기준으로 성립한다 ──────────────
        assertThat(farmMemberRepository.countLiveMembersByFarmIdAndRole(farmId, FarmRole.ADMIN))
                .as("구 OWNER 1명이 그대로 유일한 ADMIN이어야 한다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("이관 DML은 구 값에만 반응한다 — 이미 신 역할인 행(VIEWER·PENDING)은 건드리지 않는다 "
            + "(재실행 안전성: 부분 적용 후 재시도해도 신 역할이 뒤집히지 않는다)")
    void migrationLeavesNewRolesUntouched() throws Exception {
        String adminToken = signupAndLogin("이관-비대상관리자");
        String viewerToken = signupAndLogin("이관-비대상조회");
        String pendingToken = signupAndLogin("이관-비대상대기");
        long farmId = createFarm(adminToken, "비대상 농장");
        joinFarmAs(adminToken, farmId, viewerToken, FarmRole.VIEWER);
        acceptInvitation(pendingToken, createInvitationCode(adminToken, farmId));

        long viewerUserId = myUserId(viewerToken);
        long pendingUserId = myUserId(pendingToken);
        long adminUserId = myUserId(adminToken);

        syncAndDetach();
        migrationDml().forEach(jdbcTemplate::execute);
        entityManager.clear();

        assertThat(roleOf(farmId, adminUserId)).isEqualTo("ADMIN");
        assertThat(roleOf(farmId, viewerUserId)).isEqualTo("VIEWER");
        assertThat(roleOf(farmId, pendingUserId)).isEqualTo("PENDING");
    }
}
