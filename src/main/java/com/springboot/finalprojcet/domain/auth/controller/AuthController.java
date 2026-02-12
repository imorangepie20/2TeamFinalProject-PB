package com.springboot.finalprojcet.domain.auth.controller;

import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.auth.dto.login.LoginRequestDto;
import com.springboot.finalprojcet.domain.auth.dto.login.LoginResponseDto;
import com.springboot.finalprojcet.domain.auth.dto.sign.SignupRequestDto;
import com.springboot.finalprojcet.domain.auth.dto.sign.SignupResponseDto;
import com.springboot.finalprojcet.domain.auth.service.AuthService;
import com.springboot.finalprojcet.domain.tidal.dto.TidalSyncRequest;
import com.springboot.finalprojcet.domain.tidal.dto.TidalSyncResponse;
import com.springboot.finalprojcet.domain.tidal.service.TidalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "인증 관련 API")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final TidalService tidalService;

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "유저가 로그인을 합니다.")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto loginRequestDto) {
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

    @PostMapping("/register")
    @Operation(summary = "회원가입", description = "유저가 회원가입을 합니다.")
    public ResponseEntity<SignupResponseDto> signup(@RequestBody SignupRequestDto signupRequestDto) {
        SignupResponseDto responseDto = authService.signup(signupRequestDto);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/me")
    @Operation(summary = "현재 사용자 정보", description = "현재 로그인한 사용자 정보를 가져옵니다.")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "인증이 필요합니다"));
        }
        var user = userDetails.getUser();
        return ResponseEntity.ok(java.util.Map.of(
                "user", java.util.Map.of(
                        "id", user.getUserId(),
                        "email", user.getEmail(),
                        "name", user.getNickname(),
                        "role", user.getRoleType() != null ? user.getRoleType().name() : "USER")));
    }

    @PostMapping("/sync/tidal")
    @Operation(summary = "Tidal 동기화", description = "회원가입 후 Tidal 플레이리스트를 동기화합니다.")
    public ResponseEntity<TidalSyncResponse> syncTidal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody TidalSyncRequest request) {

        if (userDetails == null) {
            return ResponseEntity.status(401).body(TidalSyncResponse.builder()
                    .success(false)
                    .error("User authenticated failed (UserDetails is null)")
                    .build());
        }

        Long userId = userDetails.getUser().getUserId();
        TidalSyncResponse response = tidalService.syncTidal(userId, request);
        return ResponseEntity.ok(response);
    }
}
