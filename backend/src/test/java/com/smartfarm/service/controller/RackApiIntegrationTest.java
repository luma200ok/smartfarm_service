package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.DeviceRequest;
import com.smartfarm.service.dto.RackRequest;
import com.smartfarm.service.dto.RackUpdateRequest;
import com.smartfarm.service.entity.DeviceKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class RackApiIntegrationTest extends FarmTestSupport {

    // ── 수정 ──────────────────────────────────────────────

    @Test
    @DisplayName("OWNER는 랙 코드·표시순서를 200으로 수정할 수 있다")
    void updateRackSuccess() throws Exception {
        String token = signupAndLogin("농장주-랙수정");
        long farmId = createFarm(token, "랙수정 농장");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 3);

        mockMvc.perform(patch("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackUpdateRequest("R1-수정", null, 9))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("R1-수정"))
                .andExpect(jsonPath("$.displayOrder").value(9))
                .andExpect(jsonPath("$.levelCount").value(3));
    }

    @Test
    @DisplayName("같은 존 내 다른 랙과 코드가 중복되면 409 R004를 반환한다")
    void updateRackDuplicateCodeConflict() throws Exception {
        String token = signupAndLogin("농장주-랙수정중복");
        long farmId = createFarm(token, "랙수정중복 농장");
        long zoneId = createZone(token, farmId, "A동");
        createRack(token, farmId, zoneId, "R1", 1);
        long rackId2 = createRack(token, farmId, zoneId, "R2", 1);

        mockMvc.perform(patch("/api/farms/" + farmId + "/racks/" + rackId2)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackUpdateRequest("R1", null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R004"));
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 rackId로 수정 시 404 R002를 반환한다(농장 스코프)")
    void updateRackCrossTenantRackId() throws Exception {
        String ownerAToken = signupAndLogin("농장주A-랙");
        String ownerBToken = signupAndLogin("농장주B-랙");
        long farmA = createFarm(ownerAToken, "랙 농장 A");
        long farmB = createFarm(ownerBToken, "랙 농장 B");
        long zoneIdA = createZone(ownerAToken, farmA, "A동");
        long rackId = createRack(ownerAToken, farmA, zoneIdA, "R1", 2);

        mockMvc.perform(patch("/api/farms/" + farmB + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + ownerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackUpdateRequest("탈취", null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R002"));
    }

    // ── levelCount 변경 ────────────────────────────────────

    @Test
    @DisplayName("levelCount 확대 시 새 층이 추가 생성된다")
    void increaseLevelCountAddsLevels() throws Exception {
        String token = signupAndLogin("농장주-층확대");
        long farmId = createFarm(token, "층확대 농장");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 2);

        mockMvc.perform(patch("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackUpdateRequest(null, 4, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.levelCount").value(4));

        mockMvc.perform(get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones[0].racks[0].levels.length()").value(4));
    }

    @Test
    @DisplayName("levelCount 축소 시 장비가 없으면 층이 soft delete된다")
    void decreaseLevelCountWithoutDevicesSucceeds() throws Exception {
        String token = signupAndLogin("농장주-층축소");
        long farmId = createFarm(token, "층축소 농장");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 5);

        mockMvc.perform(patch("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackUpdateRequest(null, 2, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.levelCount").value(2));

        mockMvc.perform(get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones[0].racks[0].levels.length()").value(2));
    }

    @Test
    @DisplayName("levelCount 축소 시 잘려나가는 층에 장비가 매달려 있으면 409 R004를 반환한다")
    void decreaseLevelCountWithAttachedDeviceConflict() throws Exception {
        String token = signupAndLogin("농장주-층축소거부");
        long farmId = createFarm(token, "층축소거부 농장");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 5);
        long rackLevelId = findLevelId(token, farmId, "R1", 5); // 잘려나갈 5번째 층

        mockMvc.perform(post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                null, null, rackLevelId, "온습도센서", DeviceKind.SENSOR,
                                null, null, null, null, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackUpdateRequest(null, 3, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R004"));

        // 거부 후에도 층수는 그대로 유지되어야 한다(부분 반영 금지)
        mockMvc.perform(get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones[0].racks[0].levelCount").value(5))
                .andExpect(jsonPath("$.zones[0].racks[0].levels.length()").value(5));
    }

    // ── 삭제(캐스케이드) ────────────────────────────────────

    @Test
    @DisplayName("랙 삭제 시 하위 층까지 함께 soft delete된다")
    void deleteRackCascadesLevels() throws Exception {
        String token = signupAndLogin("농장주-랙삭제");
        long farmId = createFarm(token, "랙삭제 농장");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 2);

        mockMvc.perform(delete("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones[0].racks.length()").value(0));
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 rackId로 삭제 시 404 R002를 반환한다")
    void deleteRackCrossTenant() throws Exception {
        String ownerAToken = signupAndLogin("농장주A-랙삭제");
        String ownerBToken = signupAndLogin("농장주B-랙삭제");
        long farmA = createFarm(ownerAToken, "랙삭제 농장 A");
        long farmB = createFarm(ownerBToken, "랙삭제 농장 B");
        long zoneIdA = createZone(ownerAToken, farmA, "A동");
        long rackId = createRack(ownerAToken, farmA, zoneIdA, "R1", 1);

        mockMvc.perform(delete("/api/farms/" + farmB + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + ownerBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R002"));
    }

    // ── 삭제 시 하위 활성 장비 잔존 거부 (리뷰 P1 #89 — 계약 초판 결함 보정) ────────

    @Test
    @DisplayName("랙 직속(층 아님) 장비가 있으면 랙 삭제가 409 R004로 거부된다")
    void deleteRackWithDeviceDirectlyOnRackConflict() throws Exception {
        String token = signupAndLogin("농장주-랙삭제거부직속");
        long farmId = createFarm(token, "랙삭제거부직속 농장");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 2);
        createDevice(token, farmId, null, rackId, null, "랙직속제어기");

        mockMvc.perform(delete("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R004"));

        // 거부 후에도 랙이 트리에 그대로 살아있어야 한다(부분 반영 금지)
        mockMvc.perform(get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones[0].racks.length()").value(1));
    }

    @Test
    @DisplayName("랙의 층에 매달린 장비가 있으면 랙 삭제가 409 R004로 거부된다")
    void deleteRackWithDeviceOnLevelConflict() throws Exception {
        String token = signupAndLogin("농장주-랙삭제거부층");
        long farmId = createFarm(token, "랙삭제거부층 농장");
        long zoneId = createZone(token, farmId, "A동");
        long rackId = createRack(token, farmId, zoneId, "R1", 2);
        long levelId = findLevelId(token, farmId, "R1", 1);
        createDevice(token, farmId, null, null, levelId, "층장비");

        mockMvc.perform(delete("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R004"));
    }

    // ── 인가(비멤버·일반 멤버·데모) — 리뷰 P2-4 #89: 생성·수정·삭제 전부 커버 ──────

    @Test
    @DisplayName("cross-tenant: 미멤버가 랙 생성 시 403 F002를 반환한다")
    void createRackAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("농장주-랙생성F002");
        String otherToken = signupAndLogin("남남-랙생성F002");
        long farmId = createFarm(ownerToken, "랙생성F002 농장");
        long zoneId = createZone(ownerToken, farmId, "A동");

        mockMvc.perform(post("/api/farms/" + farmId + "/zones/" + zoneId + "/racks")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackRequest(
                                "R1", 1, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("일반 멤버(OWNER 아님)는 랙 생성 시 403 F003을 반환한다")
    void createRackAsMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("농장주-랙생성F003");
        String memberToken = signupAndLogin("멤버-랙생성F003");
        long farmId = createFarm(ownerToken, "랙생성F003 농장");
        long zoneId = createZone(ownerToken, farmId, "A동");
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));

        mockMvc.perform(post("/api/farms/" + farmId + "/zones/" + zoneId + "/racks")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackRequest(
                                "R1", 1, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    @Test
    @DisplayName("데모 계정은 랙 생성 시 403 A007을 반환한다(파괴적 작업 차단)")
    void createRackAsDemoAccountForbidden() throws Exception {
        String demoToken = demoAccountLogin();
        long demoFarmId = demoAccountFarmId(demoToken);

        mockMvc.perform(post("/api/farms/" + demoFarmId + "/zones/999999999/racks")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackRequest(
                                "데모랙", 1, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 랙 수정 시 403 F002를 반환한다")
    void updateRackAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("농장주-랙수정F002");
        String otherToken = signupAndLogin("남남-랙수정F002");
        long farmId = createFarm(ownerToken, "랙수정F002 농장");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long rackId = createRack(ownerToken, farmId, zoneId, "R1", 1);

        mockMvc.perform(patch("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackUpdateRequest("침입", null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("일반 멤버(OWNER 아님)는 랙 수정 시 403 F003을 반환한다")
    void updateRackAsMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("농장주-랙수정F003");
        String memberToken = signupAndLogin("멤버-랙수정F003");
        long farmId = createFarm(ownerToken, "랙수정F003 농장");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long rackId = createRack(ownerToken, farmId, zoneId, "R1", 1);
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));

        mockMvc.perform(patch("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackUpdateRequest("침입", null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    @Test
    @DisplayName("데모 계정은 랙 수정 시 403 A007을 반환한다(파괴적 작업 차단)")
    void updateRackAsDemoAccountForbidden() throws Exception {
        String demoToken = demoAccountLogin();
        long demoFarmId = demoAccountFarmId(demoToken);

        mockMvc.perform(patch("/api/farms/" + demoFarmId + "/racks/999999999")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RackUpdateRequest("침입", null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 랙 삭제 시 403 F002를 반환한다")
    void deleteRackAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("농장주-랙삭제F002");
        String otherToken = signupAndLogin("남남-랙삭제F002");
        long farmId = createFarm(ownerToken, "랙삭제F002 농장");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long rackId = createRack(ownerToken, farmId, zoneId, "R1", 1);

        mockMvc.perform(delete("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("일반 멤버(OWNER 아님)는 랙 삭제 시 403 F003을 반환한다")
    void deleteRackAsMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("농장주-랙삭제F003");
        String memberToken = signupAndLogin("멤버-랙삭제F003");
        long farmId = createFarm(ownerToken, "랙삭제F003 농장");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long rackId = createRack(ownerToken, farmId, zoneId, "R1", 1);
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));

        mockMvc.perform(delete("/api/farms/" + farmId + "/racks/" + rackId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    @Test
    @DisplayName("데모 계정은 랙 삭제 시 403 A007을 반환한다(파괴적 작업 차단)")
    void deleteRackAsDemoAccountForbidden() throws Exception {
        String demoToken = demoAccountLogin();
        long demoFarmId = demoAccountFarmId(demoToken);

        mockMvc.perform(delete("/api/farms/" + demoFarmId + "/racks/999999999")
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"));
    }

    /** 트리 조회에서 rackCode·levelNo로 rackLevelId를 찾는다(장비 부착 테스트용). */
    private long findLevelId(String token, long farmId, String rackCode, int levelNo) throws Exception {
        var result = mockMvc.perform(get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        var zones = readJson(result).get("zones");
        for (var zone : zones) {
            for (var rack : zone.get("racks")) {
                if (rack.get("code").asText().equals(rackCode)) {
                    for (var level : rack.get("levels")) {
                        if (level.get("levelNo").asInt() == levelNo) {
                            return level.get("id").asLong();
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("층을 찾을 수 없음: " + rackCode + "/" + levelNo);
    }
}
