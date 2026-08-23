package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.RackRequest;
import com.smartfarm.service.dto.ZoneRequest;
import com.smartfarm.service.dto.ZoneUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ZoneApiIntegrationTest extends FarmTestSupport {

    // ── 생성 ──────────────────────────────────────────────

    @Test
    @DisplayName("OWNER는 존을 201로 생성할 수 있다")
    void createZoneSuccess() throws Exception {
        String token = signupAndLogin("농장주-존생성");
        long farmId = createFarm(token, "존 농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ZoneRequest("A동", 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("A동"))
                .andExpect(jsonPath("$.displayOrder").value(1));
    }

    @Test
    @DisplayName("이름 누락 시 400 C001을 반환한다")
    void createZoneValidationFailure() throws Exception {
        String token = signupAndLogin("농장주-존검증");
        long farmId = createFarm(token, "검증 농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("일반 멤버(OWNER 아님)는 존 생성 시 403 F003을 반환한다")
    void createZoneAsMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("농장주-존권한");
        String memberToken = signupAndLogin("멤버-존권한");
        long farmId = createFarm(ownerToken, "권한 농장");
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));

        mockMvc.perform(post("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ZoneRequest("B동", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 존 생성 시 403 F002를 반환한다")
    void createZoneAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("농장주-존격리");
        String otherToken = signupAndLogin("남남-존격리");
        long farmId = createFarm(ownerToken, "격리 농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ZoneRequest("C동", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("데모 계정은 존 생성 시 403 A007을 반환한다(파괴적 작업 차단)")
    void createZoneAsDemoAccountForbidden() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/demo-login"))
                .andExpect(status().isOk())
                .andReturn();
        String demoToken = readJson(loginResult).get("accessToken").asText();
        MvcResult farmsResult = mockMvc.perform(get("/api/farms")
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isOk())
                .andReturn();
        long demoFarmId = readJson(farmsResult).get(0).get("id").asLong();

        mockMvc.perform(post("/api/farms/" + demoFarmId + "/zones")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ZoneRequest("데모동", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"));
    }

    // ── 트리 조회 ──────────────────────────────────────────

    @Test
    @DisplayName("존+랙+층 트리를 표시 순서대로 반환한다")
    void findZoneTreeSuccess() throws Exception {
        String token = signupAndLogin("농장주-트리");
        long farmId = createFarm(token, "트리 농장");
        long zoneId = createZone(token, farmId, "A동");
        createRack(token, farmId, zoneId, "R1", 3);

        mockMvc.perform(get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones.length()").value(1))
                .andExpect(jsonPath("$.zones[0].name").value("A동"))
                .andExpect(jsonPath("$.zones[0].racks.length()").value(1))
                .andExpect(jsonPath("$.zones[0].racks[0].code").value("R1"))
                .andExpect(jsonPath("$.zones[0].racks[0].levelCount").value(3))
                .andExpect(jsonPath("$.zones[0].racks[0].levels.length()").value(3))
                .andExpect(jsonPath("$.zones[0].racks[0].levels[0].levelNo").value(1))
                .andExpect(jsonPath("$.zones[0].racks[0].levels[2].levelNo").value(3));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 트리 조회 시 403 F002를 반환한다")
    void findZoneTreeAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("농장주-트리격리");
        String otherToken = signupAndLogin("남남-트리격리");
        long farmId = createFarm(ownerToken, "트리 격리 농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    // ── 수정 ──────────────────────────────────────────────

    @Test
    @DisplayName("OWNER는 존을 200으로 부분 수정할 수 있다")
    void updateZoneSuccess() throws Exception {
        String token = signupAndLogin("농장주-존수정");
        long farmId = createFarm(token, "수정 농장");
        long zoneId = createZone(token, farmId, "원래이름");

        mockMvc.perform(patch("/api/farms/" + farmId + "/zones/" + zoneId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ZoneUpdateRequest("바뀐이름", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("바뀐이름"))
                .andExpect(jsonPath("$.displayOrder").value(5));
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 zoneId로 수정 시 404 R001을 반환한다(농장 스코프)")
    void updateZoneCrossTenantZoneId() throws Exception {
        String ownerAToken = signupAndLogin("농장주A-존");
        String ownerBToken = signupAndLogin("농장주B-존");
        long farmA = createFarm(ownerAToken, "존 농장 A");
        long farmB = createFarm(ownerBToken, "존 농장 B");
        long zoneId = createZone(ownerAToken, farmA, "A동");

        mockMvc.perform(patch("/api/farms/" + farmB + "/zones/" + zoneId)
                        .header("Authorization", "Bearer " + ownerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ZoneUpdateRequest("탈취시도", null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    // ── 삭제(캐스케이드) ────────────────────────────────────

    @Test
    @DisplayName("존 삭제 시 하위 랙·층까지 함께 soft delete되어 트리에서 사라진다")
    void deleteZoneCascadesRacksAndLevels() throws Exception {
        String token = signupAndLogin("농장주-존삭제");
        long farmId = createFarm(token, "삭제 농장");
        long zoneId = createZone(token, farmId, "삭제될동");
        createRack(token, farmId, zoneId, "R1", 2);

        mockMvc.perform(delete("/api/farms/" + farmId + "/zones/" + zoneId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones.length()").value(0));

        // 삭제된 존에 랙을 다시 생성하려 하면 404 R001(soft delete 이후 조회 불가)
        mockMvc.perform(post("/api/farms/" + farmId + "/zones/" + zoneId + "/racks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackRequest("R2", 1, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    @Test
    @DisplayName("존재하지 않는 zoneId 삭제 시 404 R001을 반환한다")
    void deleteZoneNotFound() throws Exception {
        String token = signupAndLogin("농장주-존삭제미존재");
        long farmId = createFarm(token, "미존재 삭제 농장");

        mockMvc.perform(delete("/api/farms/" + farmId + "/zones/999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    // ── 랙 생성(존 하위) ─────────────────────────────────────

    @Test
    @DisplayName("랙 생성 시 levelCount만큼 층이 자동 생성된다")
    void createRackAutoGeneratesLevels() throws Exception {
        String token = signupAndLogin("농장주-랙생성");
        long farmId = createFarm(token, "랙생성 농장");
        long zoneId = createZone(token, farmId, "A동");

        mockMvc.perform(post("/api/farms/" + farmId + "/zones/" + zoneId + "/racks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackRequest("R1", 5, 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("R1"))
                .andExpect(jsonPath("$.levelCount").value(5));
    }

    @Test
    @DisplayName("같은 존 내 랙 코드 중복 시 409 R004를 반환한다")
    void createRackDuplicateCodeConflict() throws Exception {
        String token = signupAndLogin("농장주-랙중복");
        long farmId = createFarm(token, "랙중복 농장");
        long zoneId = createZone(token, farmId, "A동");
        createRack(token, farmId, zoneId, "R1", 3);

        mockMvc.perform(post("/api/farms/" + farmId + "/zones/" + zoneId + "/racks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackRequest("R1", 2, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R004"));
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 zoneId로 랙 생성 시 404 R001을 반환한다")
    void createRackCrossTenantZoneId() throws Exception {
        String ownerAToken = signupAndLogin("농장주A-랙격리");
        String ownerBToken = signupAndLogin("농장주B-랙격리");
        long farmA = createFarm(ownerAToken, "랙격리 농장 A");
        long farmB = createFarm(ownerBToken, "랙격리 농장 B");
        long zoneIdInFarmA = createZone(ownerAToken, farmA, "A동");

        mockMvc.perform(post("/api/farms/" + farmB + "/zones/" + zoneIdInFarmA + "/racks")
                        .header("Authorization", "Bearer " + ownerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackRequest("탈취랙", 1, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }
}
