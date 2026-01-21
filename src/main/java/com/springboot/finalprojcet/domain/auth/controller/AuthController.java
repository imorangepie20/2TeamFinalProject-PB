package com.springboot.finalprojcet.domain.auth.controller;

import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.auth.dto.login.LoginRequestDto;
import com.springboot.finalprojcet.domain.auth.dto.login.LoginResponseDto;
import com.springboot.finalprojcet.domain.auth.dto.sign.SignupRequestDto;
import com.springboot.finalprojcet.domain.auth.dto.sign.SignupResponseDto;
import com.springboot.finalprojcet.domain.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "인증 관련 API")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "유저가 로그인을 합니다.")
    public ResponseEntity<LoginResponseDto> login(@ParameterObject LoginRequestDto loginRequestDto) {
        LoginResponseDto responseDto = authService.login(loginRequestDto);
        return ResponseEntity.ok(responseDto);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh 토큰 재발급", description = "유저가 Refresh 토큰을 재발급 받습니다.")
    public ResponseEntity<LoginResponseDto> refresh(String refreshToken) {
        LoginResponseDto responseDto = authService.refresh(refreshToken);
        return ResponseEntity.ok(responseDto);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "유저가 로그아웃 합니다.")
    public ResponseEntity<String> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        authService.logout(userDetails.getUsername());
        return ResponseEntity.ok("로그아웃 되었습니다.");
    }

    @PostMapping("/signin")
    @Operation(summary = "회원가입", description = "유저가 회원가입을 합니다.")
    public ResponseEntity<SignupResponseDto> signup(
            @ParameterObject SignupRequestDto signupRequestDto
    ) {
        SignupResponseDto responseDto = authService.signup(signupRequestDto);
        return ResponseEntity.ok(responseDto);
    }

}
