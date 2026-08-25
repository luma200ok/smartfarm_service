package com.smartfarm.service.controller;

import com.smartfarm.service.dto.LevelSummaryResponse;
import com.smartfarm.service.dto.ReadingMatrixResponse;
import com.smartfarm.service.dto.ReadingSeriesResponse;
import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.service.ReadingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 센서 측정값 조회 API(contract §4.11, 이슈 #90) — 가상 장비 시뮬레이터가 적재한 랙×층 스코프
 * 측정값을 조회한다. 전부 멤버 권한(조회 전용, 쓰기 경로 없음 — 적재는 시뮬레이터/스케줄러 전용).
 */
@Tag(name = "Reading", description = "센서 측정값 조회 API")
@RestController
@RequestMapping("/api/farms/{farmId}/readings")
@RequiredArgsConstructor
public class ReadingController {

    private final ReadingService readingService;

    @Operation(summary = "시계열 조회 (멤버) — metrics 최대 4개, range 24h/7d/30d, "
            + "scope=farm|zone:{id}|rack:{id}|level:{id}")
    @GetMapping("/series")
    public ResponseEntity<ReadingSeriesResponse> series(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId,
            @RequestParam List<SensorMetric> metrics,
            @RequestParam(required = false) String range,
            @RequestParam String scope) {
        return ResponseEntity.ok(readingService.series(farmId, userId, metrics, range, scope));
    }

    @Operation(summary = "CSV 내보내기 (멤버) — series와 동일 파라미터·동일 다운샘플을 CSV로, "
            + "행 수 상한(5760) 초과 시 413 SA003")
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId,
            @RequestParam List<SensorMetric> metrics,
            @RequestParam(required = false) String range,
            @RequestParam String scope) {
        ReadingService.CsvExport export = readingService.exportCsv(farmId, userId, metrics, range, scope);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(export.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(export.content());
    }

    @Operation(summary = "랙 도면 최신값 조회 (멤버) — metric 필수, zoneId 생략 시 농장 전체")
    @GetMapping("/latest")
    public ResponseEntity<ReadingMatrixResponse> latest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId,
            @RequestParam SensorMetric metric,
            @RequestParam(required = false) Long zoneId) {
        return ResponseEntity.ok(readingService.latest(farmId, userId, metric, zoneId));
    }

    @Operation(summary = "층별 비교표 조회 (멤버) — rackId 필수, range 24h/7d/30d")
    @GetMapping("/level-summary")
    public ResponseEntity<LevelSummaryResponse> levelSummary(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long farmId,
            @RequestParam Long rackId,
            @RequestParam(required = false) String range) {
        return ResponseEntity.ok(readingService.levelSummary(farmId, userId, rackId, range));
    }
}
