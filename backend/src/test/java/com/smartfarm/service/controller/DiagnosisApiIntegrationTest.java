package com.smartfarm.service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfarm.service.DiagnosisApiTestSupport;
import com.smartfarm.service.entity.Diagnosis;
import com.smartfarm.service.entity.FarmRole;
import com.smartfarm.service.repository.DiagnosisRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class DiagnosisApiIntegrationTest extends DiagnosisApiTestSupport {

    @Autowired
    private DiagnosisRepository diagnosisRepository;

    /** 매직바이트 검증(reviewer P2)을 통과하도록 JPEG 시그니처(FF D8 FF)를 실제로 앞에 붙인다. */
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] LEAF_IMAGE_BYTES =
            concat(JPEG_SIGNATURE, "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8));

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private MockMultipartFile leafImage() {
        return new MockMultipartFile("file", "leaf.jpg", "image/jpeg", LEAF_IMAGE_BYTES);
    }

    // ── 생성 (정상/ood_blocked) ──────────────────────────────

    @Test
    @DisplayName("정상 진단 요청 시 201, ai-server 결과가 이력으로 저장·응답된다")
    void createDiagnosisSuccess() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "진단 농장");
        enqueueOkResponse();

        MvcResult result = mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(leafImage())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.label").value("early_blight"))
                .andExpect(jsonPath("$.labelKr").value("잎마름병"))
                .andExpect(jsonPath("$.prob").value(0.93))
                .andExpect(jsonPath("$.part").value("leaf"))
                .andExpect(jsonPath("$.imageUrl").exists())
                .andExpect(jsonPath("$.camPngBase64").value("aGVsbG8="))
                .andExpect(jsonPath("$.createdBy").isNumber())
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        long diagnosisId = readJson(result).get("id").asLong();
        assertThat(readJson(result).get("imageUrl").asText())
                .isEqualTo("/api/farms/" + farmId + "/diagnoses/" + diagnosisId + "/image");
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

    // ── content-type 화이트리스트·매직바이트 검증 (reviewer P2) ──

    @Test
    @DisplayName("SVG 등 화이트리스트 밖 image/* content-type은 400 D002를 반환한다")
    void createDiagnosisSvgContentTypeRejected() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "SVG거부 농장");
        MockMultipartFile svgFile = new MockMultipartFile("file", "leaf.svg", "image/svg+xml",
                "<svg onload=\"alert(1)\"></svg>".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(svgFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("D002"));
    }

    @Test
    @DisplayName("content-type 위조(png 선언 + jpeg 매직바이트)는 400 D002를 반환한다")
    void createDiagnosisContentTypeSignatureMismatch() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "위조 농장");
        MockMultipartFile forged = new MockMultipartFile("file", "leaf.png", "image/png", LEAF_IMAGE_BYTES);

        mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(forged)
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
        joinFarmAs(ownerToken, farmId, memberToken, FarmRole.OPERATOR);
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

    // ── 이미지 저장 (성공/실패가 진단 자체에 영향 없음) ──────────

    @Test
    @DisplayName("이미지 저장 실패(디렉터리 생성 불가)여도 진단은 201로 유지되고 imageUrl은 null이다")
    void createDiagnosisImageStorageFailureDoesNotFailDiagnosis() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "저장실패 농장");
        // farmId 서브디렉터리 자리에 파일을 미리 만들어 두어 디렉터리 생성을 강제로 실패시킨다.
        Path blocker = IMAGE_STORAGE_DIR.resolve(String.valueOf(farmId));
        Files.write(blocker, "blocker".getBytes(StandardCharsets.UTF_8));
        enqueueOkResponse();

        mockMvc.perform(multipart("/api/farms/" + farmId + "/diagnoses")
                        .file(leafImage())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    // ── 이미지 조회 (인가 스트리밍) ──────────────────────────

    @Test
    @DisplayName("정상 이미지 스트리밍 — content-type·바이트가 원본과 일치한다")
    void findDiagnosisImageSuccess() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "이미지 농장");
        enqueueOkResponse();
        long diagnosisId = createDiagnosis(token, farmId);

        MvcResult result = mockMvc.perform(get(
                                "/api/farms/" + farmId + "/diagnoses/" + diagnosisId + "/image")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(LEAF_IMAGE_BYTES);
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 이미지 조회 시 403 F002를 반환한다")
    void findDiagnosisImageAsNonMember() throws Exception {
        String ownerToken = signupAndLogin("주인장");
        String otherToken = signupAndLogin("남남남");
        long farmId = createFarm(ownerToken, "격리 이미지 농장");
        enqueueOkResponse();
        long diagnosisId = createDiagnosis(ownerToken, farmId);

        mockMvc.perform(get("/api/farms/" + farmId + "/diagnoses/" + diagnosisId + "/image")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 diagnosisId로 이미지 조회 시 404 D001을 반환한다(농장 스코프)")
    void findDiagnosisImageCrossTenantDiagnosisId() throws Exception {
        String ownerAToken = signupAndLogin("농장주A-이미지");
        String ownerBToken = signupAndLogin("농장주B-이미지");
        long farmA = createFarm(ownerAToken, "이미지 농장 A");
        long farmB = createFarm(ownerBToken, "이미지 농장 B");
        enqueueOkResponse();
        long diagnosisId = createDiagnosis(ownerAToken, farmA);

        mockMvc.perform(get("/api/farms/" + farmB + "/diagnoses/" + diagnosisId + "/image")
                        .header("Authorization", "Bearer " + ownerBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("D001"));
    }

    @Test
    @DisplayName("이미지 미보유 진단(저장 실패) 조회 시 404 D004를 반환한다")
    void findDiagnosisImageNotStored() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "이미지없음 농장");
        Path blocker = IMAGE_STORAGE_DIR.resolve(String.valueOf(farmId));
        Files.write(blocker, "blocker".getBytes(StandardCharsets.UTF_8));
        enqueueOkResponse();
        long diagnosisId = createDiagnosis(token, farmId);

        mockMvc.perform(get("/api/farms/" + farmId + "/diagnoses/" + diagnosisId + "/image")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("D004"));
    }

    @Test
    @DisplayName("image_path에 path traversal 문자열이 주입돼도 500이 아닌 안전하게 404 D004로 실패한다")
    void findDiagnosisImagePathTraversalRejected() throws Exception {
        String token = signupAndLogin("농부");
        long farmId = createFarm(token, "트래버설 농장");
        enqueueOkResponse();
        long diagnosisId = createDiagnosis(token, farmId);

        Diagnosis diagnosis = diagnosisRepository.findById(diagnosisId).orElseThrow();
        diagnosis.attachImage("../../../../etc/passwd", "image/jpeg");
        diagnosisRepository.save(diagnosis);

        mockMvc.perform(get("/api/farms/" + farmId + "/diagnoses/" + diagnosisId + "/image")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("D004"));
    }
}
