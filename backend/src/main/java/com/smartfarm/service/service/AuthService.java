package com.smartfarm.service.service;

import com.smartfarm.service.config.JwtProperties;
import com.smartfarm.service.config.JwtTokenProvider;
import com.smartfarm.service.dto.LoginRequest;
import com.smartfarm.service.dto.SignupRequest;
import com.smartfarm.service.dto.TokenResponse;
import com.smartfarm.service.dto.UserResponse;
import com.smartfarm.service.entity.RefreshToken;
import com.smartfarm.service.entity.User;
import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import com.smartfarm.service.repository.RefreshTokenRepository;
import com.smartfarm.service.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        // 이메일 정규화는 DTO compact constructor 단일 지점(EmailNormalizer)에서 수행됨
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.A001);
        }
        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .build();
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // 동시 가입 race → unique index 위반
            throw new CustomException(ErrorCode.A001);
        }
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.A002));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.A002);
        }
        return issueTokens(user.getId());
    }

    /**
     * 데모 로그인(contract §4.5, 이슈 #49) — 자격증명 없이 데모 유저(is_demo=true)로
     * 기존 토큰 발급 로직({@link #issueTokens})을 그대로 재사용한다(인증 필터·토큰 검증 무변경).
     * 데모 유저 존재는 시드(DemoAccountInitializer, 트래픽 수신 전 실행)가 보장하는 전제 —
     * 미존재는 서버 결함이므로 C002(500)로 처리한다(A00x 오용 금지).
     */
    @Transactional
    public TokenResponse demoLogin() {
        User demoUser = userRepository.findFirstByIsDemoTrueOrderByIdAsc()
                .orElseThrow(() -> {
                    log.error("데모 유저 미존재 — 시드(DemoAccountInitializer) 결함, contract §4.5 위반");
                    return new CustomException(ErrorCode.C002);
                });
        return issueTokens(demoUser.getId());
    }

    /**
     * refresh 로테이션 — refresh 토큰의 만료/무효/재사용은 전부 A004 (A003은 access 만료 전용, contract §5).
     * - 미존재/변조 → A004
     * - 만료 → A004 (부작용 없이 조용히 종료 — 만료 토큰 반복 제시가 전 기기 세션을
     *   무효화하는 DoS 프리미티브가 되지 않도록 재사용 검사보다 먼저 수행)
     * - 이미 revoke된 토큰 재사용(동시 요청 race 포함) → 해당 유저 전체 무효화 후 A004
     * - 유저 미존재(soft delete 포함) → 잔여 토큰 일괄 무효화 후 A004
     *
     * noRollbackFor: A004를 던져도 무효화 UPDATE는 커밋돼야 한다.
     */
    @Transactional(noRollbackFor = CustomException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        String tokenHash = TokenHasher.sha256(rawRefreshToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.A004));

        if (refreshToken.isExpired()) {
            throw new CustomException(ErrorCode.A004);
        }

        int revokedCount = refreshTokenRepository.revokeIfActive(refreshToken.getId());
        if (revokedCount == 0) {
            // 재사용 감지 → 해당 유저의 모든 refresh token 무효화
            log.warn("refresh token 재사용 감지 — userId={} 전체 토큰 무효화", refreshToken.getUserId());
            refreshTokenRepository.revokeAllByUserId(refreshToken.getUserId());
            throw new CustomException(ErrorCode.A004);
        }

        // soft delete된 유저의 세션 연장 차단 (@SQLRestriction으로 삭제 유저는 조회되지 않음)
        // + 잔여 refresh token 일괄 무효화
        if (userRepository.findById(refreshToken.getUserId()).isEmpty()) {
            refreshTokenRepository.revokeAllByUserId(refreshToken.getUserId());
            throw new CustomException(ErrorCode.A004);
        }

        return issueTokens(refreshToken.getUserId());
    }

    @Transactional
    public void logout(Long userId, String rawRefreshToken) {
        String tokenHash = TokenHasher.sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .filter(token -> token.getUserId().equals(userId))
                .ifPresent(token -> refreshTokenRepository.revokeIfActive(token.getId()));
    }

    private TokenResponse issueTokens(Long userId) {
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String rawRefreshToken = generateRefreshToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(TokenHasher.sha256(rawRefreshToken))
                .expiresAt(LocalDateTime.now().plus(jwtProperties.refreshTokenValidity()))
                .build();
        refreshTokenRepository.save(refreshToken);
        return new TokenResponse(accessToken, rawRefreshToken);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
