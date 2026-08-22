package com.smartfarm.service.controller;

import com.smartfarm.service.dto.LoginRequest;
import com.smartfarm.service.dto.RefreshRequest;
import com.smartfarm.service.dto.SignupRequest;
import com.smartfarm.service.dto.TokenResponse;
import com.smartfarm.service.dto.UserResponse;
import com.smartfarm.service.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        UserResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "데모 로그인 (공개) — 자격증명 없이 공유 데모 계정 토큰 발급",
            description = "포트폴리오 체험용. 데모 계정은 파괴적 작업(탈퇴·농장 생성/수정/삭제·웹훅·"
                    + "초대·멤버 제거)이 403 A007로 차단된다(contract §4.5).")
    @PostMapping("/demo-login")
    public ResponseEntity<TokenResponse> demoLogin() {
        return ResponseEntity.ok(authService.demoLogin());
    }

    @Operation(summary = "토큰 재발급(로테이션)")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody RefreshRequest request) {
        authService.logout(userId, request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
