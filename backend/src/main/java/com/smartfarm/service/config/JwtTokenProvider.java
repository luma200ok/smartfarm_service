package com.smartfarm.service.config;

import com.smartfarm.service.exception.CustomException;
import com.smartfarm.service.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenValidityMillis;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMillis = properties.accessTokenValidity().toMillis();
    }

    public String createAccessToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenValidityMillis))
                .signWith(key, Jwts.SIG.HS256) // 시크릿 길이에 따른 alg 변동 제거 — HS256 고정
                .compact();
    }

    /**
     * 액세스 토큰 검증 후 userId 반환.
     * 만료 → A003, 그 외 무효(변조·형식 오류) → A004.
     */
    public Long parseUserId(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            if (!Jwts.SIG.HS256.getId().equals(jws.getHeader().getAlgorithm())) {
                throw new CustomException(ErrorCode.A004); // 검증도 HS256만 허용
            }
            return Long.parseLong(jws.getPayload().getSubject());
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.A003);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.A004);
        }
    }
}
