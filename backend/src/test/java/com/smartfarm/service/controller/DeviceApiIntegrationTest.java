package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.DeviceRequest;
import com.smartfarm.service.entity.DeviceKind;
import com.smartfarm.service.entity.DeviceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class DeviceApiIntegrationTest extends FarmTestSupport {

    // ── 생성 ──────────────────────────────────────────────

    @Test
    @DisplayName("OWNER는 존 단위 장비를 201로 등록할 수 있다")
    void createDeviceWithZoneLocationSuccess() throws Exception {
        String token = signupAndLogin("농장주-장비생성");
        long farmId = createFarm(token, "장비생성 농장");
        long zoneId = createZone(token, farmId, "A동");

        mockMvc.perform(post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneId, null, null, "게이트웨이1호", DeviceKind.GATEWAY,
                                "GW-100", "SN-0001", DeviceStatus.NORMAL, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.zoneId").value(zoneId))
                .andExpect(jsonPath("$.name").value("게이트웨이1호"))
                .andExpect(jsonPath("$.kind").value("GATEWAY"))
                .andExpect(jsonPath("$.status").value("NORMAL"));
    }

    @Test
    @DisplayName("위치(존·랙·층) 전부 null이면 400 C001을 반환한다")
    void createDeviceWithoutLocationFails() throws Exception {
        String token = signupAndLogin("농장주-장비위치없음");
        long farmId = createFarm(token, "위치없음 농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                null, null, null, "장비", DeviceKind.SENSOR,
                                null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("장비명 누락 시 400 C001을 반환한다")
    void createDeviceWithoutNameFails() throws Exception {
        String token = signupAndLogin("농장주-장비이름없음");
        long farmId = createFarm(token, "이름없음 농장");
        long zoneId = createZone(token, farmId, "A동");

        mockMvc.perform(post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneId, null, null, null, DeviceKind.SENSOR,
                                null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 zoneId 위치로 장비 생성 시 404 R001을 반환한다")
    void createDeviceCrossTenantZoneId() throws Exception {
        String ownerAToken = signupAndLogin("농장주A-장비위치");
        String ownerBToken = signupAndLogin("농장주B-장비위치");
        long farmA = createFarm(ownerAToken, "장비위치 농장 A");
        long farmB = createFarm(ownerBToken, "장비위치 농장 B");
        long zoneIdInFarmA = createZone(ownerAToken, farmA, "A동");

        mockMvc.perform(post("/api/farms/" + farmB + "/devices")
                        .header("Authorization", "Bearer " + ownerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneIdInFarmA, null, null, "탈취장비", DeviceKind.SENSOR,
                                null, null, null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 rackId 위치로 장비 생성 시 404 R002를 반환한다")
    void createDeviceCrossTenantRackId() throws Exception {
        String ownerAToken = signupAndLogin("농장주A-장비랙위치");
        String ownerBToken = signupAndLogin("농장주B-장비랙위치");
        long farmA = createFarm(ownerAToken, "장비랙위치 농장 A");
        long farmB = createFarm(ownerBToken, "장비랙위치 농장 B");
        long zoneIdA = createZone(ownerAToken, farmA, "A동");
        long rackIdInFarmA = createRack(ownerAToken, farmA, zoneIdA, "R1", 1);

        mockMvc.perform(post("/api/farms/" + farmB + "/devices")
                        .header("Authorization", "Bearer " + ownerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                null, rackIdInFarmA, null, "탈취장비", DeviceKind.SENSOR,
                                null, null, null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R002"));
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 rackLevelId 위치로 장비 생성 시 404 R003을 반환한다")
    void createDeviceCrossTenantRackLevelId() throws Exception {
        String ownerAToken = signupAndLogin("농장주A-장비층위치");
        String ownerBToken = signupAndLogin("농장주B-장비층위치");
        long farmA = createFarm(ownerAToken, "장비층위치 농장 A");
        long farmB = createFarm(ownerBToken, "장비층위치 농장 B");
        long zoneIdA = createZone(ownerAToken, farmA, "A동");
        long rackIdA = createRack(ownerAToken, farmA, zoneIdA, "R1", 1);
        long rackLevelIdInFarmA = firstLevelId(ownerAToken, farmA);

        mockMvc.perform(post("/api/farms/" + farmB + "/devices")
                        .header("Authorization", "Bearer " + ownerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                null, null, rackLevelIdInFarmA, "탈취장비", DeviceKind.SENSOR,
                                null, null, null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R003"));
    }

    @Test
    @DisplayName("위치 FK 계층 불일치(rack이 지정한 zone 소속 아님) 시 400 C001을 반환한다(리뷰 P2-3 #89)")
    void createDeviceLocationZoneRackMismatchFails() throws Exception {
        String token = signupAndLogin("농장주-장비위치불일치1");
        long farmId = createFarm(token, "위치불일치1 농장");
        long zoneA = createZone(token, farmId, "A동");
        long zoneB = createZone(token, farmId, "B동");
        long rackInZoneB = createRack(token, farmId, zoneB, "R1", 1);

        mockMvc.perform(post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneA, rackInZoneB, null, "모순장비", DeviceKind.SENSOR,
                                null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("위치 FK 계층 불일치(level이 지정한 rack 소속 아님) 시 400 C001을 반환한다(리뷰 P2-3 #89)")
    void createDeviceLocationRackLevelMismatchFails() throws Exception {
        String token = signupAndLogin("농장주-장비위치불일치2");
        long farmId = createFarm(token, "위치불일치2 농장");
        long zoneId = createZone(token, farmId, "A동");
        long rackA = createRack(token, farmId, zoneId, "R1", 1);
        createRack(token, farmId, zoneId, "R2", 1);
        long levelInRackB = findLevelId(token, farmId, "R2", 1);

        mockMvc.perform(post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                null, rackA, levelInRackB, "모순장비", DeviceKind.SENSOR,
                                null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("위치 FK 전이 불일치(rackId 생략, level→rack→zone이 지정한 zoneId와 다름) 시 400 C001(2차 리뷰 P1 #89)")
    void createDeviceLocationTransitiveMismatchFails() throws Exception {
        String token = signupAndLogin("농장주-장비위치전이불일치");
        long farmId = createFarm(token, "위치전이불일치 농장");
        long zoneA = createZone(token, farmId, "A동");
        long zoneB = createZone(token, farmId, "B동");
        createRack(token, farmId, zoneB, "R1", 1);
        long levelInRackOfZoneB = findLevelId(token, farmId, "R1", 1);

        // rackId를 생략(null)하면 쌍 검사(①②) 둘 다 skip된다 — level -> rack -> zone 전이 검사만이 잡는다.
        mockMvc.perform(post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneA, null, levelInRackOfZoneB, "모순장비", DeviceKind.SENSOR,
                                null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("PATCH로 rackLevelId만 바꿔도 병합된 최종 상태의 전이 불일치면 400 C001(2차 리뷰 P1 #89)")
    void updateDeviceLocationTransitiveMismatchFails() throws Exception {
        String token = signupAndLogin("농장주-장비수정전이불일치");
        long farmId = createFarm(token, "장비수정전이불일치 농장");
        long zoneA = createZone(token, farmId, "A동");
        long zoneB = createZone(token, farmId, "B동");
        createRack(token, farmId, zoneB, "R1", 1);
        long levelInRackOfZoneB = findLevelId(token, farmId, "R1", 1);
        // 존 직속(zoneId만) 게이트웨이 — rackId·rackLevelId 둘 다 null인 기존 장비
        long deviceId = createDevice(token, farmId, zoneA, null, null, "존직속게이트웨이");

        // rackLevelId만 바꾼다 — 기존 zoneId(A동)는 그대로라 병합된 최종 상태가 전이로 어긋난다
        mockMvc.perform(patch("/api/farms/" + farmId + "/devices/" + deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                null, null, levelInRackOfZoneB, null, null, null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("같은 농장 내 시리얼 중복 시 409 E002를 반환한다")
    void createDeviceDuplicateSerialConflict() throws Exception {
        String token = signupAndLogin("농장주-시리얼중복");
        long farmId = createFarm(token, "시리얼중복 농장");
        long zoneId = createZone(token, farmId, "A동");
        createDevice(token, farmId, zoneId, "센서1", DeviceKind.SENSOR, "SN-DUP", DeviceStatus.NORMAL);

        mockMvc.perform(post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneId, null, null, "센서2", DeviceKind.SENSOR,
                                null, "SN-DUP", null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E002"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 장비 등록 시 403 F002를 반환한다")
    void createDeviceAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("농장주-장비격리");
        String otherToken = signupAndLogin("남남-장비격리");
        long farmId = createFarm(ownerToken, "장비격리 농장");
        long zoneId = createZone(ownerToken, farmId, "A동");

        mockMvc.perform(post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneId, null, null, "침입장비", DeviceKind.SENSOR,
                                null, null, null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("일반 멤버(OWNER 아님)는 장비 등록 시 403 F003을 반환한다(2차 리뷰 P3 #89)")
    void createDeviceAsMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("농장주-장비등록F003");
        String memberToken = signupAndLogin("멤버-장비등록F003");
        long farmId = createFarm(ownerToken, "장비등록F003 농장");
        long zoneId = createZone(ownerToken, farmId, "A동");
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));

        mockMvc.perform(post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneId, null, null, "침입장비", DeviceKind.SENSOR,
                                null, null, null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    // ── 목록 조회(필터) ────────────────────────────────────

    @Test
    @DisplayName("kind·status·q·zoneId 필터로 장비 목록을 조회할 수 있다")
    void listDevicesWithFilters() throws Exception {
        String token = signupAndLogin("농장주-장비목록");
        long farmId = createFarm(token, "장비목록 농장");
        long zoneA = createZone(token, farmId, "A동");
        long zoneB = createZone(token, farmId, "B동");
        createDevice(token, farmId, zoneA, "온습도센서", DeviceKind.SENSOR, "SN-1", DeviceStatus.NORMAL);
        createDevice(token, farmId, zoneA, "CO2센서", DeviceKind.SENSOR, "SN-2", DeviceStatus.WARNING);
        createDevice(token, farmId, zoneB, "제어기1", DeviceKind.CONTROLLER, "SN-3", DeviceStatus.NORMAL);

        mockMvc.perform(get("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices.length()").value(3));

        mockMvc.perform(get("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .param("kind", "SENSOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices.length()").value(2));

        mockMvc.perform(get("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "WARNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices.length()").value(1))
                .andExpect(jsonPath("$.devices[0].name").value("CO2센서"));

        mockMvc.perform(get("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .param("zoneId", String.valueOf(zoneB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices.length()").value(1))
                .andExpect(jsonPath("$.devices[0].name").value("제어기1"));

        mockMvc.perform(get("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "온습도"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices.length()").value(1))
                .andExpect(jsonPath("$.devices[0].name").value("온습도센서"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 장비 목록 조회 시 403 F002를 반환한다")
    void listDevicesAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("농장주-장비목록격리");
        String otherToken = signupAndLogin("남남-장비목록격리");
        long farmId = createFarm(ownerToken, "장비목록격리 농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    // ── KPI 요약 ───────────────────────────────────────────

    @Test
    @DisplayName("KPI 요약이 총합·상태별 개수를 정확히 계산한다")
    void summaryCalculatesCounts() throws Exception {
        String token = signupAndLogin("농장주-요약");
        long farmId = createFarm(token, "요약 농장");
        long zoneId = createZone(token, farmId, "A동");
        createDevice(token, farmId, zoneId, "센서1", DeviceKind.SENSOR, "SN-S1", DeviceStatus.NORMAL);
        createDevice(token, farmId, zoneId, "센서2", DeviceKind.SENSOR, "SN-S2", DeviceStatus.NORMAL);
        createDevice(token, farmId, zoneId, "센서3", DeviceKind.SENSOR, "SN-S3", DeviceStatus.WARNING);
        createDevice(token, farmId, zoneId, "센서4", DeviceKind.SENSOR, "SN-S4", DeviceStatus.FAULT);
        createDevice(token, farmId, zoneId, "센서5", DeviceKind.SENSOR, "SN-S5", DeviceStatus.OFFLINE);

        mockMvc.perform(get("/api/farms/" + farmId + "/devices/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.normal").value(2))
                .andExpect(jsonPath("$.warning").value(1))
                .andExpect(jsonPath("$.faultOrOffline").value(2))
                .andExpect(jsonPath("$.calibrationDueSoon").value(0));
    }

    // ── 수정 ──────────────────────────────────────────────

    @Test
    @DisplayName("OWNER는 장비를 부분 수정할 수 있고, null 필드는 미변경이다")
    void updateDevicePartialSuccess() throws Exception {
        String token = signupAndLogin("농장주-장비수정");
        long farmId = createFarm(token, "장비수정 농장");
        long zoneId = createZone(token, farmId, "A동");
        long deviceId = createDevice(token, farmId, zoneId, "센서", DeviceKind.SENSOR, "SN-U1", DeviceStatus.NORMAL);

        mockMvc.perform(patch("/api/farms/" + farmId + "/devices/" + deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                null, null, null, null, null, null, null, DeviceStatus.WARNING, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("센서")) // 미변경
                .andExpect(jsonPath("$.serial").value("SN-U1")) // 미변경
                .andExpect(jsonPath("$.status").value("WARNING")); // 변경됨
    }

    @Test
    @DisplayName("수정 시 다른 장비와 시리얼이 중복되면 409 E002를 반환한다")
    void updateDeviceDuplicateSerialConflict() throws Exception {
        String token = signupAndLogin("농장주-장비수정중복");
        long farmId = createFarm(token, "장비수정중복 농장");
        long zoneId = createZone(token, farmId, "A동");
        createDevice(token, farmId, zoneId, "센서1", DeviceKind.SENSOR, "SN-A", DeviceStatus.NORMAL);
        long deviceId2 = createDevice(token, farmId, zoneId, "센서2", DeviceKind.SENSOR, "SN-B", DeviceStatus.NORMAL);

        mockMvc.perform(patch("/api/farms/" + farmId + "/devices/" + deviceId2)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                null, null, null, null, null, null, "SN-A", null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E002"));
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 deviceId로 수정 시 404 E001을 반환한다(농장 스코프)")
    void updateDeviceCrossTenant() throws Exception {
        String ownerAToken = signupAndLogin("농장주A-장비수정");
        String ownerBToken = signupAndLogin("농장주B-장비수정");
        long farmA = createFarm(ownerAToken, "장비수정 농장 A");
        long farmB = createFarm(ownerBToken, "장비수정 농장 B");
        long zoneIdA = createZone(ownerAToken, farmA, "A동");
        long deviceId = createDevice(ownerAToken, farmA, zoneIdA, "센서", DeviceKind.SENSOR, "SN-X", DeviceStatus.NORMAL);

        mockMvc.perform(patch("/api/farms/" + farmB + "/devices/" + deviceId)
                        .header("Authorization", "Bearer " + ownerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                null, null, null, "탈취", null, null, null, null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("E001"));
    }

    @Test
    @DisplayName("일반 멤버(OWNER 아님)는 장비 수정 시 403 F003을 반환한다(2차 리뷰 P3 #89)")
    void updateDeviceAsMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("농장주-장비수정F003");
        String memberToken = signupAndLogin("멤버-장비수정F003");
        long farmId = createFarm(ownerToken, "장비수정F003 농장");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long deviceId = createDevice(ownerToken, farmId, zoneId, "센서", DeviceKind.SENSOR, "SN-F003", DeviceStatus.NORMAL);
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));

        mockMvc.perform(patch("/api/farms/" + farmId + "/devices/" + deviceId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                null, null, null, "침입", null, null, null, null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    @Test
    @DisplayName("데모 계정은 장비 수정 시 403 A007을 반환한다(파괴적 작업 차단, 2차 리뷰 P3 #89)")
    void updateDeviceAsDemoAccountForbidden() throws Exception {
        String demoToken = demoAccountLogin();
        long demoFarmId = demoAccountFarmId(demoToken);

        mockMvc.perform(patch("/api/farms/" + demoFarmId + "/devices/999999999")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                null, null, null, "침입", null, null, null, null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"));
    }

    @Test
    @DisplayName("PATCH로 위치 FK 한 필드만 바꿔도 병합된 최종 상태 기준 계층 불일치면 400 C001(리뷰 P2-3 #89 — 요청값만 보면 통과해버리는 우회 케이스)")
    void updateDeviceLocationHierarchyMismatchFails() throws Exception {
        String token = signupAndLogin("농장주-장비수정위치불일치");
        long farmId = createFarm(token, "장비수정위치불일치 농장");
        long zoneId = createZone(token, farmId, "A동");
        createRack(token, farmId, zoneId, "R1", 1);
        long rackB = createRack(token, farmId, zoneId, "R2", 1);
        long levelInRackA = findLevelId(token, farmId, "R1", 1);
        long deviceId = createDevice(token, farmId, null, null, levelInRackA, "센서");

        // rackId만 rackB로 바꾼다 — 기존 rackLevelId(rackA 소속)는 그대로라 병합된 최종 상태가 어긋난다
        mockMvc.perform(patch("/api/farms/" + farmId + "/devices/" + deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                null, rackB, null, null, null, null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("cross-tenant: PATCH body의 zoneId가 타 농장 존이면 404 R001을 반환한다(리뷰 P2-5 #89)")
    void updateDeviceCrossTenantLocationInBody() throws Exception {
        String ownerAToken = signupAndLogin("농장주A-장비수정위치");
        String ownerBToken = signupAndLogin("농장주B-장비수정위치");
        long farmA = createFarm(ownerAToken, "장비수정위치 농장 A");
        long farmB = createFarm(ownerBToken, "장비수정위치 농장 B");
        long zoneIdA = createZone(ownerAToken, farmA, "A동");
        long zoneIdBInFarmB = createZone(ownerBToken, farmB, "B동");
        long deviceId = createDevice(ownerAToken, farmA, zoneIdA, "센서", DeviceKind.SENSOR, "SN-P25",
                DeviceStatus.NORMAL);

        mockMvc.perform(patch("/api/farms/" + farmA + "/devices/" + deviceId)
                        .header("Authorization", "Bearer " + ownerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneIdBInFarmB, null, null, null, null, null, null, null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    @Test
    @DisplayName("데모 계정은 장비 등록 시 403 A007을 반환한다(파괴적 작업 차단 — 데모는 존 생성도 막혀 있어 등록 자체가 항상 차단된다)")
    void createDeviceAsDemoAccountForbidden() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/demo-login"))
                .andExpect(status().isOk())
                .andReturn();
        String demoToken = readJson(loginResult).get("accessToken").asText();
        MvcResult farmsResult = mockMvc.perform(get("/api/farms")
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isOk())
                .andReturn();
        long demoFarmId = readJson(farmsResult).get(0).get("id").asLong();

        mockMvc.perform(post("/api/farms/" + demoFarmId + "/devices")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                1L, null, null, "데모장비", DeviceKind.SENSOR, null, null, null, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"));
    }

    // ── 삭제 ──────────────────────────────────────────────

    @Test
    @DisplayName("OWNER는 장비를 204로 삭제할 수 있고, 이후 목록에서 사라진다")
    void deleteDeviceSuccess() throws Exception {
        String token = signupAndLogin("농장주-장비삭제");
        long farmId = createFarm(token, "장비삭제 농장");
        long zoneId = createZone(token, farmId, "A동");
        long deviceId = createDevice(token, farmId, zoneId, "센서", DeviceKind.SENSOR, "SN-D1", DeviceStatus.NORMAL);

        mockMvc.perform(delete("/api/farms/" + farmId + "/devices/" + deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices.length()").value(0));
    }

    @Test
    @DisplayName("PATCH name이 공백만이면(trim 후 빈 문자열) 400 C001을 반환한다(리뷰 P3-1 #89)")
    void updateDeviceBlankNameFails() throws Exception {
        String token = signupAndLogin("농장주-장비공백이름");
        long farmId = createFarm(token, "장비공백이름 농장");
        long zoneId = createZone(token, farmId, "A동");
        long deviceId = createDevice(token, farmId, zoneId, "센서", DeviceKind.SENSOR, "SN-BLANK", DeviceStatus.NORMAL);

        mockMvc.perform(patch("/api/farms/" + farmId + "/devices/" + deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("q가 50자를 초과하면 400 C001을 반환한다(리뷰 P3-3 #89)")
    void listDevicesQueryTooLongFails() throws Exception {
        String token = signupAndLogin("농장주-검색어초과");
        long farmId = createFarm(token, "검색어초과 농장");
        String tooLong = "a".repeat(51);

        mockMvc.perform(get("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .param("q", tooLong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("존재하지 않는 deviceId 삭제 시 404 E001을 반환한다")
    void deleteDeviceNotFound() throws Exception {
        String token = signupAndLogin("농장주-장비삭제미존재");
        long farmId = createFarm(token, "장비삭제미존재 농장");

        mockMvc.perform(delete("/api/farms/" + farmId + "/devices/999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("E001"));
    }

    @Test
    @DisplayName("일반 멤버(OWNER 아님)는 장비 삭제 시 403 F003을 반환한다(2차 리뷰 P3 #89)")
    void deleteDeviceAsMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("농장주-장비삭제F003");
        String memberToken = signupAndLogin("멤버-장비삭제F003");
        long farmId = createFarm(ownerToken, "장비삭제F003 농장");
        long zoneId = createZone(ownerToken, farmId, "A동");
        long deviceId = createDevice(ownerToken, farmId, zoneId, "센서", DeviceKind.SENSOR, "SN-DF003", DeviceStatus.NORMAL);
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));

        mockMvc.perform(delete("/api/farms/" + farmId + "/devices/" + deviceId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    @Test
    @DisplayName("데모 계정은 장비 삭제 시 403 A007을 반환한다(파괴적 작업 차단, 2차 리뷰 P3 #89)")
    void deleteDeviceAsDemoAccountForbidden() throws Exception {
        String demoToken = demoAccountLogin();
        long demoFarmId = demoAccountFarmId(demoToken);

        mockMvc.perform(delete("/api/farms/" + demoFarmId + "/devices/999999999")
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A007"));
    }

    private long createDevice(String token, long farmId, long zoneId, String name, DeviceKind kind, String serial,
                               DeviceStatus status) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneId, null, null, name, kind, null, serial, status, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private long firstLevelId(String token, long farmId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("zones").get(0).get("racks").get(0).get("levels").get(0).get("id").asLong();
    }

    /** 트리 조회에서 rackCode·levelNo로 rackLevelId를 찾는다(RackApiIntegrationTest와 동일 패턴). */
    private long findLevelId(String token, long farmId, String rackCode, int levelNo) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        for (var zone : readJson(result).get("zones")) {
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
