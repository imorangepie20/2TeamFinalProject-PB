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
import com.springboot.finalprojcet.domain.user.repository.MusicGenresRepository;
import com.springboot.finalprojcet.domain.user.repository.UserGenresRepository;
import com.springboot.finalprojcet.entity.UserGenres;
import com.springboot.finalprojcet.enums.RoleType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final MusicGenresRepository musicGenresRepository;
    private final UserGenresRepository userGenresRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Override
    public LoginResponseDto login(LoginRequestDto loginRequestDto) {
        Users user = userRepository.findByEmail(loginRequestDto.getEmail())
                .orElseThrow(() -> new RuntimeException("사용자 정보가 존재하지 않습니다."));

        if (!passwordEncoder.matches(loginRequestDto.getPassword(), user.getPassword()))
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");

        String accessToken = jwtTokenProvider.createAccessToken(user.getEmail(), user.getRoleType().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getEmail(), user.getRoleType().name());

        refreshTokenService.saveRefreshToken(user.getEmail(), refreshToken);

        return LoginResponseDto.builder()
                .message("로그인 성공")
                .token(accessToken)
                .user(java.util.Map.of(
                        "id", user.getUserId(),
                        "email", user.getEmail(),
                        "name", user.getNickname()))
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public LoginResponseDto refresh(String refreshToken) {

        refreshToken = refreshToken.trim().replace("\"", "");

        // 토큰 유효성 검사
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Refresh 토큰이 일치하지 않습니다.");
        }

        // 토큰에서 이메일 추출
        String email = jwtTokenProvider.getEmail(refreshToken);

        // Redis에 저장된 토큰과 비교
        if (!refreshTokenService.validateRefreshToken(email, refreshToken)) {
            throw new RuntimeException("Refresh 토큰이 일치하지 않습니다.");
        }

        Users user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getEmail(), user.getRoleType().name());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getEmail(), user.getRoleType().name());

        // new Refresh 토큰 redis에 저장
        refreshTokenService.saveRefreshToken(email, newRefreshToken);

        return LoginResponseDto.builder()
                .message("토큰 갱신 성공")
                .token(newAccessToken)
                .user(java.util.Map.of(
                        "id", user.getUserId(),
                        "email", user.getEmail(),
                        "name", user.getNickname()))
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
        if (userRepository.existsByEmail(signupRequestDto.getEmail()))
            throw new RuntimeException("이미 존재하는 이메일입니다.");

        // Streaming Services -> JSON String
        String streamingServicesJson = "[]";
        try {
            if (signupRequestDto.getStreamingServices() != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                streamingServicesJson = mapper.writeValueAsString(signupRequestDto.getStreamingServices());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Users user = Users.builder()
                .email(signupRequestDto.getEmail())
                .password(passwordEncoder.encode(signupRequestDto.getPassword()))
                .nickname(signupRequestDto.getName())
                .roleType(RoleType.USER)
                .streamingServices(streamingServicesJson)
                .build();

        userRepository.save(user);

        // Genres handling
        if (signupRequestDto.getGenres() != null && !signupRequestDto.getGenres().isEmpty()) {
            for (String genreCode : signupRequestDto.getGenres()) {
                musicGenresRepository.findByGenreCode(genreCode).ifPresent(genre -> {
                    UserGenres userGenre = UserGenres.builder()
                            .user(user)
                            .genre(genre)
                            .preferenceLevel(1)
                            .build();
                    userGenresRepository.save(userGenre);
                });
            }
        }

        // Generate Hash (Immediate Login effect)
        String accessToken = jwtTokenProvider.createAccessToken(user.getEmail(), user.getRoleType().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getEmail(), user.getRoleType().name());
        refreshTokenService.saveRefreshToken(user.getEmail(), refreshToken);

        // Build User Response Object
        java.util.Map<String, Object> userMap = new java.util.HashMap<>();
        userMap.put("id", user.getUserId());
        userMap.put("email", user.getEmail());
        userMap.put("name", user.getNickname());
        userMap.put("streamingServices",
                signupRequestDto.getStreamingServices() != null ? signupRequestDto.getStreamingServices()
                        : new java.util.ArrayList<>());
        userMap.put("genres",
                signupRequestDto.getGenres() != null ? signupRequestDto.getGenres() : new java.util.ArrayList<>());

        return SignupResponseDto.builder()
                .message("회원가입이 완료되었습니다")
                .token(accessToken) // Return Access Token
                .user(userMap)
                .build();
    }
}
