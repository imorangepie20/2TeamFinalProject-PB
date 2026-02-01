package com.springboot.finalprojcet.domain.auth.dto.sign;

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
public class SignupResponseDto {
    private String message;
    private String token;
    private Object user;
}
