package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.smartfarm.service.entity.SensorMetric;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.RackLevelRepository;
import com.smartfarm.service.repository.RackRepository;
import com.smartfarm.service.repository.ReadingSeriesBucketProjection;
import com.smartfarm.service.repository.SensorReadingRepository;
import com.smartfarm.service.repository.ZoneRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ReadingService#exportCsv} 행 수 상한({@link ReadingService#MAX_EXPORT_ROWS}) 순수 단위
 * 테스트 — 실사용 경로(series 다운샘플 재사용)로는 metric 4개 × 24h 최대 1440버킷 = 5760이 이미
 * 구조적 상한이라 <b>실 데이터로는 그 상한을 초과시킬 수 없다</b>(§4.11 "응답 크기 상한"과 동일
 * 계산, {@code ReadingService.MAX_EXPORT_ROWS} 주석 참고). 그래서 이 방어선은 레포지토리를
 * Mockito로 스텁해 <b>가정된 대량 응답</b>으로 검증한다 — 목적은 "미래에 다운샘플 버킷이 더
 * 촘촘해져도(예: 24h를 30초 버킷으로) 이 가드가 여전히 살아 있는가"를 잡는 회귀 방지다
 * ({@code SensorSimulatorServiceTest}와 동일하게 순수 계산 로직을 Mockito로 격리하는 이유도 같다).
 *
 * <p><b>뮤테이션 확인(수동, 구현 중 실제로 수행)</b>: {@code ReadingService.exportCsv}의
 * {@code if (totalRows > MAX_EXPORT_ROWS)} 가드를 주석 처리하고 이 클래스를 다시 돌리면
 * {@code exportCsvThrowsWhenRowCapExceeded}가 RED로 바뀐다(그 가드가 실제로 이 테스트를 통과시키는
 * 유일한 코드임을 확인 — 결과는 구현 보고에 기록).
 */
@ExtendWith(MockitoExtension.class)
class ReadingServiceExportTest {

    @Mock
    private FarmAccessGuard farmAccessGuard;
    @Mock
    private ZoneRepository zoneRepository;
    @Mock
    private RackRepository rackRepository;
    @Mock
    private RackLevelRepository rackLevelRepository;
    @Mock
    private SensorReadingRepository sensorReadingRepository;

    private ReadingService readingService;

    @BeforeEach
    void setUp() {
        readingService = new ReadingService(farmAccessGuard, zoneRepository, rackRepository,
                rackLevelRepository, sensorReadingRepository);
    }

    private record FakeBucket(LocalDateTime bucket, Double value) implements ReadingSeriesBucketProjection {
        @Override
        public LocalDateTime getBucket() {
            return bucket;
        }

        @Override
        public Double getValue() {
            return value;
        }
    }

    private List<ReadingSeriesBucketProjection> fakeRows(int count) {
        List<ReadingSeriesBucketProjection> rows = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        for (int i = 0; i < count; i++) {
            rows.add(new FakeBucket(base.plusMinutes(i), 20.0 + (i % 5)));
        }
        return rows;
    }

    @Test
    @DisplayName("metric당 1441행(24h 원본 상한+1) × 4metric = 5764행이면 413 SA003이다")
    void exportCsvThrowsWhenRowCapExceeded() {
        when(sensorReadingRepository.findSeriesAggregated(any(), any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(fakeRows(1441));

        assertThatThrownBy(() -> readingService.exportCsv(1L, 1L,
                List.of(SensorMetric.TEMPERATURE, SensorMetric.HUMIDITY, SensorMetric.CO2, SensorMetric.EC),
                "24h", "farm"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.SA003));
    }

    @Test
    @DisplayName("상한 정확히 이내(4metric × 1440행 = 5760)면 예외 없이 CSV가 만들어진다(경계값)")
    void exportCsvSucceedsAtExactCap() {
        when(sensorReadingRepository.findSeriesAggregated(any(), any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(fakeRows(1440));

        ReadingService.CsvExport export = readingService.exportCsv(1L, 1L,
                List.of(SensorMetric.TEMPERATURE, SensorMetric.HUMIDITY, SensorMetric.CO2, SensorMetric.EC),
                "24h", "farm");

        assertThat(export.content().length).isGreaterThan(0);
        assertThat(export.filename()).endsWith(".csv");
    }
}
