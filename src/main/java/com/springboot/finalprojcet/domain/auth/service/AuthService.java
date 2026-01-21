package com.springboot.finalprojcet.domain.auth.service;

import com.springboot.finalprojcet.domain.auth.dto.login.LoginRequestDto;
import com.springboot.finalprojcet.domain.auth.dto.login.LoginResponseDto;
import com.springboot.finalprojcet.domain.auth.dto.sign.SignupRequestDto;
import com.springboot.finalprojcet.domain.auth.dto.sign.SignupResponseDto;

public interface AuthService {
    // 로그인
    LoginResponseDto login(LoginRequestDto loginRequestDto);
    // 토큰 갱신
    LoginResponseDto refresh(String refreshToken);
    // 로그아웃
    void logout(String email);
    // 회원가입
    SignupResponseDto signup(SignupRequestDto signupRequestDto);
}
