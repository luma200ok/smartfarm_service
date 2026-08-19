package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.FarmRequest;
import com.smartfarm.service.dto.FarmUpdateRequest;
import com.smartfarm.service.entity.CropType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class FarmApiIntegrationTest extends FarmTestSupport {

    // ── 생성 ──────────────────────────────────────────────

    @Test
    @DisplayName("농장 생성 시 201, 생성자는 OWNER로 자동 등록되고 memberCount=1이다")
    void createFarmSuccess() throws Exception {
        String token = signupAndLogin("농장주");

        mockMvc.perform(post("/api/farms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmRequest("토마토 농장", CropType.TOMATO, "경기 화성"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("토마토 농장"))
                .andExpect(jsonPath("$.cropType").value("TOMATO"))
                .andExpect(jsonPath("$.location").value("경기 화성"))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("농장 이름이 1자면 400 C001을 반환한다")
    void createFarmNameTooShort() throws Exception {
        String token = signupAndLogin("농장주");

        mockMvc.perform(post("/api/farms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmRequest("농", CropType.TOMATO, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("정의되지 않은 cropType이면 400 C001을 반환한다")
    void createFarmInvalidCropType() throws Exception {
        String token = signupAndLogin("농장주");

        mockMvc.perform(post("/api/farms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"사과 농장\",\"cropType\":\"APPLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("무인증으로 농장 생성 시 401을 반환한다")
    void createFarmWithoutToken() throws Exception {
        mockMvc.perform(post("/api/farms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmRequest("무인증 농장", CropType.TOMATO, null))))
                .andExpect(status().isUnauthorized());
    }

    // ── 목록 ──────────────────────────────────────────────

    @Test
    @DisplayName("내 농장 목록은 내가 속한 농장만 반환한다 (타 유저 농장 제외)")
    void findMyFarmsReturnsOnlyMine() throws Exception {
        String tokenA = signupAndLogin("갑농부");
        String tokenB = signupAndLogin("을농부");
        long farmA1 = createFarm(tokenA, "갑 농장 하나");
        long farmA2 = createFarm(tokenA, "갑 농장 둘");
        createFarm(tokenB, "을 농장");

        mockMvc.perform(get("/api/farms")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(farmA1))
                .andExpect(jsonPath("$[0].myRole").value("OWNER"))
                .andExpect(jsonPath("$[1].id").value(farmA2));
    }

    // ── 상세 (테넌트 가드) ────────────────────────────────

    @Test
    @DisplayName("멤버는 농장 상세를 조회할 수 있고 myRole=MEMBER, memberCount=2다")
    void findFarmAsMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String memberToken = signupAndLogin("일꾼이");
        long farmId = createFarm(ownerToken, "합류 농장");
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));

        mockMvc.perform(get("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(farmId))
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 타 농장 상세 조회 시 403 F002를 반환한다")
    void findFarmAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 농장");

        mockMvc.perform(get("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("존재하지 않는 farmId도 403 F002로 통일한다 (농장 존재 유추 채널 차단)")
    void findFarmNotExistsReturnsF002() throws Exception {
        String token = signupAndLogin("탐색꾼");

        mockMvc.perform(get("/api/farms/999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    // ── 수정 ──────────────────────────────────────────────

    @Test
    @DisplayName("OWNER는 부분 수정할 수 있고 미전달 필드는 유지된다")
    void updateFarmPartial() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmId = createFarm(ownerToken, "수정 전 농장");

        mockMvc.perform(patch("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmUpdateRequest("수정 후 농장", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("수정 후 농장"))
                .andExpect(jsonPath("$.cropType").value("TOMATO"))
                .andExpect(jsonPath("$.location").value("온실 1동"));
    }

    @Test
    @DisplayName("MEMBER가 농장 수정 시 403 F003을 반환한다")
    void updateFarmAsMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String memberToken = signupAndLogin("일꾼이");
        long farmId = createFarm(ownerToken, "권한 농장");
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));

        mockMvc.perform(patch("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmUpdateRequest("탈취 시도", null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 타 농장 수정 시 403 F002를 반환한다")
    void updateFarmAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 농장");

        mockMvc.perform(patch("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmUpdateRequest("침입 시도", null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("PATCH에 공백만인 이름이 오면 400 C001을 반환한다 (trim 후 검증)")
    void updateFarmBlankName() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmId = createFarm(ownerToken, "공백 검증 농장");

        mockMvc.perform(patch("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmUpdateRequest("   ", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("PATCH에 1자 이름이 오면 400 C001을 반환한다")
    void updateFarmNameTooShort() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        long farmId = createFarm(ownerToken, "검증 농장");

        mockMvc.perform(patch("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FarmUpdateRequest("농", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    // ── 삭제 (soft delete) ────────────────────────────────

    @Test
    @DisplayName("MEMBER가 농장 삭제 시 403 F003, 미멤버는 403 F002를 반환한다")
    void deleteFarmForbidden() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String memberToken = signupAndLogin("일꾼이");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "삭제 방어 농장");
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));

        mockMvc.perform(delete("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F003"));

        mockMvc.perform(delete("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("OWNER 삭제 시 204, 이후 멤버 접근은 404 F001이고 목록에서도 제외된다")
    void deleteFarmSoftDelete() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String memberToken = signupAndLogin("일꾼이");
        long farmId = createFarm(ownerToken, "폐업 농장");
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));

        mockMvc.perform(delete("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // soft delete 농장 접근 — 멤버십은 남아 있으므로 F001 (전 멤버에게만 노출)
        mockMvc.perform(get("/api/farms/" + farmId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("F001"));

        // 내 농장 목록에서도 제외 (@SQLRestriction join 검증)
        mockMvc.perform(get("/api/farms")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
