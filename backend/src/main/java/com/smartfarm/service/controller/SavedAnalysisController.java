package com.smartfarm.service.controller;

import com.smartfarm.service.dto.SavedAnalysisRequest;
import com.smartfarm.service.dto.SavedAnalysisResponse;
import com.smartfarm.service.dto.SavedAnalysisUpdateRequest;
import com.smartfarm.service.service.SavedAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 저장한 분석 API(contract §4.11 후속, 이슈 #126) — 데이터 화면 필터(metrics/range/scope)를 이름
 * 붙여 저장·조회·이름변경·삭제한다. 실행(그 필터로 다시 조회)은 기존
 * {@code GET /readings/series}를 그대로 쓴다(SavedAnalysisService 클래스 주석 참고).
 */
@Tag(name = "SavedAnalysis", description = "저장한 분석 API")
@RestController
@RequestMapping("/api/farms/{farmId}/saved-analyses")
@RequiredArgsConstructor
public class SavedAnalysisController {

    private final SavedAnalysisService savedAnalysisService;

    @Operation(summary = "저장한 분석 생성 (OPERATOR 이상) — metrics(최대 4)/range(24h|7d|30d)/scope를 이름 붙여 저장")
    @PostMapping
    public ResponseEntity<SavedAnalysisResponse> create(@AuthenticationPrincipal Long userId,
                                                          @PathVariable Long farmId,
                                                          @Valid @RequestBody SavedAnalysisRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedAnalysisService.create(farmId, userId, request));
    }

    @Operation(summary = "저장한 분석 목록 조회 (멤버, 최신순)")
    @GetMapping
    public ResponseEntity<List<SavedAnalysisResponse>> findAll(@AuthenticationPrincipal Long userId,
                                                                 @PathVariable Long farmId) {
        return ResponseEntity.ok(savedAnalysisService.findAll(farmId, userId));
    }

    @Operation(summary = "저장한 분석 이름 변경 (작성자 본인 또는 ADMIN) — name만 수정 가능")
    @PatchMapping("/{analysisId}")
    public ResponseEntity<SavedAnalysisResponse> rename(@AuthenticationPrincipal Long userId,
                                                          @PathVariable Long farmId,
                                                          @PathVariable Long analysisId,
                                                          @Valid @RequestBody SavedAnalysisUpdateRequest request) {
        return ResponseEntity.ok(savedAnalysisService.rename(farmId, userId, analysisId, request));
    }

    @Operation(summary = "저장한 분석 삭제 (작성자 본인 또는 ADMIN)")
    @DeleteMapping("/{analysisId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId,
                                        @PathVariable Long farmId,
                                        @PathVariable Long analysisId) {
        savedAnalysisService.delete(farmId, userId, analysisId);
        return ResponseEntity.noContent().build();
    }
}
