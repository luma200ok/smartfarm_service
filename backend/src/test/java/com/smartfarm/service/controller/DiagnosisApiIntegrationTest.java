package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.DiagnosisApiTestSupport;
import java.nio.charset.StandardCharsets;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class DiagnosisApiIntegrationTest extends DiagnosisApiTestSupport {

    private MockMultipartFile leafImage() {
        return new MockMultipartFile("file", "leaf.jpg", "image/jpeg",
                "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8));
    }

    // ── 생성 (정상/ood_blocked) ──────────────────────────────

    @Test
    @DisplayName("정상 진단 요청 시 201, ai-server 결과가 이력으로 저장·응답된다")
    void createDiagnosisSuccess() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "진단 농장");
        enqueueOkResponse();

        mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(leafImage())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.label").value("early_blight"))
                .andExpect(jsonPath("$.labelKr").value("잎마름병"))
                .andExpect(jsonPath("$.prob").value(0.93))
                .andExpect(jsonPath("$.part").value("leaf"))
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.camPngBase64").value("aGVsbG8="))
                .andExpect(jsonPath("$.createdBy").isNumber())
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("ood_blocked 응답도 200 프록시 결과대로 201로 정상 저장된다")
    void createDiagnosisOodBlocked() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "차단 농장");
        enqueueOodBlockedResponse();

        mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(leafImage())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ood_blocked"))
                .andExpect(jsonPath("$.reason").value("식물·잎으로 보이지 않는 사진(OOD)"))
                .andExpect(jsonPath("$.label").doesNotExist());
    }

    // ── ai-server 오류 매핑 ──────────────────────────────────

    @Test
    @DisplayName("ai-server 5xx 응답 시 502 D003을 반환한다")
    void createDiagnosisAiServer5xx() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "오류 농장");
        AI_SERVER.enqueue(new MockResponse().setResponseCode(500));

        mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(leafImage())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("D003"));
    }

    @Test
    @DisplayName("ai-server 응답 없음(타임아웃) 시 502 D003을 반환한다")
    void createDiagnosisAiServerTimeout() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "타임아웃 농장");
        AI_SERVER.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(leafImage())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("D003"));
    }

    @Test
    @DisplayName("ai-server가 이미지 검증 실패(400)를 반환하면 400 D002로 매핑한다")
    void createDiagnosisAiServerRejectsImage() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "거부 농장");
        AI_SERVER.enqueue(new MockResponse().setResponseCode(400));

        mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(leafImage())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("D002"));
    }

    // ── 업로드 검증 (ai-server 호출 전 차단) ──────────────────

    @Test
    @DisplayName("비이미지 content-type이면 ai-server 호출 없이 400 D002를 반환한다")
    void createDiagnosisNonImageContentType() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "검증 농장");
        MockMultipartFile textFile = new MockMultipartFile("file", "note.txt", "text/plain",
                "not an image".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(textFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("D002"));
    }

    @Test
    @DisplayName("빈 파일이면 400 D002를 반환한다")
    void createDiagnosisEmptyFile() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "빈파일 농장");
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(emptyFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("D002"));
    }

    @Test
    @DisplayName("file 파트가 아예 없으면 400 D002를 반환한다")
    void createDiagnosisMissingFilePart() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "파트누락 농장");

        mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("D002"));
    }

    @Test
    @DisplayName("10MB 초과 15MB 이하 이미지는 ai-server 호출 전 서비스 캡에서 400 D002를 반환한다"
            + "(Spring 멀티파트 캡 15MB는 통과하고 서비스 자체 10MB 캡에서 걸리는 경계 케이스)")
    void createDiagnosisOverServiceCapUnderMultipartCap() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "대용량 농장");
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", new byte[11 * 1024 * 1024]);

        mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(oversized)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("D002"));
    }

    // ── cross-tenant ─────────────────────────────────────────

    @Test
    @DisplayName("cross-tenant: 미멤버가 진단 생성 시 403 F002를 반환한다")
    void createDiagnosisAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 농장");

        mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(leafImage())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 진단 목록 조회 시 403 F002를 반환한다")
    void findDiagnosesAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 농장 둘");

        mockMvc.perform(get("/api/farms/" + farmId + "/diagnoses")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 diagnosisId로 상세 조회 시 404 D001을 반환한다(농장 스코프)")
    void findDiagnosisCrossTenant() throws Exception {
        String ownerAToken = signupAndLogin("농장주A");
        String ownerBToken = signupAndLogin("농장주B");
        long farmA = createFarm(ownerAToken, "농장 A");
        long farmB = createFarm(ownerBToken, "농장 B");
        enqueueOkResponse();
        long diagnosisId = createDiagnosis(ownerAToken, farmA);

        mockMvc.perform(get("/api/farms/" + farmB + "/diagnoses/" + diagnosisId)
                        .header("Authorization", "Bearer " + ownerBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("D001"));
    }

    @Test
    @DisplayName("존재하지 않는 diagnosisId는 404 D001을 반환한다")
    void findDiagnosisNotFound() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "미존재 농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/diagnoses/999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("D001"));
    }

    // ── 조회 (상세/목록/페이지네이션) ──────────────────────────

    @Test
    @DisplayName("멤버는 진단 상세를 farm 스코프로 조회할 수 있다")
    void findDiagnosisDetail() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String memberToken = signupAndLogin("일꾼이");
        long farmId = createFarm(ownerToken, "조회 농장");
        acceptInvitation(memberToken, createInvitationCode(ownerToken, farmId));
        enqueueOkResponse();
        long diagnosisId = createDiagnosis(ownerToken, farmId);

        mockMvc.perform(get("/api/farms/" + farmId + "/diagnoses/" + diagnosisId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(diagnosisId))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.label").value("early_blight"));
    }

    @Test
    @DisplayName("진단 목록은 최신순이며 PageResponse 필드가 정확하다")
    void findDiagnosesPagination() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "이력 농장");
        enqueueOkResponse();
        long first = createDiagnosis(token, farmId);
        enqueueOodBlockedResponse();
        long second = createDiagnosis(token, farmId);
        enqueueOkResponse();
        long third = createDiagnosis(token, farmId);

        mockMvc.perform(get("/api/farms/" + farmId + "/diagnoses")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.content[0].camPngBase64").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/farms/" + farmId + "/diagnoses")
                        .param("page", "1")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first));
    }

    private long createDiagnosis(String token, long farmId) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(leafImage())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }
}
