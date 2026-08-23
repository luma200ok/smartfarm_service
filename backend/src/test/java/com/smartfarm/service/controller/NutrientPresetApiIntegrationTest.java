package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.FarmTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 양액 프리셋 조회 통합 테스트(contract §4.9, 이슈 #64) — 인증만 필요, 농장 스코프 아님. */
class NutrientPresetApiIntegrationTest extends FarmTestSupport {

    @Test
    @DisplayName("TOMATO 프리셋은 생육단계 4개(SEEDLING/VEGETATIVE/FRUITING/HARVEST)를 반환한다")
    void findPresetsReturnsFourStages() throws Exception {
        String token = signupAndLogin("프리셋조회자");

        mockMvc.perform(get("/api/nutrient-presets")
                        .param("cropType", "TOMATO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    @DisplayName("SEEDLING 프리셋 목표값은 NutrientPresets 상수(OSU HYG-1437 Table3 S1)와 정확히 일치한다")
    void seedlingPresetMatchesSourceTable() throws Exception {
        String token = signupAndLogin("프리셋조회자-값검증");

        mockMvc.perform(get("/api/nutrient-presets")
                        .param("cropType", "TOMATO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.stage=='SEEDLING')].target.n").value(90.0))
                .andExpect(jsonPath("$[?(@.stage=='SEEDLING')].target.p").value(47.0))
                .andExpect(jsonPath("$[?(@.stage=='SEEDLING')].target.k").value(144.0))
                .andExpect(jsonPath("$[?(@.stage=='SEEDLING')].target.ca").value(160.0))
                .andExpect(jsonPath("$[?(@.stage=='SEEDLING')].target.mg").value(60.0))
                .andExpect(jsonPath("$[?(@.stage=='SEEDLING')].target.s").value(79.0))
                .andExpect(jsonPath("$[?(@.stage=='SEEDLING')].cropType").value("TOMATO"));
    }

    @Test
    @DisplayName("cropType 누락 시 400 C001을 반환한다")
    void missingCropTypeReturnsC001() throws Exception {
        String token = signupAndLogin("프리셋조회자-누락");

        mockMvc.perform(get("/api/nutrient-presets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("존재하지 않는 cropType 값이면 400 C001을 반환한다(enum 바인딩 실패)")
    void invalidCropTypeReturnsC001() throws Exception {
        String token = signupAndLogin("프리셋조회자-잘못된값");

        mockMvc.perform(get("/api/nutrient-presets")
                        .param("cropType", "POTATO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("인증 없이 조회하면 401을 반환한다")
    void unauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/nutrient-presets").param("cropType", "TOMATO"))
                .andExpect(status().isUnauthorized());
    }
}
