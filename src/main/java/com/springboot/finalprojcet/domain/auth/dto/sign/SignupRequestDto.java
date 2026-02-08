package com.springboot.finalprojcet.domain.auth.dto.sign;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SignupRequestDto {
    @NotBlank(message = "이메일을 입력해주세요")
    private String email;

    @NotBlank(message = "비밀번호를 입력해주세요")
    private String password;

    @NotBlank(message = "이름을 입력해주세요")
    private String name;

    private java.util.List<String> streamingServices;
    private java.util.List<String> genres;

// Tidal 연동 관련 필드
    private Boolean tidalConnected;
    private String tidalVisitorId;
    private String tidalAccessToken;
    private String tidalRefreshToken;

    // AI 모델 설정 (M1, M2, M3)
    private String model;
}
