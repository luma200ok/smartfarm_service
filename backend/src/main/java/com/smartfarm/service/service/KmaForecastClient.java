package com.smartfarm.service.service;

import com.smartfarm.service.config.KmaProperties;
import com.smartfarm.service.dto.ForecastResponse;
import com.smartfarm.service.dto.KmaForecastEnvelope;
import com.smartfarm.service.dto.SkyCondition;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
     * 인코딩한 쿼리 문자열을 만들어 {@link URI}로 넘긴다(URI를 직접 주면 재인코딩되지 않아
     * 이중 인코딩도 없다).
     */
    private URI buildForecastUri(BaseSlot slot) {
        String encodedKey = URLEncoder.encode(kmaProperties.serviceKey(), StandardCharsets.UTF_8);
        String query = "serviceKey=" + encodedKey
                + "&pageNo=1&numOfRows=1000&dataType=JSON"
                + "&base_date=" + slot.baseDate()
                + "&base_time=" + slot.baseTime()
                + "&nx=" + kmaProperties.gridNx()
                + "&ny=" + kmaProperties.gridNy();
        return URI.create(kmaProperties.baseUrl() + "/getVilageFcst?" + query);
    }

    public ForecastResponse fetchForecast() {
        try {
            BaseSlot slot = resolveBaseSlot();
            KmaForecastEnvelope envelope = kmaRestClient.get()
                    .uri(buildForecastUri(slot))
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            (req, res) -> {
                                // 요청 URI(res에는 없지만 req.getURI()에는 서비스키 쿼리파라미터가 실린다)는
                                // 절대 로그에 남기지 않는다 — HTTP 상태 코드만 남긴다.
                                log.warn("KMA 단기예보 조회 실패(HTTP {})", res.getStatusCode().value());
                                throw new CustomException(ErrorCode.W001);
                            })
                    .body(KmaForecastEnvelope.class);

            if (!isSuccessful(envelope)) {
                log.warn("KMA 단기예보 응답 실패(resultCode={}, resultMsg={})",
                        extractResultCode(envelope), extractResultMsg(envelope));
                throw new CustomException(ErrorCode.W001);
            }
            return toForecastResponse(envelope);
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            // Spring이 I/O 실패를 감쌀 때 예외 메시지·스택트레이스에 요청 URI 전체(서비스키 쿼리파라미터
            // 포함)가 실릴 수 있다(이슈 #80 P1) — 지금은 로그 레벨이 INFO라 노출되지 않지만, 장애 시
            // DEBUG를 켜는 순간 평문으로 남는다. throwable을 그대로 넘기지 않고 클래스명+마스킹된
            // 메시지만 문자열로 조립해 DEBUG에 남긴다(스택트레이스 자체는 남기지 않음 — 스택트레이스
            // 헤더 라인에도 메시지가 반복 출력되므로 이 방식이 아니면 마스킹이 무의미해진다).
            log.warn("KMA 단기예보 조회 실패(접속불가/타임아웃/파싱): {}", e.getClass().getSimpleName());
            log.debug("KMA 단기예보 조회 실패 상세: {}", sanitizeForLog(e));
            throw new CustomException(ErrorCode.W001);
        } catch (RuntimeException e) {
            // fcstDate/fcstTime·fcstValue 형식이 예상과 다른 경우(NumberFormatException 등)와
            // CancellationException(JDK HttpClient 타임아웃 표면화, 이슈 #80)을 함께 잡는 catch-all —
            // 응답 자체는 200으로 왔으나 파싱할 수 없는 이례적 데이터거나 접속·타임아웃 실패이므로
            // 마찬가지로 W001로 통일한다. DEBUG 로그는 RestClientException 분기와 동일하게 throwable을
            // 직접 넘기지 않고 마스킹된 문자열로 남긴다(일관성·방어적 하드닝).
            log.warn("KMA 단기예보 응답 파싱/타임아웃 실패: {}", e.getClass().getSimpleName());
            log.debug("KMA 단기예보 응답 파싱/타임아웃 실패 상세: {}", sanitizeForLog(e));
            throw new CustomException(ErrorCode.W001);
        }
    }

    private boolean isSuccessful(KmaForecastEnvelope envelope) {
        return envelope != null
                && envelope.response() != null
                && envelope.response().header() != null
                && "00".equals(envelope.response().header().resultCode());
    }

    /** 실패 로깅 전용 — header가 없는 이례적 응답이면 null(서비스키는 포함되지 않는 필드만 사용). */
    private String extractResultCode(KmaForecastEnvelope envelope) {
        return hasHeader(envelope) ? envelope.response().header().resultCode() : null;
    }

    private String extractResultMsg(KmaForecastEnvelope envelope) {
        return hasHeader(envelope) ? envelope.response().header().resultMsg() : null;
    }

    private boolean hasHeader(KmaForecastEnvelope envelope) {
        return envelope != null && envelope.response() != null && envelope.response().header() != null;
    }

    /**
     * DEBUG 로그 전용(이슈 #80 P1) — throwable을 그대로 넘기면 원인 체인의 메시지(요청 URI·서비스키
     * 쿼리파라미터 포함 가능)가 스택트레이스와 함께 그대로 출력된다. 클래스명 + 서비스키를 마스킹한
     * 메시지만 원인 체인을 따라가며 조립한다 — 어떤 경로로도 키 원문이 로그에 남지 않는다.
     */
    private String sanitizeForLog(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (!sb.isEmpty()) {
                sb.append(" -> ");
            }
            sb.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null) {
                sb.append(": ").append(maskServiceKey(message));
            }
            current = current.getCause();
        }
        return sb.toString();
    }

    private String maskServiceKey(String message) {
        return message.replaceAll("(?i)(service ?key)=[^&\\s\"'\\]\\)]*", "$1=***");
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
