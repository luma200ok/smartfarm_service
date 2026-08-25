package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.entity.FarmRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code /api/farms/{farmId}/saved-analyses} 통합 테스트(이슈 #126) — CRUD·권한(작성 OPERATOR
 * 이상·수정삭제 작성자 OR ADMIN)·상한(락)·<b>cross-tenant 스코프 격리(§4.10 미소속 404)</b>를
 * 검증한다.
 *
 * <p>모든 권한 테스트는 <b>실제 사용자가 손에 넣을 수 있는 토큰</b>만 쓴다 — VIEWER·다른 OPERATOR의
 * 403은 그 역할로 로그인한 진짜 토큰으로 검증하고, ADMIN 토큰을 빌려 얻은 값으로 "이 사용자가
 * 도달 가능하다"고 위장하지 않는다.
 */
class SavedAnalysisApiIntegrationTest extends FarmTestSupport {

    private static final String FARM_SCOPE_ANALYSIS = """
            {"name":"주간 EC 편차 점검","metrics":["EC","PH"],"range":"7d","scopeType":"FARM"}
            """;

    private MvcResult createAnalysis(String token, long farmId, String body) throws Exception {
        return mockMvc.perform(post("/api/farms/" + farmId + "/saved-analyses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }

    // ── CRUD ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ADMIN(농장 생성자)은 저장한 분석을 만들고 목록에서 확인할 수 있다(프리뷰 '주간 EC 편차 점검')")
    void adminCanCreateAndListAnalysis() throws Exception {
        String token = signupAndLogin("분석주인1");
        long farmId = createFarm(token, "분석농장1");

        long id = readJson(createAnalysis(token, farmId, FARM_SCOPE_ANALYSIS)).get("id").asLong();

        mockMvc.perform(get("/api/farms/" + farmId + "/saved-analyses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].name").value("주간 EC 편차 점검"))
                .andExpect(jsonPath("$[0].metrics.length()").value(2))
                .andExpect(jsonPath("$[0].metrics[0]").value("EC"))
                .andExpect(jsonPath("$[0].metrics[1]").value("PH"))
                .andExpect(jsonPath("$[0].range").value("7d"))
                .andExpect(jsonPath("$[0].scopeType").value("FARM"))
                .andExpect(jsonPath("$[0].scopeId").doesNotExist());
    }

    @Test
    @DisplayName("PATCH로 이름만 바꿀 수 있다(작성자 본인)")
    void authorCanRenameAnalysis() throws Exception {
        String token = signupAndLogin("분석주인2");
        long farmId = createFarm(token, "분석농장2");
        long id = readJson(createAnalysis(token, farmId, FARM_SCOPE_ANALYSIS)).get("id").asLong();

        mockMvc.perform(patch("/api/farms/" + farmId + "/saved-analyses/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"주간 EC 편차 점검(수정)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("주간 EC 편차 점검(수정)"))
                // metrics/range/scope는 PATCH 대상이 아니라 그대로여야 한다.
                .andExpect(jsonPath("$.range").value("7d"));
    }

    @Test
    @DisplayName("DELETE 후에는 목록에서 사라지고 재조회는 그대로 200 빈 목록이다")
    void deletedAnalysisIsGone() throws Exception {
        String token = signupAndLogin("분석주인3");
        long farmId = createFarm(token, "분석농장3");
        long id = readJson(createAnalysis(token, farmId, FARM_SCOPE_ANALYSIS)).get("id").asLong();

        mockMvc.perform(delete("/api/farms/" + farmId + "/saved-analyses/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/farms/" + farmId + "/saved-analyses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("삭제된(혹은 존재하지 않는) 분석 id로 PATCH하면 404 SA001이다")
    void renameMissingAnalysisReturnsSA001() throws Exception {
        String token = signupAndLogin("분석주인4");
        long farmId = createFarm(token, "분석농장4");

        mockMvc.perform(patch("/api/farms/" + farmId + "/saved-analyses/999999")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"없음\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SA001"));
    }

    // ── 권한 ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("VIEWER는 목록 조회는 되지만 생성은 403 F007이다(작성은 OPERATOR 이상 — #122 원칙)")
    void viewerCanReadButNotCreate() throws Exception {
        String adminToken = signupAndLogin("분석관리자1");
        long farmId = createFarm(adminToken, "분석농장5");
        String viewerToken = signupAndLogin("분석뷰어1");
        joinFarmAs(adminToken, farmId, viewerToken, FarmRole.VIEWER);

        mockMvc.perform(get("/api/farms/" + farmId + "/saved-analyses")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/farms/" + farmId + "/saved-analyses")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FARM_SCOPE_ANALYSIS))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F007"));
    }

    @Test
    @DisplayName("VIEWER가 작성 자체를 못 하므로 '작성자가 되어 삭제 권한을 얻는' 우회 경로가 없다"
            + "(#122 원칙 재확인 — F007이 첫 관문에서 막는다)")
    void viewerCannotBypassByAuthoringThenDeleting() throws Exception {
        String adminToken = signupAndLogin("분석관리자2");
        long farmId = createFarm(adminToken, "분석농장6");
        String viewerToken = signupAndLogin("분석뷰어2");
        joinFarmAs(adminToken, farmId, viewerToken, FarmRole.VIEWER);

        mockMvc.perform(post("/api/farms/" + farmId + "/saved-analyses")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FARM_SCOPE_ANALYSIS))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F007"));
    }

    @Test
    @DisplayName("작성자가 아닌 다른 OPERATOR는 수정·삭제가 403 SA004이다"
            + "(ADMIN 토큰이 아니라 그 OPERATOR 본인의 실제 토큰으로 검증)")
    void nonAuthorOperatorCannotRenameOrDelete() throws Exception {
        String adminToken = signupAndLogin("분석관리자3");
        long farmId = createFarm(adminToken, "분석농장7");
        String authorToken = signupAndLogin("분석작성자1");
        joinFarmAs(adminToken, farmId, authorToken, FarmRole.OPERATOR);
        String otherOperatorToken = signupAndLogin("분석타인1");
        joinFarmAs(adminToken, farmId, otherOperatorToken, FarmRole.OPERATOR);

        long id = readJson(createAnalysis(authorToken, farmId, FARM_SCOPE_ANALYSIS)).get("id").asLong();

        mockMvc.perform(patch("/api/farms/" + farmId + "/saved-analyses/" + id)
                        .header("Authorization", "Bearer " + otherOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"가로채기 시도\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SA004"));

        mockMvc.perform(delete("/api/farms/" + farmId + "/saved-analyses/" + id)
                        .header("Authorization", "Bearer " + otherOperatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SA004"));
    }

    @Test
    @DisplayName("ADMIN은 타인이 작성한 분석도 삭제할 수 있다(author OR ADMIN, FarmLogService 선례)")
    void adminCanDeleteOthersAnalysis() throws Exception {
        String adminToken = signupAndLogin("분석관리자4");
        long farmId = createFarm(adminToken, "분석농장8");
        String authorToken = signupAndLogin("분석작성자2");
        joinFarmAs(adminToken, farmId, authorToken, FarmRole.OPERATOR);

        long id = readJson(createAnalysis(authorToken, farmId, FARM_SCOPE_ANALYSIS)).get("id").asLong();

        mockMvc.perform(delete("/api/farms/" + farmId + "/saved-analyses/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("ADMIN은 타인이 작성한 분석의 이름도 바꿀 수 있다 — 삭제와 같은 author OR ADMIN 규칙")
    void adminCanRenameOthersAnalysis() throws Exception {
        // 한쪽만 작성자 전용이면 ADMIN이 팀원의 오타 난 이름을 고칠 수는 없으면서 지울 수는 있는
        // 비대칭이 된다(#126 보안 리뷰 P2 — Swagger는 "작성자 본인 또는 ADMIN"인데 코드는 작성자 전용이었다).
        String adminToken = signupAndLogin("분석관리자5");
        long farmId = createFarm(adminToken, "분석농장11");
        String authorToken = signupAndLogin("분석작성자3");
        joinFarmAs(adminToken, farmId, authorToken, FarmRole.OPERATOR);

        long id = readJson(createAnalysis(authorToken, farmId, FARM_SCOPE_ANALYSIS)).get("id").asLong();

        mockMvc.perform(patch("/api/farms/" + farmId + "/saved-analyses/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"관리자가 고친 이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("관리자가 고친 이름"));
    }

    @Test
    @DisplayName("타 농장의 분석 id로 접근하면 404 SA001이다(cross-tenant IDOR — 존재를 유추당하지 않는다)")
    void crossTenantAnalysisIdIsNotFound() throws Exception {
        String ownerA = signupAndLogin("분석A주인");
        long farmA = createFarm(ownerA, "A분석농장");
        long idInA = readJson(createAnalysis(ownerA, farmA, FARM_SCOPE_ANALYSIS)).get("id").asLong();

        String ownerB = signupAndLogin("분석B주인");
        long farmB = createFarm(ownerB, "B분석농장");

        mockMvc.perform(delete("/api/farms/" + farmB + "/saved-analyses/" + idInA)
                        .header("Authorization", "Bearer " + ownerB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SA001"));
    }

    // ── 스코프 격리(§4.10) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("자기 농장의 랙을 스코프로 지정하면 생성된다(랙 단위 스코프)")
    void rackScopedAnalysisCanBeCreated() throws Exception {
        String token = signupAndLogin("분석스코프주인1");
        long farmId = createFarm(token, "분석스코프농장1");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "B3", 5);

        mockMvc.perform(post("/api/farms/" + farmId + "/saved-analyses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"B3랙 온습도","metrics":["TEMPERATURE","HUMIDITY"],"range":"24h",
                                 "scopeType":"RACK","scopeId":%d}
                                """.formatted(rackId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scopeType").value("RACK"))
                .andExpect(jsonPath("$.scopeId").value(rackId));
    }

    @Test
    @DisplayName("타 농장의 랙을 scopeId로 지정하면 404 R002다(cross-tenant IDOR 차단 — AlarmScopeResolver 재사용)")
    void otherFarmRackAsScopeIsNotFound() throws Exception {
        String ownerA = signupAndLogin("분석스코프주인A");
        long farmA = createFarm(ownerA, "분석스코프A농장");
        long zoneA = createZone(ownerA, farmA, "A동");
        long rackInA = createRack(ownerA, farmA, zoneA, "B3", 5);

        String ownerB = signupAndLogin("분석스코프주인B");
        long farmB = createFarm(ownerB, "분석스코프B농장");

        mockMvc.perform(post("/api/farms/" + farmB + "/saved-analyses")
                        .header("Authorization", "Bearer " + ownerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"남의 랙 감시","metrics":["EC"],"range":"24h",
                                 "scopeType":"RACK","scopeId":%d}
                                """.formatted(rackInA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R002"));
    }

    @Test
    @DisplayName("FARM 스코프에 scopeId를 주거나, 하위 스코프에 scopeId를 생략하면 400 C001이다")
    void scopeShapeMismatchIsRejected() throws Exception {
        String token = signupAndLogin("분석스코프주인2");
        long farmId = createFarm(token, "분석스코프농장2");

        mockMvc.perform(post("/api/farms/" + farmId + "/saved-analyses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"이상한 스코프","metrics":["EC"],"range":"24h",
                                 "scopeType":"FARM","scopeId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        mockMvc.perform(post("/api/farms/" + farmId + "/saved-analyses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"이상한 스코프2","metrics":["EC"],"range":"24h","scopeType":"ZONE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    // ── 필드 검증 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("metrics가 비어 있거나 5개 이상이면 400 C001이다")
    void metricsCountIsValidated() throws Exception {
        String token = signupAndLogin("분석검증주인1");
        long farmId = createFarm(token, "분석검증농장1");

        mockMvc.perform(post("/api/farms/" + farmId + "/saved-analyses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"빈 지표","metrics":[],"range":"24h","scopeType":"FARM"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        mockMvc.perform(post("/api/farms/" + farmId + "/saved-analyses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"지표 초과","metrics":["TEMPERATURE","HUMIDITY","CO2","EC","PH"],
                                 "range":"24h","scopeType":"FARM"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("range가 24h/7d/30d가 아니면 400 C001이다")
    void invalidRangeIsRejected() throws Exception {
        String token = signupAndLogin("분석검증주인2");
        long farmId = createFarm(token, "분석검증농장2");

        mockMvc.perform(post("/api/farms/" + farmId + "/saved-analyses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"이상한 기간","metrics":["EC"],"range":"1h","scopeType":"FARM"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("중복 지표는 저장 시 정리된다(['EC','EC','PH'] → 2개)")
    void duplicateMetricsAreDeduplicated() throws Exception {
        String token = signupAndLogin("분석검증주인3");
        long farmId = createFarm(token, "분석검증농장3");

        createAnalysis(token, farmId, """
                {"name":"중복 지표","metrics":["EC","EC","PH"],"range":"24h","scopeType":"FARM"}
                """);

        mockMvc.perform(get("/api/farms/" + farmId + "/saved-analyses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].metrics.length()").value(2))
                .andExpect(jsonPath("$[0].metrics[0]").value("EC"))
                .andExpect(jsonPath("$[0].metrics[1]").value("PH"));
    }

    // ── 상한(락) ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("농장당 저장한 분석 상한(50건)을 넘기면 409 SA002다(#91 리소스 생성 상한 정책, "
            + "AlarmRuleService의 농장 행 잠금 안 count 관용구 재사용)")
    void analysisCountLimitIsEnforced() throws Exception {
        String token = signupAndLogin("분석상한주인");
        long farmId = createFarm(token, "분석상한농장");
        for (int i = 0; i < 50; i++) {
            createAnalysis(token, farmId, """
                    {"name":"분석%d","metrics":["EC"],"range":"24h","scopeType":"FARM"}
                    """.formatted(i));
        }

        mockMvc.perform(post("/api/farms/" + farmId + "/saved-analyses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FARM_SCOPE_ANALYSIS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SA002"));
    }
}
