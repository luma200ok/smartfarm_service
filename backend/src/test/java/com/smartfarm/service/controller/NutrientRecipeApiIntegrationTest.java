package com.smartfarm.service.controller;

import com.smartfarm.service.entity.FarmRole;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.NutrientRecipeRequest;
import com.smartfarm.service.dto.NutrientSourceWaterRequest;
import com.smartfarm.service.dto.NutrientTargetRequest;
import com.smartfarm.service.entity.GrowthStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 양액 배합 계산·레시피 CRUD 통합 테스트(contract §4.9, 이슈 #64). 계산 로직 자체의 정확성은
 * {@code NutrientCalculationEngineTest}(단위 테스트)가 두껍게 검증하므로, 여기서는 HTTP 계층
 * (권한 매트릭스·N001~N003·데모 허용·페이지네이션)에 집중한다.
 */
class NutrientRecipeApiIntegrationTest extends FarmTestSupport {

    private static final NutrientTargetRequest SEEDLING_TARGET =
            new NutrientTargetRequest(90.0, 47.0, 144.0, 160.0, 60.0, 79.0);

    private static NutrientRecipeRequest request(String name) {
        return new NutrientRecipeRequest(name, GrowthStage.SEEDLING, SEEDLING_TARGET, 1.0, 1.0, null);
    }

    // ── calculate(미리보기, 저장 없음) ──────────────────────────────

    @Test
    @DisplayName("calculate는 저장 없이 200으로 계산 결과(tanks/estimatedEc/ionBalance/warnings)를 반환한다")
    void calculateReturnsResultWithoutPersisting() throws Exception {
        String token = signupAndLogin("양액농부");
        long farmId = createFarm(token, "계산 농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/nutrient-recipes/calculate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tanks.length()").value(2))
                .andExpect(jsonPath("$.estimatedEc").isNumber())
                .andExpect(jsonPath("$.ionBalance.cationMeL").isNumber())
                .andExpect(jsonPath("$.warnings").isArray());

        // 저장되지 않았는지 목록으로 확인.
        mockMvc.perform(get("/api/farms/" + farmId + "/nutrient-recipes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("calculate: 원수 Ca가 목표 Ca를 초과하면 400 N003을 반환한다")
    void calculateNegativeDosageReturnsN003() throws Exception {
        String token = signupAndLogin("양액농부-N003");
        long farmId = createFarm(token, "N003 농장");
        NutrientRecipeRequest invalid = new NutrientRecipeRequest(null, GrowthStage.SEEDLING, SEEDLING_TARGET, 1.0,
                1.0, new NutrientSourceWaterRequest(200.0, null, null));

        mockMvc.perform(post("/api/farms/" + farmId + "/nutrient-recipes/calculate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("N003"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Ca")));
    }

    @Test
    @DisplayName("calculate: target 누락 시 400 C001을 반환한다")
    void calculateValidationFailureReturnsC001() throws Exception {
        String token = signupAndLogin("양액농부-검증");
        long farmId = createFarm(token, "검증 농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/nutrient-recipes/calculate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"SEEDLING\",\"tankVolumeL\":1,\"concentrationFactor\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("calculate: cross-tenant 미멤버는 403 F002를 반환한다")
    void calculateAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("주인장-계산");
        String otherToken = signupAndLogin("남남남-계산");
        long farmId = createFarm(ownerToken, "격리 계산 농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/nutrient-recipes/calculate")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    // ── 생성 ──────────────────────────────────────────────

    @Test
    @DisplayName("정상 저장 시 201, 계산 결과가 동봉된다")
    void createRecipeSuccess() throws Exception {
        String token = signupAndLogin("레시피농부");
        long farmId = createFarm(token, "레시피 농장");
        long userId = myUserId(token);

        mockMvc.perform(post("/api/farms/" + farmId + "/nutrient-recipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("육묘기 표준"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("육묘기 표준"))
                .andExpect(jsonPath("$.stage").value("SEEDLING"))
                .andExpect(jsonPath("$.target.ca").value(160.0))
                .andExpect(jsonPath("$.calculation.tanks.length()").value(2))
                .andExpect(jsonPath("$.createdBy").value(userId))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("저장 시 name 누락이면 400 C001을 반환한다(calculate와 달리 저장은 name 필수)")
    void createRecipeWithoutNameReturnsC001() throws Exception {
        String token = signupAndLogin("레시피농부-이름없음");
        long farmId = createFarm(token, "이름없음 농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/nutrient-recipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 저장 시 403 F002를 반환한다")
    void createRecipeAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("주인장-저장");
        String otherToken = signupAndLogin("남남남-저장");
        long farmId = createFarm(ownerToken, "격리 저장 농장");

        mockMvc.perform(post("/api/farms/" + farmId + "/nutrient-recipes")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("몰래 저장"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("데모 계정도 레시피 저장이 201로 성공한다(체험 핵심)")
    void createRecipeAsDemoAccountAllowed() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/demo-login"))
                .andExpect(status().isOk())
                .andReturn();
        String demoToken = readJson(loginResult).get("accessToken").asText();
        MvcResult farmsResult = mockMvc.perform(get("/api/farms")
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isOk())
                .andReturn();
        long demoFarmId = readJson(farmsResult).get(0).get("id").asLong();

        mockMvc.perform(post("/api/farms/" + demoFarmId + "/nutrient-recipes")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("데모 레시피"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("데모 레시피"));
    }

    // ── 목록 조회(페이지네이션·최신순) ────────────────────────────

    @Test
    @DisplayName("목록은 최신순이며 PageResponse 필드가 정확하다")
    void findRecipesOrderedByCreatedAtDesc() throws Exception {
        String token = signupAndLogin("목록농부");
        long farmId = createFarm(token, "목록 농장");
        long oldRecipe = createRecipe(token, farmId, "첫번째");
        long midRecipe = createRecipe(token, farmId, "두번째");
        long newRecipe = createRecipe(token, farmId, "세번째");

        mockMvc.perform(get("/api/farms/" + farmId + "/nutrient-recipes")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(newRecipe))
                .andExpect(jsonPath("$.content[1].id").value(midRecipe))
                .andExpect(jsonPath("$.content[0].estimatedEc").isNumber())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/farms/" + farmId + "/nutrient-recipes")
                        .param("page", "1")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(oldRecipe));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 목록 조회 시 403 F002를 반환한다")
    void findRecipesAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("주인장-목록");
        String otherToken = signupAndLogin("남남남-목록");
        long farmId = createFarm(ownerToken, "격리 목록 농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/nutrient-recipes")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    // ── 단건 조회 ──────────────────────────────────────────────

    @Test
    @DisplayName("단건 조회는 저장 시점 계산 결과를 그대로 동봉한다")
    void findRecipeReturnsSnapshot() throws Exception {
        String token = signupAndLogin("단건농부");
        long farmId = createFarm(token, "단건 농장");
        long recipeId = createRecipe(token, farmId, "단건 레시피");

        mockMvc.perform(get("/api/farms/" + farmId + "/nutrient-recipes/" + recipeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recipeId))
                .andExpect(jsonPath("$.calculation.tanks.length()").value(2))
                .andExpect(jsonPath("$.calculation.warnings").isArray());
    }

    @Test
    @DisplayName("존재하지 않는 recipeId 조회 시 404 N001을 반환한다")
    void findRecipeNotFound() throws Exception {
        String token = signupAndLogin("단건농부-없음");
        long farmId = createFarm(token, "미존재 농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/nutrient-recipes/999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("N001"));
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 recipeId로 조회 시 404 N001을 반환한다(농장 스코프)")
    void findRecipeCrossTenantScoped() throws Exception {
        String ownerAToken = signupAndLogin("농장주A-레시피");
        String ownerBToken = signupAndLogin("농장주B-레시피");
        long farmA = createFarm(ownerAToken, "레시피 농장 A");
        long farmB = createFarm(ownerBToken, "레시피 농장 B");
        long recipeId = createRecipe(ownerAToken, farmA, "A농장 레시피");

        mockMvc.perform(get("/api/farms/" + farmB + "/nutrient-recipes/" + recipeId)
                        .header("Authorization", "Bearer " + ownerBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("N001"));
    }

    // ── 수정(작성자 본인만) ────────────────────────────────────

    @Test
    @DisplayName("작성자 본인은 200으로 수정할 수 있다")
    void updateRecipeByAuthorSuccess() throws Exception {
        String token = signupAndLogin("수정농부");
        long farmId = createFarm(token, "수정 농장");
        long recipeId = createRecipe(token, farmId, "수정 전");

        NutrientRecipeRequest updated = new NutrientRecipeRequest("수정 후", GrowthStage.VEGETATIVE,
                new NutrientTargetRequest(120.0, 47.0, 210.0, 169.0, 60.0, 79.0), 1.0, 1.0, null);

        mockMvc.perform(patch("/api/farms/" + farmId + "/nutrient-recipes/" + recipeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("수정 후"))
                .andExpect(jsonPath("$.stage").value("VEGETATIVE"))
                .andExpect(jsonPath("$.target.k").value(210.0));
    }

    @Test
    @DisplayName("작성자가 아닌 멤버는(OWNER 포함) 403 N002를 반환한다")
    void updateRecipeByNonAuthorMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("주인장-레시피수정");
        String memberToken = signupAndLogin("일꾼이-레시피수정");
        long farmId = createFarm(ownerToken, "권한 레시피 농장");
        joinFarmAs(ownerToken, farmId, memberToken, FarmRole.OPERATOR);
        long recipeId = createRecipe(memberToken, farmId, "일꾼이 레시피");

        mockMvc.perform(patch("/api/farms/" + farmId + "/nutrient-recipes/" + recipeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("주인장이 수정 시도"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("N002"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 수정 시 403 F002를 반환한다(N002보다 먼저 멤버십 검사)")
    void updateRecipeAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("주인장-레시피수정2");
        String otherToken = signupAndLogin("남남남-레시피수정2");
        long farmId = createFarm(ownerToken, "격리 레시피수정 농장");
        long recipeId = createRecipe(ownerToken, farmId, "원본 레시피");

        mockMvc.perform(patch("/api/farms/" + farmId + "/nutrient-recipes/" + recipeId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("남남남이 수정 시도"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("존재하지 않는 recipeId 수정 시 404 N001을 반환한다")
    void updateRecipeNotFound() throws Exception {
        String token = signupAndLogin("수정농부-없음");
        long farmId = createFarm(token, "미존재 수정 농장");

        mockMvc.perform(patch("/api/farms/" + farmId + "/nutrient-recipes/999999999")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("없는 레시피 수정"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("N001"));
    }

    // ── 삭제(작성자 본인 또는 OWNER) ─────────────────────────────

    @Test
    @DisplayName("작성자 본인은 204로 삭제할 수 있다")
    void deleteRecipeByAuthorSuccess() throws Exception {
        String token = signupAndLogin("삭제농부");
        long farmId = createFarm(token, "삭제 농장");
        long recipeId = createRecipe(token, farmId, "삭제될 레시피");

        mockMvc.perform(delete("/api/farms/" + farmId + "/nutrient-recipes/" + recipeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/farms/" + farmId + "/nutrient-recipes/" + recipeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("N001"));
    }

    @Test
    @DisplayName("작성자가 아니어도 OWNER는 204로 삭제할 수 있다")
    void deleteRecipeByOwnerNotAuthorSuccess() throws Exception {
        String ownerToken = signupAndLogin("주인장-레시피삭제");
        String memberToken = signupAndLogin("일꾼이-레시피삭제");
        long farmId = createFarm(ownerToken, "OWNER삭제 레시피 농장");
        joinFarmAs(ownerToken, farmId, memberToken, FarmRole.OPERATOR);
        long recipeId = createRecipe(memberToken, farmId, "일꾼이 레시피2");

        mockMvc.perform(delete("/api/farms/" + farmId + "/nutrient-recipes/" + recipeId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("작성자도 OWNER도 아닌 멤버는 403 N002를 반환한다")
    void deleteRecipeByNonAuthorNonOwnerForbidden() throws Exception {
        String ownerToken = signupAndLogin("주인장-레시피삭제2");
        String authorToken = signupAndLogin("작성자-레시피삭제2");
        String bystanderToken = signupAndLogin("구경꾼-레시피삭제2");
        long farmId = createFarm(ownerToken, "제3자 레시피 농장");
        joinFarmAs(ownerToken, farmId, authorToken, FarmRole.OPERATOR);
        joinFarmAs(ownerToken, farmId, bystanderToken, FarmRole.OPERATOR);
        long recipeId = createRecipe(authorToken, farmId, "작성자 레시피");

        mockMvc.perform(delete("/api/farms/" + farmId + "/nutrient-recipes/" + recipeId)
                        .header("Authorization", "Bearer " + bystanderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("N002"));
    }

    @Test
    @DisplayName("존재하지 않는 recipeId 삭제 시 404 N001을 반환한다")
    void deleteRecipeNotFound() throws Exception {
        String token = signupAndLogin("삭제농부-없음");
        long farmId = createFarm(token, "미존재 삭제 농장");

        mockMvc.perform(delete("/api/farms/" + farmId + "/nutrient-recipes/999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("N001"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 삭제 시 403 F002를 반환한다")
    void deleteRecipeAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("주인장-레시피삭제3");
        String otherToken = signupAndLogin("남남남-레시피삭제3");
        long farmId = createFarm(ownerToken, "격리 레시피삭제 농장");
        long recipeId = createRecipe(ownerToken, farmId, "격리 레시피");

        mockMvc.perform(delete("/api/farms/" + farmId + "/nutrient-recipes/" + recipeId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    private long createRecipe(String token, long farmId, String name) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/farms/" + farmId + "/nutrient-recipes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(name))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }
}
