package com.smartfarm.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 공공데이터포털 KMA 단기예보(getVilageFcst) 원본 응답 매핑 전용 — 내부용, 계약 DTO 아님
 * (contract §4.8, 이슈 #56). 실제 응답 필드는 이미 camelCase(baseDate, fcstValue 등)라
 * 네이밍 전략 변환이 필요 없다.
 *
 * <p>header.resultCode가 "00"(NORMAL_SERVICE)이 아니면 KMA 자체 오류(서비스키 오류·NODATA 등)이므로
 * {@link com.smartfarm.service.service.KmaForecastClient}가 W001로 매핑한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KmaForecastEnvelope(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String category, String fcstDate, String fcstTime, String fcstValue) {
    }
}
