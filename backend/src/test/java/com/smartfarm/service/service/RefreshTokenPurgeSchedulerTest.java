package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.IntegrationTestSupport;
import com.smartfarm.service.entity.RefreshToken;
import com.smartfarm.service.entity.User;
import com.smartfarm.service.repository.RefreshTokenRepository;
import com.smartfarm.service.repository.UserRepository;
import com.smartfarm.service.scheduler.RefreshTokenPurgeScheduler;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * refresh_tokens 퍼지 스케줄러 검증 — expiresAt을 직접 다양하게 지정해 재현하고 purge()를
 * 직접 호출한다(@Scheduled 자체는 application-test.yml에서 PT1H로 키워 테스트 실행 중
 * 자동 구동되지 않는다 — PrescriptionSweeperTest 선례).
 */
class RefreshTokenPurgeSchedulerTest extends IntegrationTestSupport {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenPurgeScheduler purgeScheduler;

    @Test
    @DisplayName("퍼지: 만료 후 유예(7일)를 넘긴 토큰은 삭제된다")
    void purgeDeletesTokensBeyondGracePeriod() {
        Long userId = saveUser("퍼지대상").getId();
        long id = saveToken(userId, LocalDateTime.now().minusDays(8)); // 만료 8일 경과 > 7일 유예

        purgeScheduler.purge();

        assertThat(refreshTokenRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("퍼지: 만료됐지만 유예(7일) 이내인 토큰은 재사용 감지 신호 보존을 위해 남긴다")
    void purgeKeepsTokensWithinGracePeriod() {
        Long userId = saveUser("유예내토큰").getId();
        long id = saveToken(userId, LocalDateTime.now().minusDays(3)); // 만료 3일 경과, 7일 유예 이내

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
