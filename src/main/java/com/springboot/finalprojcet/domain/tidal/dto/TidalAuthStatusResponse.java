package com.springboot.finalprojcet.domain.tidal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TidalAuthStatusResponse {
    private boolean authenticated;
    private boolean userConnected;
    private String type;
    private TidalUserInfo user;
    private String error;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TidalUserInfo {
        private String userId;
        private String countryCode;
        private String username;
    }
}
