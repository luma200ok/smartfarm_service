package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.AlarmMemoRequest;
import com.smartfarm.service.dto.AlarmRuleRequest;
import com.smartfarm.service.dto.ControlApplyRequest;
import com.smartfarm.service.dto.ControlChangeRequest;
import com.smartfarm.service.dto.ControlModeRequest;
import com.smartfarm.service.dto.EnvThresholdsRequest;
import com.smartfarm.service.dto.FarmLogRequest;
import com.smartfarm.service.dto.FarmUpdateRequest;
import com.smartfarm.service.dto.MemberRoleUpdateRequest;
import com.smartfarm.service.dto.NutrientRecipeRequest;
import com.smartfarm.service.dto.NutrientTargetRequest;
import com.smartfarm.service.dto.WebhookRequest;
import com.smartfarm.service.dto.ZoneRequest;
import com.smartfarm.service.entity.AlarmComparator;
import com.smartfarm.service.entity.AlarmRuleSource;
import com.smartfarm.service.entity.AlarmScopeType;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.ControlChangeKind;
import com.smartfarm.service.entity.FarmLogType;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.entity.GrowthStage;
import com.smartfarm.service.entity.OperationMode;
import com.smartfarm.service.entity.SensorMetric;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * <b>역할 × 엔드포인트 인가 매트릭스</b>(이슈 #122). 이 사이클의 회귀 방지 본체다.
 *
 * <p>권한 재편은 양방향으로 틀릴 수 있다 — <b>조이면 회귀</b>(기존 사용자가 하던 일을 못 하게 된다),
 * <b>풀면 취약점</b>(못 하던 일을 하게 된다). 기존 609개가 전부 통과하는 것만으로는 부족하다:
 * 그 테스트들은 대부분 "구 OWNER" 또는 "구 MEMBER" 시점에서 쓰였을 뿐, <b>새로 생긴 두 역할
 * (VIEWER·PENDING)이 실제로 무엇을 못 하는지</b>를 한 줄도 지나지 않는다. 그래서 4역할 전부를
 * 같은 농장에 세워 두고 표를 그대로 검증한다.
 *
 * <pre>
 *                    ADMIN   OPERATOR  VIEWER  PENDING
 *   조회               ok       ok       ok     F008
 *   제어               ok       ok      F007    F008
 *   알람 확인/처리      ok       ok      F007    F008
 *   콘텐츠 작성         ok       ok      F007    F008
 *   구조 변경           ok      F003     F003    F008
 *   멤버 관리           ok      F003     F003    F008
 * </pre>
 *
 * <p><b>거부 검증은 부수효과가 없다</b>(요청이 막히므로) — 그래서 한 농장 픽스처를 4역할이 공유해도
 * 서로 오염되지 않는다. 성공 경로만 상태를 바꾸므로 별도 절에서 확인한다.
 */
class FarmRoleMatrixIntegrationTest extends FarmTestSupport {

    private static final NutrientTargetRequest TARGET =
            new NutrientTargetRequest(90.0, 47.0, 144.0, 160.0, 60.0, 79.0);

    private String adminToken;
    private String operatorToken;
    private String viewerToken;
    private String pendingToken;
    private long farmId;
    private long zoneId;
    private long viewerMemberId;
    /** 멤버 관리 거부 검증 전용 대상 — 호출자 본인이면 "자기 탈퇴"가 되어 역할과 무관하게 허용된다. */
    private long targetMemberId;

    @BeforeEach
    void setUpFarmWithAllFourRoles() throws Exception {
        adminToken = signupAndLogin("매트릭스-관리자");
        operatorToken = signupAndLogin("매트릭스-제어자");
        viewerToken = signupAndLogin("매트릭스-조회자");
        pendingToken = signupAndLogin("매트릭스-대기자");

        farmId = createFarm(adminToken, "권한 매트릭스 농장");
        zoneId = createZone(adminToken, farmId, "A동");

        joinFarmAs(adminToken, farmId, operatorToken, FarmRole.OPERATOR);
        viewerMemberId = joinFarmAs(adminToken, farmId, viewerToken, FarmRole.VIEWER);
        targetMemberId = joinFarmAs(adminToken, farmId, signupAndLogin("매트릭스-대상"), FarmRole.VIEWER);
        // PENDING = 수락만 하고 승인은 받지 않은 상태(#122 결정 ⓑ)
        acceptInvitation(pendingToken, createInvitationCode(adminToken, farmId));
    }

    // ── 요청 카탈로그 ────────────────────────────────────────

    /** 조회 표면 — 승인된 멤버 전원(ADMIN·OPERATOR·VIEWER) 허용. */
    private List<Supplier<MockHttpServletRequestBuilder>> readRequests() {
        return List.of(
                () -> MockMvcRequestBuilders.get("/api/farms/" + farmId),
                () -> MockMvcRequestBuilders.get("/api/farms/" + farmId + "/members"),
                () -> MockMvcRequestBuilders.get("/api/farms/" + farmId + "/zones"),
                () -> MockMvcRequestBuilders.get("/api/farms/" + farmId + "/devices"),
                () -> MockMvcRequestBuilders.get("/api/farms/" + farmId + "/logs"),
                () -> MockMvcRequestBuilders.get("/api/farms/" + farmId + "/nutrient-recipes"),
                () -> MockMvcRequestBuilders.get("/api/farms/" + farmId + "/env-thresholds"),
                () -> MockMvcRequestBuilders.get("/api/farms/" + farmId + "/alarm-rules"),
                () -> MockMvcRequestBuilders.get("/api/farms/" + farmId + "/alarm-events"),
                () -> MockMvcRequestBuilders.get("/api/farms/" + farmId + "/alarm-events/unacknowledged-count"),
                // 제어 상태 조회는 읽기 경로다 — VIEWER도 현재 모드·목표값을 볼 수 있어야 한다
                () -> MockMvcRequestBuilders.get("/api/farms/" + farmId + "/zones/" + zoneId + "/control"));
    }

    /** 제어 표면 — OPERATOR 이상. 구 MEMBER가 하던 일 전량이 여기 있다(회귀 감시 지점). */
    private List<Supplier<MockHttpServletRequestBuilder>> controlRequests() {
        String base = "/api/farms/" + farmId + "/zones/" + zoneId + "/control";
        return List.of(
                () -> json(MockMvcRequestBuilders.put(base + "/mode"),
                        new ControlModeRequest(OperationMode.MANUAL)),
                () -> json(MockMvcRequestBuilders.post(base + "/changes"),
                        new ControlChangeRequest(ControlChangeKind.SETPOINT, SensorMetric.TEMPERATURE,
                                23.0, null, null)),
                () -> MockMvcRequestBuilders.delete(base + "/changes"),
                () -> MockMvcRequestBuilders.delete(base + "/changes/1"),
                () -> json(MockMvcRequestBuilders.post(base + "/apply"),
                        new ControlApplyRequest(List.of())),
                // 비상 정지 — #122 결정 ⓐ로 OWNER 전용에서 OPERATOR 이상으로 완화된 지점
                () -> MockMvcRequestBuilders.post("/api/farms/" + farmId + "/control/emergency-stop"));
    }

    /** 알람 확인/처리 — OPERATOR 이상(조회는 read 카탈로그에 있다). */
    private List<Supplier<MockHttpServletRequestBuilder>> alarmHandlingRequests() {
        String base = "/api/farms/" + farmId + "/alarm-events";
        return List.of(
                () -> MockMvcRequestBuilders.post(base + "/acknowledge-all"),
                () -> MockMvcRequestBuilders.patch(base + "/1/acknowledge"),
                () -> MockMvcRequestBuilders.post(base + "/1/resolve"),
                () -> json(MockMvcRequestBuilders.post(base + "/1/memo"), new AlarmMemoRequest("메모")));
    }

    /**
     * 콘텐츠 작성 — OPERATOR 이상(#122 판단). VIEWER에게 작성을 허용하면 author가 되어
     * "author OR ADMIN" 삭제 규칙으로 삭제 권한까지 갖는다.
     */
    private List<Supplier<MockHttpServletRequestBuilder>> authoringRequests() {
        return List.of(
                () -> json(MockMvcRequestBuilders.post("/api/farms/" + farmId + "/logs"),
                        new FarmLogRequest(LocalDate.now(), FarmLogType.WATERING, "메모")),
                () -> json(MockMvcRequestBuilders.post("/api/farms/" + farmId + "/nutrient-recipes"),
                        new NutrientRecipeRequest("레시피", GrowthStage.SEEDLING, TARGET, 1.0, 1.0, null)));
    }

    /** 구조 변경 — ADMIN 전용. */
    private List<Supplier<MockHttpServletRequestBuilder>> structureRequests() {
        return List.of(
                () -> json(MockMvcRequestBuilders.patch("/api/farms/" + farmId),
                        new FarmUpdateRequest("바뀐 이름", null, null)),
                () -> MockMvcRequestBuilders.delete("/api/farms/" + farmId),
                () -> json(MockMvcRequestBuilders.patch("/api/farms/" + farmId + "/webhook"),
                        new WebhookRequest(null)),
                () -> json(MockMvcRequestBuilders.post("/api/farms/" + farmId + "/zones"),
                        new ZoneRequest("새 존", null)),
                () -> MockMvcRequestBuilders.delete("/api/farms/" + farmId + "/zones/" + zoneId),
                () -> MockMvcRequestBuilders.post("/api/farms/" + farmId + "/invitations"),
                () -> json(MockMvcRequestBuilders.put("/api/farms/" + farmId + "/env-thresholds"),
                        new EnvThresholdsRequest(true, 10.0, 30.0, null, null)),
                () -> json(MockMvcRequestBuilders.post("/api/farms/" + farmId + "/alarm-rules"),
                        new AlarmRuleRequest("규칙", true, AlarmRuleSource.SENSOR_READING,
                                SensorMetric.EC.name(), AlarmComparator.GT, 2.8, null, null, 300,
                                AlarmSeverity.WARNING, AlarmScopeType.FARM, null)));
    }

    /** 멤버 관리 — ADMIN 전용. */
    private List<Supplier<MockHttpServletRequestBuilder>> memberManagementRequests() {
        return List.of(
                () -> json(MockMvcRequestBuilders
                                .patch("/api/farms/" + farmId + "/members/" + targetMemberId + "/role"),
                        new MemberRoleUpdateRequest(FarmRole.ADMIN)),
                // 타인 제거 — 본인 탈퇴(DELETE 본인 memberId)는 역할과 무관하게 허용되므로 제3자를 쓴다
                () -> MockMvcRequestBuilders
                        .delete("/api/farms/" + farmId + "/members/" + targetMemberId));
    }

    // ── 매트릭스 검증 ────────────────────────────────────────

    @Nested
    @DisplayName("PENDING(승인 대기) — farm-scoped 표면 전체가 F008로 막힌다")
    class PendingIsFullyBlocked {

        @Test
        @DisplayName("조회·제어·알람처리·작성·구조변경·멤버관리 전 표면에서 403 F008")
        void everyFarmScopedSurfaceIsBlocked() throws Exception {
            expectAll(pendingToken, readRequests(), "F008");
            expectAll(pendingToken, controlRequests(), "F008");
            expectAll(pendingToken, alarmHandlingRequests(), "F008");
            expectAll(pendingToken, authoringRequests(), "F008");
            expectAll(pendingToken, structureRequests(), "F008");
            expectAll(pendingToken, memberManagementRequests(), "F008");
        }

        @Test
        @DisplayName("본인 멤버십 제거(농장 나가기)도 F008 — 승인 전에는 어떤 farm 경로도 열리지 않는다")
        void evenSelfRemovalIsBlocked() throws Exception {
            long pendingMemberId = memberIdOfUser(adminToken, farmId, myUserId(pendingToken));

            mockMvc.perform(MockMvcRequestBuilders
                            .delete("/api/farms/" + farmId + "/members/" + pendingMemberId)
                            .header("Authorization", "Bearer " + pendingToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("F008"));
        }

        @Test
        @DisplayName("내 농장 목록에는 myRole=PENDING으로 보인다 — 승인 대기 중임을 알 수 있어야 한다")
        void pendingFarmIsVisibleInMyFarmsWithPendingRole() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.get("/api/farms")
                            .header("Authorization", "Bearer " + pendingToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(farmId))
                    .andExpect(jsonPath("$[0].myRole").value("PENDING"));
        }
    }

    @Nested
    @DisplayName("VIEWER(조회전용) — 조회만 열리고 쓰기는 전부 막힌다")
    class ViewerCanOnlyRead {

        @Test
        @DisplayName("조회 표면은 전부 200")
        void readSurfacesAreOpen() throws Exception {
            expectAllOk(viewerToken, readRequests());
        }

        @Test
        @DisplayName("제어·알람처리·작성은 403 F007 (OPERATOR 권한 필요)")
        void writeSurfacesRequireOperator() throws Exception {
            expectAll(viewerToken, controlRequests(), "F007");
            expectAll(viewerToken, alarmHandlingRequests(), "F007");
            expectAll(viewerToken, authoringRequests(), "F007");
        }

        @Test
        @DisplayName("구조 변경·멤버 관리는 403 F003 (ADMIN 권한 필요)")
        void adminSurfacesRequireAdmin() throws Exception {
            expectAll(viewerToken, structureRequests(), "F003");
            expectAll(viewerToken, memberManagementRequests(), "F003");
        }
    }

    @Nested
    @DisplayName("OPERATOR(제어가능) — 구 MEMBER의 권한을 그대로 승계한다(회귀 감시)")
    class OperatorInheritsLegacyMember {

        @Test
        @DisplayName("조회 표면은 전부 200")
        void readSurfacesAreOpen() throws Exception {
            expectAllOk(operatorToken, readRequests());
        }

        @Test
        @DisplayName("제어 전 표면이 열린다 — 모드 변경·큐 적재·적용·전체 취소·비상 정지")
        void controlSurfacesAreOpen() throws Exception {
            String base = "/api/farms/" + farmId + "/zones/" + zoneId + "/control";

            // AUTO에서만 SETPOINT 적재가 허용된다(§4.12 모드별 허용 조작) — 인가가 아니라 도메인 규칙
            mockMvc.perform(json(MockMvcRequestBuilders.put(base + "/mode"),
                            new ControlModeRequest(OperationMode.AUTO))
                            .header("Authorization", "Bearer " + operatorToken))
                    .andExpect(status().isOk());

            mockMvc.perform(json(MockMvcRequestBuilders.post(base + "/changes"),
                            new ControlChangeRequest(ControlChangeKind.SETPOINT, SensorMetric.TEMPERATURE,
                                    23.0, null, null))
                            .header("Authorization", "Bearer " + operatorToken))
                    .andExpect(status().isCreated());

            mockMvc.perform(MockMvcRequestBuilders.delete(base + "/changes")
                            .header("Authorization", "Bearer " + operatorToken))
                    .andExpect(status().isNoContent());

            mockMvc.perform(json(MockMvcRequestBuilders.post(base + "/apply"),
                            new ControlApplyRequest(List.of()))
                            .header("Authorization", "Bearer " + operatorToken))
                    .andExpect(status().isOk());

            mockMvc.perform(MockMvcRequestBuilders
                            .post("/api/farms/" + farmId + "/control/emergency-stop")
                            .header("Authorization", "Bearer " + operatorToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("콘텐츠 작성·알람 전체 확인이 열린다")
        void authoringAndAlarmHandlingAreOpen() throws Exception {
            expectAllOk(operatorToken, authoringRequests());

            mockMvc.perform(MockMvcRequestBuilders
                            .post("/api/farms/" + farmId + "/alarm-events/acknowledge-all")
                            .header("Authorization", "Bearer " + operatorToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("구조 변경·멤버 관리는 403 F003 — 여기가 ADMIN과의 경계다")
        void structureAndMemberManagementAreClosed() throws Exception {
            expectAll(operatorToken, structureRequests(), "F003");
            expectAll(operatorToken, memberManagementRequests(), "F003");
        }
    }

    @Nested
    @DisplayName("ADMIN(관리자) — 구 OWNER의 권한을 전량 승계한다")
    class AdminInheritsLegacyOwner {

        @Test
        @DisplayName("조회·제어·작성이 모두 열린다 (OPERATOR 요구를 ADMIN이 통과한다)")
        void adminPassesOperatorGates() throws Exception {
            expectAllOk(adminToken, readRequests());
            expectAllOk(adminToken, authoringRequests());

            mockMvc.perform(MockMvcRequestBuilders
                            .post("/api/farms/" + farmId + "/control/emergency-stop")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("구조 변경·멤버 관리가 열린다 — 존 생성·초대 발급·임계치 설정·역할 변경")
        void adminSurfacesAreOpen() throws Exception {
            mockMvc.perform(json(MockMvcRequestBuilders.post("/api/farms/" + farmId + "/zones"),
                            new ZoneRequest("관리자 존", null))
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isCreated());

            mockMvc.perform(MockMvcRequestBuilders.post("/api/farms/" + farmId + "/invitations")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isCreated());

            mockMvc.perform(json(MockMvcRequestBuilders.put("/api/farms/" + farmId + "/env-thresholds"),
                            new EnvThresholdsRequest(true, 10.0, 30.0, null, null))
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());

            mockMvc.perform(json(MockMvcRequestBuilders
                                    .patch("/api/farms/" + farmId + "/members/" + viewerMemberId + "/role"),
                            new MemberRoleUpdateRequest(FarmRole.OPERATOR))
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("OPERATOR"));
        }
    }

    @Nested
    @DisplayName("승인 흐름 — PENDING은 역할을 받는 순간 그 역할만큼 열린다")
    class ApprovalOpensAccess {

        @Test
        @DisplayName("PENDING → VIEWER 승인 시 조회는 열리고 제어는 여전히 F007")
        void approveAsViewer() throws Exception {
            long pendingMemberId = memberIdOfUser(adminToken, farmId, myUserId(pendingToken));
            changeMemberRole(adminToken, farmId, pendingMemberId, FarmRole.VIEWER);

            expectAllOk(pendingToken, readRequests());
            expectAll(pendingToken, controlRequests(), "F007");
        }

        @Test
        @DisplayName("PENDING → OPERATOR 승인 시 제어까지 열린다")
        void approveAsOperator() throws Exception {
            long pendingMemberId = memberIdOfUser(adminToken, farmId, myUserId(pendingToken));
            changeMemberRole(adminToken, farmId, pendingMemberId, FarmRole.OPERATOR);

            mockMvc.perform(json(MockMvcRequestBuilders
                                    .put("/api/farms/" + farmId + "/zones/" + zoneId + "/control/mode"),
                            new ControlModeRequest(OperationMode.AUTO))
                            .header("Authorization", "Bearer " + pendingToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("역할을 PENDING으로 되돌리면 접근이 다시 회수된다(F008)")
        void revokingBackToPendingClosesAccess() throws Exception {
            long operatorMemberId = memberIdOfUser(adminToken, farmId, myUserId(operatorToken));
            changeMemberRole(adminToken, farmId, operatorMemberId, FarmRole.PENDING);

            expectAll(operatorToken, readRequests(), "F008");
            expectAll(operatorToken, controlRequests(), "F008");
        }
    }

    // ── 헬퍼 ────────────────────────────────────────────────

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) {
        try {
            return builder.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalStateException("요청 본문 직렬화 실패", e);
        }
    }

    /** 모든 요청이 같은 ErrorCode로 거부되는지 — 거부 경로라 부수효과가 없어 한 픽스처를 공유한다. */
    private void expectAll(String token, List<Supplier<MockHttpServletRequestBuilder>> requests,
                           String expectedCode) throws Exception {
        for (Supplier<MockHttpServletRequestBuilder> request : requests) {
            mockMvc.perform(request.get().header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(expectedCode));
        }
    }

    /** 모든 요청이 2xx인지 — 인가를 통과했는지만 본다(도메인 응답 본문은 각 도메인 테스트의 몫). */
    private void expectAllOk(String token, List<Supplier<MockHttpServletRequestBuilder>> requests)
            throws Exception {
        for (Supplier<MockHttpServletRequestBuilder> request : requests) {
            mockMvc.perform(request.get().header("Authorization", "Bearer " + token))
                    .andExpect(status().is2xxSuccessful());
        }
    }
}
