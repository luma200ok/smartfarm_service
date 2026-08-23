package com.smartfarm.service.service;

import com.smartfarm.service.config.KmaProperties;
import com.smartfarm.service.dto.ForecastResponse;
import com.smartfarm.service.dto.KmaForecastEnvelope;
import com.smartfarm.service.dto.SkyCondition;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 공공데이터포털 KMA 단기예보(getVilageFcst) 프록시(contract §4.8, 이슈 #56). 전역 고정 지점(env
 * {@code KMA_GRID_NX/NY})만 조회하므로 파라미터 없이 단일 시점 예보를 반환한다.
 *
 * <p>base_date/base_time은 KMA 발표 주기(02·05·08·11·14·17·20·23시, 발표 후 약 10분 뒤 조회 가능)에
 * 맞춰 "현재 시각 - 10분" 기준으로 가장 최근에 확정된 슬롯을 역산한다. 자정 직후(02:10 이전)에는
 * 전날 23시 슬롯으로 넘어간다.
 *
 * <p>KMA 응답 자체 오류(header.resultCode != "00" — 서비스키 오류·NODATA 등)·네트워크 실패·응답 파싱
 * 실패는 전부 {@link ErrorCode#W001}로 통일한다(ForecastService가 캐시 폴백 여부를 판단).
 */
@Slf4j
@Component
public class KmaForecastClient {

    /** KMA 단기예보 발표 시각(KST, 시 단위) — 발표 후 약 10분 뒤부터 조회 가능. */
    private static final int[] BASE_HOURS = {2, 5, 8, 11, 14, 17, 20, 23};
    private static final int AVAILABILITY_BUFFER_MINUTES = 10;
    private static final DateTimeFormatter FCST_TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");

    /** contract: 향후 24h, 1시간 간격 — 응답에 실제로 실린 고유 시각 수만큼만(최대 24개) 노출한다. */
    private static final int MAX_POINTS = 24;

    private final RestClient kmaRestClient;
    private final KmaProperties kmaProperties;
    private final Clock clock;

    @Autowired
    public KmaForecastClient(@Qualifier("kmaRestClient") RestClient kmaRestClient, KmaProperties kmaProperties) {
        this(kmaRestClient, kmaProperties, Clock.systemDefaultZone());
    }

    /** 테스트 전용 — base_date/base_time 슬롯 역산을 고정 시각으로 결정적으로 검증한다(package-private). */
    KmaForecastClient(RestClient kmaRestClient, KmaProperties kmaProperties, Clock clock) {
        this.kmaRestClient = kmaRestClient;
        this.kmaProperties = kmaProperties;
        this.clock = clock;
    }

    /**
     * 공공데이터포털 인증키(디코딩 키)에는 {@code +}·{@code =}가 흔한데, {@code +}는 쿼리에서 합법 문자라
     * UriBuilder가 그대로 두고 서버는 이를 공백으로 해석해 인증이 깨진다. 그래서 키만 직접 퍼센트
     * 인코딩한 쿼리 문자열을 만들어 {@link java.net.URI}로 넘긴다(URI를 직접 주면 재인코딩되지 않아
     * 이중 인코딩도 없다).
     */
    private java.net.URI buildForecastUri(BaseSlot slot) {
        String encodedKey = java.net.URLEncoder.encode(
                kmaProperties.serviceKey(), java.nio.charset.StandardCharsets.UTF_8);
        String query = "serviceKey=" + encodedKey
                + "&pageNo=1&numOfRows=1000&dataType=JSON"
                + "&base_date=" + slot.baseDate()
                + "&base_time=" + slot.baseTime()
                + "&nx=" + kmaProperties.gridNx()
                + "&ny=" + kmaProperties.gridNy();
        return java.net.URI.create(kmaProperties.baseUrl() + "/getVilageFcst?" + query);
    }

    public ForecastResponse fetchForecast() {
        try {
            BaseSlot slot = resolveBaseSlot();
            KmaForecastEnvelope envelope = kmaRestClient.get()
                    .uri(buildForecastUri(slot))
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            (req, res) -> {
                                throw new CustomException(ErrorCode.W001);
                            })
                    .body(KmaForecastEnvelope.class);

            if (!isSuccessful(envelope)) {
                throw new CustomException(ErrorCode.W001);
            }
            return toForecastResponse(envelope);
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            // 예외 본문에 요청 URL(서비스키 쿼리파라미터 포함)이 실릴 수 있어 WARN에는 클래스명만.
            log.warn("KMA 단기예보 조회 실패(접속불가/타임아웃/파싱): {}", e.getClass().getSimpleName());
            log.debug("KMA 단기예보 조회 실패 상세", e);
            throw new CustomException(ErrorCode.W001);
        } catch (RuntimeException e) {
            // fcstDate/fcstTime·fcstValue 형식이 예상과 다른 경우(NumberFormatException 등) — 응답 자체는
            // 200으로 왔으나 파싱할 수 없는 이례적 데이터라 마찬가지로 W001로 통일한다.
            log.warn("KMA 단기예보 응답 파싱 실패: {}", e.getClass().getSimpleName());
            log.debug("KMA 단기예보 응답 파싱 실패 상세", e);
            throw new CustomException(ErrorCode.W001);
        }
    }

    private boolean isSuccessful(KmaForecastEnvelope envelope) {
        return envelope != null
                && envelope.response() != null
                && envelope.response().header() != null
                && "00".equals(envelope.response().header().resultCode());
    }

    private ForecastResponse toForecastResponse(KmaForecastEnvelope envelope) {
        List<KmaForecastEnvelope.Item> items = extractItems(envelope);

        Map<LocalDateTime, PointAccumulator> byTime = new TreeMap<>();
        for (KmaForecastEnvelope.Item item : items) {
            LocalDateTime time = parseFcstDateTime(item.fcstDate(), item.fcstTime());
            PointAccumulator acc = byTime.computeIfAbsent(time, t -> new PointAccumulator());
            switch (item.category()) {
                case "TMP" -> acc.temp = parseDouble(item.fcstValue());
                case "REH" -> acc.humidity = parseDouble(item.fcstValue());
                case "SKY" -> acc.sky = mapSky(item.fcstValue());
                case "POP" -> acc.pop = parseInt(item.fcstValue());
                default -> {
                    // 관심 카테고리(TMP·REH·SKY·POP) 외는 무시(contract §4.8 필드 외 — PTY 등)
                }
            }
        }

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.HOURS);
        List<ForecastResponse.Point> points = byTime.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(now))
                .limit(MAX_POINTS)
                .map(entry -> entry.getValue().toPoint(entry.getKey()))
                .toList();

        return new ForecastResponse(LocalDateTime.now(clock), points);
    }

    private List<KmaForecastEnvelope.Item> extractItems(KmaForecastEnvelope envelope) {
        if (envelope.response().body() == null
                || envelope.response().body().items() == null
                || envelope.response().body().items().item() == null) {
            return List.of();
        }
        return envelope.response().body().items().item();
    }

    private LocalDateTime parseFcstDateTime(String fcstDate, String fcstTime) {
        LocalDate date = LocalDate.parse(fcstDate, DateTimeFormatter.BASIC_ISO_DATE);
        LocalTime time = LocalTime.parse(fcstTime, FCST_TIME_FORMATTER);
        return LocalDateTime.of(date, time);
    }

    private Double parseDouble(String value) {
        return value == null ? null : Double.valueOf(value);
    }

    private Integer parseInt(String value) {
        return value == null ? null : (int) Double.parseDouble(value);
    }

    /** SKY 코드 1=맑음, 3=구름많음, 4=흐림(2는 미사용) — 그 외 미지 코드는 null로 관대하게 처리한다. */
    private SkyCondition mapSky(String value) {
        return switch (value) {
            case "1" -> SkyCondition.SUNNY;
            case "3" -> SkyCondition.CLOUDY;
            case "4" -> SkyCondition.OVERCAST;
            default -> null;
        };
    }

    private BaseSlot resolveBaseSlot() {
        LocalDateTime reference = LocalDateTime.now(clock).minusMinutes(AVAILABILITY_BUFFER_MINUTES);
        LocalDate date = reference.toLocalDate();
        int hour = reference.getHour();
        for (int i = BASE_HOURS.length - 1; i >= 0; i--) {
            if (BASE_HOURS[i] <= hour) {
                return new BaseSlot(date, BASE_HOURS[i]);
            }
        }
        // 자정~02:10 이전 — 전날 23시 슬롯으로 넘어간다.
        return new BaseSlot(date.minusDays(1), BASE_HOURS[BASE_HOURS.length - 1]);
    }

    private record BaseSlot(LocalDate date, int hour) {
        String baseDate() {
            return date.format(DateTimeFormatter.BASIC_ISO_DATE);
        }

        String baseTime() {
            return "%02d00".formatted(hour);
        }
    }

    private static final class PointAccumulator {
        private Double temp;
        private Double humidity;
        private SkyCondition sky;
        private Integer pop;

        private ForecastResponse.Point toPoint(LocalDateTime time) {
            return new ForecastResponse.Point(time, temp, humidity, sky, pop);
        }
    }
}
