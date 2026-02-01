package com.springboot.finalprojcet.domain.tidal.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TidalSyncRequest {
    private TidalAuthData tidalAuthData;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TidalAuthData {
        @com.fasterxml.jackson.annotation.JsonAlias("access_token")
        private String accessToken;

        @com.fasterxml.jackson.annotation.JsonAlias("refresh_token")
        private String refreshToken;

        private Map<String, Object> user;
    }
}
