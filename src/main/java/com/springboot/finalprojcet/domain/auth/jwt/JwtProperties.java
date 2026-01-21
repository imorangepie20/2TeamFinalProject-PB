package com.springboot.finalprojcet.domain.auth.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt") // application-dev.properties에서 jwt.으로 시작하는 설정값을 자동으로 매핑해줌
public class JwtProperties {
    private String secret; // jwt.secret
    private Long accessTokenExpiration; // accessTokenExpiration
    private Long refreshTokenExpiration; // refreshTokenExpiration
}
