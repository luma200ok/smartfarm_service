package com.smartfarm.service.service;

import com.smartfarm.service.dto.ForecastResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 날씨예보 응답의 서버 전역 단일 캐시(contract §4.8 — EnvironmentCache와 동일 패턴, 전역 고정 지점
 * 하나뿐이라 farmId 무관 단일 값). TTL 60분(contract). KMA 장애 시에도 만료된 값을 폴백으로 쓸 수
 * 있도록 {@link #getStale()}은 만료 여부와 무관하게 최근 값을 노출한다.
 */
@Component
public class ForecastCache {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(60);

    private final Duration ttl;
    private final AtomicReference<CachedValue> holder = new AtomicReference<>();

    public ForecastCache() {
        this(DEFAULT_TTL);
    }

    /** 테스트 전용 — 짧은 TTL을 주입해 만료 로직을 실시간 대기 없이 검증한다(package-private). */
    ForecastCache(Duration ttl) {
        this.ttl = ttl;
    }

    private record CachedValue(ForecastResponse response, Instant expiresAt) {
    }

    public Optional<ForecastResponse> getIfFresh() {
        CachedValue cached = holder.get();
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            return Optional.of(cached.response());
        }
        return Optional.empty();
    }

    /** 만료 여부와 무관하게 최근 값(있다면) — KMA 장애 시 폴백 조회용. */
    public Optional<ForecastResponse> getStale() {
        return Optional.ofNullable(holder.get()).map(CachedValue::response);
    }

    public void put(ForecastResponse response) {
        holder.set(new CachedValue(response, Instant.now().plus(ttl)));
    }

    /** 테스트 격리용 — 싱글턴 빈이라 통합 테스트 간(같은 Spring 컨텍스트 공유) 상태가 새지 않도록 초기화한다. */
    public void evict() {
        holder.set(null);
    }
}
