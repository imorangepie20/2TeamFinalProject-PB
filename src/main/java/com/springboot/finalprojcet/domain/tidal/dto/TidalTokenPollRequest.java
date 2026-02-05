package com.springboot.finalprojcet.domain.tidal.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TidalTokenPollRequest {
    private String deviceCode;
    private String visitorId;
}
