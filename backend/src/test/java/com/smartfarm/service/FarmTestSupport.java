package com.smartfarm.service;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.service.dto.AcceptInvitationRequest;
import com.smartfarm.service.dto.DeviceRequest;
import com.smartfarm.service.dto.FarmRequest;
import com.smartfarm.service.dto.LoginRequest;
import com.smartfarm.service.dto.RackRequest;
import com.smartfarm.service.dto.SignupRequest;
import com.smartfarm.service.dto.ZoneRequest;
import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.DeviceKind;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Farm 도메인 통합 테스트 공통 헬퍼 — 전부 공개 API 경유로 시나리오를 구성한다.
 * (싱글턴 컨테이너를 전 테스트가 공유하므로 이메일은 실행별 유니크)
 */
public abstract class FarmTestSupport extends IntegrationTestSupport {

    protected String signupAndLogin(String nickname) throws Exception {
        String email = "farm-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(email, "password123", nickname))))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("accessToken").asText();
    }

    protected long createFarm(String accessToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/farms")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmRequest(name, CropType.TOMATO, "온실 1동"))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    protected String createInvitationCode(String ownerToken, long farmId) throws Exception {
        MvcResult result = mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/farms/" + farmId + "/invitations")
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("code").asText();
    }

    protected void acceptInvitation(String accessToken, String code) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/invitations/accept")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcceptInvitationRequest(code))))
                .andExpect(status().isOk());
    }

    /** 멤버 목록에서 닉네임으로 memberId(멤버십 id)를 찾는다. */
    protected long memberIdOf(String accessToken, long farmId, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(
                        MockMvcRequestBuilders.get("/api/farms/" + farmId + "/members")
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode member : readJson(result)) {
            if (nickname.equals(member.get("nickname").asText())) {
                return member.get("memberId").asLong();
            }
        }
        throw new IllegalStateException("멤버를 찾을 수 없음: " + nickname);
    }

    /** 내 userId 조회(/api/users/me) — 엔티티를 repository로 직접 구성하는 테스트의 FK용. */
    protected long myUserId(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    /** 존 생성(contract §4.10) — 랙·장비 테스트의 공통 픽스처. */
    protected long createZone(String ownerToken, long farmId, String name) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/farms/" + farmId + "/zones")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ZoneRequest(name, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    /** 랙 생성(존 하위, contract §4.10) — 장비 테스트의 공통 픽스처. */
    protected long createRack(String ownerToken, long farmId, long zoneId, String code, int levelCount)
            throws Exception {
        MvcResult result = mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/farms/" + farmId + "/zones/" + zoneId + "/racks")
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new RackRequest(code, levelCount, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    /** 존·랙·층 중 지정한 위치에 장비를 등록한다(contract §4.10) — R004 삭제 거부 등 공통 픽스처. */
    protected long createDevice(String ownerToken, long farmId, Long zoneId, Long rackId, Long rackLevelId,
                                 String name) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/farms/" + farmId + "/devices")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceRequest(
                                zoneId, rackId, rackLevelId, name, DeviceKind.SENSOR,
                                null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    /** 데모 계정 로그인(contract §4.5, 이슈 #49) — access token 반환. */
    protected String demoAccountLogin() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/demo-login"))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("accessToken").asText();
    }

    /** 데모 계정이 이미 보유한 시드 농장 id(첫 번째) — 데모 계정은 존·랙 생성 자체가 막혀 있어
     * 이 픽스처 농장을 대상으로 파괴적 작업 차단(A007) 테스트를 수행한다. */
    protected long demoAccountFarmId(String demoToken) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/farms")
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get(0).get("id").asLong();
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
