package com.springboot.finalprojcet.domain.auth.dto.login;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDto {
    private String email;
    private String password;
}
