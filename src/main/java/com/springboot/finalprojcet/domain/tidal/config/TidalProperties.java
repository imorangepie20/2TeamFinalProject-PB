package com.springboot.finalprojcet.domain.tidal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tidal")
@Getter
@Setter
public class TidalProperties {
    private String clientId;
    private String clientSecret;
    private String authUrl;
    private String apiUrl;
    private String redirectUri;
}
