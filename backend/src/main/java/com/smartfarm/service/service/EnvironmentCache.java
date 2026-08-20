package com.smartfarm.service.service;

import com.smartfarm.service.dto.EnvironmentTodayResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 환경 대시보드 응답의 서버 전역 단일 캐시(contract §3 Phase 3 handoff — 전 농장 공용 데이터라
 * farmId 무관 단일 값 하나면 충분하다). 키가 1개뿐이라 Caffeine 등 캐시 라이브러리 도입 대신
 * {@code AtomicReference<CachedValue>} + 만료 시각으로 충분하다고 판단(handoff 재량 허용 — 다중
 * 엔트리 축출·용량 관리가 필요 없는 단일 키 캐시에 라이브러리는 과함).
 *
 * <p>TTL 60s(contract). ai-server 장애 시에도 서비스가 만료된 값을 폴백으로 쓸 수 있도록
 * {@link #getStale()}은 만료 여부와 무관하게 최근 값을 노출한다.
 */
@Component
public class EnvironmentCache {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    private final Duration ttl;
    private final AtomicReference<CachedValue> holder = new AtomicReference<>();

    public EnvironmentCache() {
        this(DEFAULT_TTL);
    }

    /** 테스트 전용 — 짧은 TTL을 주입해 만료 로직을 실시간 대기 없이 검증한다(package-private). */
    EnvironmentCache(Duration ttl) {
        this.ttl = ttl;
    }

    private record CachedValue(EnvironmentTodayResponse response, Instant expiresAt) {
    }

    public Optional<EnvironmentTodayResponse> getIfFresh() {
        CachedValue cached = holder.get();
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            return Optional.of(cached.response());
        }
        return Optional.empty();
    }

    /** 만료 여부와 무관하게 최근 값(있다면) — ai-server 장애 시 폴백 조회용. */
    public Optional<EnvironmentTodayResponse> getStale() {
        return Optional.ofNullable(holder.get()).map(CachedValue::response);
    }

    public void put(EnvironmentTodayResponse response) {
        holder.set(new CachedValue(response, Instant.now().plus(ttl)));
    }

    /** 테스트 격리용 — 싱글턴 빈이라 통합 테스트 간(같은 Spring 컨텍스트 공유) 상태가 새지 않도록 초기화한다. */
    public void evict() {
        holder.set(null);
    }
}
