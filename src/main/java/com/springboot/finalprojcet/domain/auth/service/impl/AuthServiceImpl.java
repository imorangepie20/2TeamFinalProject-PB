package com.springboot.finalprojcet.domain.auth.service.impl;

import com.springboot.finalprojcet.domain.auth.service.RefreshTokenService;
import com.springboot.finalprojcet.domain.auth.dto.login.LoginRequestDto;
import com.springboot.finalprojcet.domain.auth.dto.login.LoginResponseDto;
import com.springboot.finalprojcet.domain.auth.dto.sign.SignupRequestDto;
import com.springboot.finalprojcet.domain.auth.dto.sign.SignupResponseDto;
import com.springboot.finalprojcet.domain.auth.jwt.JwtTokenProvider;
import com.springboot.finalprojcet.domain.auth.service.AuthService;
import com.springboot.finalprojcet.domain.user.repository.UserRepository;
import com.springboot.finalprojcet.entity.Users;
import com.springboot.finalprojcet.enums.RoleType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Override
    public LoginResponseDto login(LoginRequestDto loginRequestDto) {
        Users user = userRepository.findByEmail(loginRequestDto.getEmail())
                .orElseThrow(() -> new RuntimeException("사용자 정보가 존재하지 않습니다."));

        if (!passwordEncoder.matches(loginRequestDto.getPassword(), user.getPassword())) throw new RuntimeException("비밀번호가 일치하지 않습니다.");

        String accessToken = jwtTokenProvider.createAccessToken(user.getEmail(), user.getRoleType().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getEmail(), user.getRoleType().name());

        refreshTokenService.saveRefreshToken(user.getEmail(), refreshToken);

        return LoginResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public LoginResponseDto refresh(String refreshToken) {

        refreshToken = refreshToken.trim().replace("\"", "");

        // 토큰 유효성 검사
        if(!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Refresh 토큰이 일치하지 않습니다.");
        }

        // 토큰에서 이메일 추출
        String email = jwtTokenProvider.getEmail(refreshToken);

        // Redis에 저장된 토큰과 비교
        if(!refreshTokenService.validateRefreshToken(email, refreshToken)) {
            throw new RuntimeException("Refresh 토큰이 일치하지 않습니다.");
        }

        Users user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getEmail(), user.getRoleType().name());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getEmail(), user.getRoleType().name());

        // new Refresh 토큰 redis에 저장
        refreshTokenService.saveRefreshToken(email, newRefreshToken);

        return LoginResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Override
    public void logout(String email) {
        refreshTokenService.deleteRefreshToken(email);
    }

    @Override
    public SignupResponseDto signup(SignupRequestDto signupRequestDto) {
        if (userRepository.existsByEmail(signupRequestDto.getEmail())) throw new RuntimeException("이미 존재하는 이메일입니다.");

        if(!signupRequestDto.getPassword().equals(signupRequestDto.getPasswordConfirm())) throw new RuntimeException("비밀번호가 일치하지 않습니다.");

        Users user = Users.builder()
                .email(signupRequestDto.getEmail())
                .password(passwordEncoder.encode(signupRequestDto.getPassword()))
                .nickname(signupRequestDto.getNickname())
                .roleType(RoleType.USER)
                .build();

        userRepository.save(user);

        return SignupResponseDto.builder()
                .message("회원가입을 성공했습니다.")
                .build();
    }
}
