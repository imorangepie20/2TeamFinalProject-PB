package com.springboot.finalprojcet.domain.tidal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TidalExchangeResponse {
    private boolean success;
    private TidalAuthStatusResponse.TidalUserInfo user;
    private String visitorId;
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private String error;
}
