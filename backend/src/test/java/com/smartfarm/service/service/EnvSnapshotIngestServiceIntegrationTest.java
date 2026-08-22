package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.dto.AiEnvironmentResponse;
import com.smartfarm.service.entity.EnvSnapshot;
import com.smartfarm.service.repository.EnvSnapshotRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link EnvSnapshotIngestService} 적재 규칙(contract §4.6, 이슈 #52) — 부분 응답 수용, 동일
 * updated_at skip, updated_at 없음(null) skip을 실제 DB로 검증한다.
 */
@Transactional
class EnvSnapshotIngestServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private EnvSnapshotIngestService envSnapshotIngestService;

    @Autowired
    private EnvSnapshotRepository envSnapshotRepository;

    @Test
    @DisplayName("정상 응답은 그대로 적재된다")
    void ingestsFullResponse() {
        AiEnvironmentResponse response = new AiEnvironmentResponse(
                true,
                LocalDateTime.of(2026, 8, 22, 9, 0),
                new AiEnvironmentResponse.Weather(28.5, 62.0),
                new AiEnvironmentResponse.Indoor(24.1, 55.3, true),
                List.of(),
                List.of());

        Optional<EnvSnapshot> saved = envSnapshotIngestService.ingest(response);

        assertThat(saved).isPresent();
        assertThat(saved.get().getCapturedAt()).isEqualTo(LocalDateTime.of(2026, 8, 22, 9, 0));
        assertThat(saved.get().getOutdoorTemp()).isEqualTo(28.5);
        assertThat(saved.get().getIndoorHumidity()).isEqualTo(55.3);
        assertThat(saved.get().getControlled()).isTrue();
    }

    @Test
    @DisplayName("부분 응답(outdoor null)은 가용 필드만 저장하고 나머지는 null로 남는다")
    void ingestsPartialResponse() {
        AiEnvironmentResponse response = new AiEnvironmentResponse(
                true,
                LocalDateTime.of(2026, 8, 22, 9, 1),
                null,
                new AiEnvironmentResponse.Indoor(24.1, 55.3, true),
                List.of(),
                List.of("외기 데이터 조회 실패"));

        Optional<EnvSnapshot> saved = envSnapshotIngestService.ingest(response);

        assertThat(saved).isPresent();
        assertThat(saved.get().getOutdoorTemp()).isNull();
        assertThat(saved.get().getOutdoorHumidity()).isNull();
        assertThat(saved.get().getIndoorTemp()).isEqualTo(24.1);
    }

    @Test
    @DisplayName("직전 적재와 동일한 updated_at은 skip되고 새 행이 생기지 않는다")
    void skipsDuplicateUpdatedAt() {
        LocalDateTime sameUpdatedAt = LocalDateTime.of(2026, 8, 22, 9, 2);
        AiEnvironmentResponse first = new AiEnvironmentResponse(
                true, sameUpdatedAt,
                new AiEnvironmentResponse.Weather(20.0, 50.0),
                new AiEnvironmentResponse.Indoor(22.0, 45.0, false),
                List.of(), List.of());
        AiEnvironmentResponse duplicate = new AiEnvironmentResponse(
                true, sameUpdatedAt,
                new AiEnvironmentResponse.Weather(99.0, 99.0), // 값이 달라도 updated_at이 같으면 skip
                new AiEnvironmentResponse.Indoor(99.0, 99.0, true),
                List.of(), List.of());

        Optional<EnvSnapshot> firstSaved = envSnapshotIngestService.ingest(first);
        Optional<EnvSnapshot> secondSaved = envSnapshotIngestService.ingest(duplicate);

        assertThat(firstSaved).isPresent();
        assertThat(secondSaved).isEmpty();
        long countWithThisTimestamp = envSnapshotRepository.findAll().stream()
                .filter(s -> s.getCapturedAt().equals(sameUpdatedAt))
                .count();
        assertThat(countWithThisTimestamp).isEqualTo(1);
    }

    @Test
    @DisplayName("updated_at이 없는(null) 응답은 저장하지 않는다")
    void skipsWhenUpdatedAtMissing() {
        AiEnvironmentResponse response = new AiEnvironmentResponse(
                true, null, null, null, List.of(), List.of("상태 파일 없음"));

        Optional<EnvSnapshot> saved = envSnapshotIngestService.ingest(response);

        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("다른 updated_at이면 skip하지 않고 새 행으로 적재된다")
    void differentUpdatedAtIsNotSkipped() {
        AiEnvironmentResponse first = new AiEnvironmentResponse(
                true, LocalDateTime.of(2026, 8, 22, 9, 3),
                new AiEnvironmentResponse.Weather(20.0, 50.0),
                new AiEnvironmentResponse.Indoor(22.0, 45.0, false),
                List.of(), List.of());
        AiEnvironmentResponse second = new AiEnvironmentResponse(
                true, LocalDateTime.of(2026, 8, 22, 9, 4),
                new AiEnvironmentResponse.Weather(21.0, 51.0),
                new AiEnvironmentResponse.Indoor(23.0, 46.0, false),
                List.of(), List.of());

        Optional<EnvSnapshot> firstSaved = envSnapshotIngestService.ingest(first);
        Optional<EnvSnapshot> secondSaved = envSnapshotIngestService.ingest(second);

        assertThat(firstSaved).isPresent();
        assertThat(secondSaved).isPresent();
        assertThat(firstSaved.get().getId()).isNotEqualTo(secondSaved.get().getId());
    }
}
