package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.DeviceRequest;
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
