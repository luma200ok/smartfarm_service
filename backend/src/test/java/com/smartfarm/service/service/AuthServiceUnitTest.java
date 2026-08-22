package com.smartfarm.service.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartfarm.service.config.JwtProperties;
import com.smartfarm.service.config.JwtTokenProvider;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.RefreshTokenRepository;
import com.smartfarm.service.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * demoLogin 미존재 → C002 분기 결정적 단위 테스트(#49 code-reviewer P2-3) — 통합 환경은 시드가
 * 기동 시 항상 데모 유저를 만들어 "미존재"를 재현할 수 없어 목으로 검증한다.
 */
class AuthServiceUnitTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final JwtProperties jwtProperties = mock(JwtProperties.class);

    private final AuthService authService = new AuthService(
            userRepository, refreshTokenRepository, passwordEncoder, jwtTokenProvider, jwtProperties);

    @Test
    @DisplayName("데모 유저 미존재 시 demoLogin은 C002(서버 결함)를 던진다 — A00x 오용 금지(contract §4.5)")
    void demoLoginWithoutSeededUserIsC002() {
        when(userRepository.findFirstByIsDemoTrueOrderByIdAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(authService::demoLogin)
                .isInstanceOf(CustomException.class)
                .extracting(t -> ((CustomException) t).getErrorCode())
                .isEqualTo(ErrorCode.C002);
    }
}
