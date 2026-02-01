package com.springboot.finalprojcet.domain.tidal.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TidalDeviceAuthResponse {
    private String deviceCode;
    private String userCode;
    private String verificationUri;
    private int expiresIn;
    private int interval;
}
