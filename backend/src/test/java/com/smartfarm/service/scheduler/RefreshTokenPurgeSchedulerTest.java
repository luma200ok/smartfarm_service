package com.smartfarm.service.scheduler;

import static com.smartfarm.service.scheduler.RefreshTokenPurgeScheduler.GRACE_PERIOD_DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.entity.RefreshToken;
import com.smartfarm.service.entity.User;
import com.smartfarm.service.repository.RefreshTokenRepository;
import com.smartfarm.service.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * refresh_tokens 퍼지 스케줄러 검증 — expiresAt을 직접 다양하게 지정해 재현하고 purge()를
 * 직접 호출한다(@Scheduled 자체는 application-test.yml에서 PT1H로 키워 테스트 실행 중
 * 자동 구동되지 않는다 — PrescriptionSweeperTest 선례). GRACE_PERIOD_DAYS 상수를 그대로
 * 참조하기 위해 스케줄러와 같은 패키지에 둔다(package-private 상수 접근).
 *
 * <p>application-test.yml의 purge-batch-size=2로 소수 행으로도 배치 반복 루프를 검증한다.
 */
class RefreshTokenPurgeSchedulerTest extends IntegrationTestSupport {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenPurgeScheduler purgeScheduler;

    @Test
    @DisplayName("퍼지: 만료 후 유예를 넘긴 토큰은 삭제된다")
    void purgeDeletesTokensBeyondGracePeriod() {
        Long userId = saveUser("퍼지대상").getId();
        // 유예(GRACE_PERIOD_DAYS)를 넘겨 만료된 토큰
        long id = saveToken(userId, LocalDateTime.now().minusDays(GRACE_PERIOD_DAYS + 1));

        purgeScheduler.purge();

        assertThat(refreshTokenRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("퍼지: 만료됐지만 유예 이내인 토큰은 재사용 감지 신호 보존을 위해 남긴다")
    void purgeKeepsTokensWithinGracePeriod() {
        Long userId = saveUser("유예내토큰").getId();
        // 유예(GRACE_PERIOD_DAYS) 이내에 만료된 토큰
        long id = saveToken(userId, LocalDateTime.now().minusDays(GRACE_PERIOD_DAYS - 4));

        purgeScheduler.purge();

        assertThat(refreshTokenRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("퍼지: 아직 만료되지 않은 활성 토큰은 건드리지 않는다")
    void purgeLeavesActiveTokensUntouched() {
        Long userId = saveUser("활성토큰").getId();
        long id = saveToken(userId, LocalDateTime.now().plusDays(14)); // 미만료

        purgeScheduler.purge();

        assertThat(refreshTokenRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("퍼지: 배치 크기(테스트 설정 2)를 초과하는 대상도 반복 호출로 모두 삭제된다")
    void purgeDrainsAllRowsAcrossMultipleBatches() {
        Long userId = saveUser("배치대상").getId();
        // 배치 크기(2)보다 많은 5건 — 2+2+1 세 배치로 나뉘어야 전부 삭제된다
        List<Long> ids = List.of(
                saveToken(userId, LocalDateTime.now().minusDays(GRACE_PERIOD_DAYS + 1)),
                saveToken(userId, LocalDateTime.now().minusDays(GRACE_PERIOD_DAYS + 2)),
                saveToken(userId, LocalDateTime.now().minusDays(GRACE_PERIOD_DAYS + 3)),
                saveToken(userId, LocalDateTime.now().minusDays(GRACE_PERIOD_DAYS + 4)),
                saveToken(userId, LocalDateTime.now().minusDays(GRACE_PERIOD_DAYS + 5)));

        purgeScheduler.purge();

        ids.forEach(id -> assertThat(refreshTokenRepository.findById(id)).isEmpty());
    }

    private User saveUser(String nickname) {
        return userRepository.save(User.builder()
                .email(nickname + "@example.com")
                .password("encoded-password")
                .nickname(nickname)
                .build());
    }

    private long saveToken(Long userId, LocalDateTime expiresAt) {
        return refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash("hash-" + userId + "-" + expiresAt)
                .expiresAt(expiresAt)
                .build()).getId();
    }
}
