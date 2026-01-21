package com.springboot.finalprojcet.domain.auth.dto.login;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LoginResponseDto {
    private String accessToken;
    private String refreshToken;
}
