package com.springboot.finalprojcet.domain.tidal.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TidalTokenPollResponse {
    private boolean success;
    private TidalAuthStatusResponse.TidalUserInfo user;
    private String error;
    private String error_description;
    private String accessToken;
    private String refreshToken;
    private int expiresIn;
}
