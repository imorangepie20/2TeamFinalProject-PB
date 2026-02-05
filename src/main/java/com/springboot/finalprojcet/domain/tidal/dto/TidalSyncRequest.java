package com.springboot.finalprojcet.domain.tidal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TidalSyncRequest {
    private Long userId;
    private TidalAuthData tidalAuthData;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TidalAuthData {
        @com.fasterxml.jackson.annotation.JsonAlias("access_token")
        private String accessToken;

        @com.fasterxml.jackson.annotation.JsonAlias("refresh_token")
        private String refreshToken;

        private Map<String, Object> user;

        // Convenience constructor for accessToken and user map
        public TidalAuthData(String accessToken, Map<String, Object> user) {
            this.accessToken = accessToken;
            this.refreshToken = null;
            this.user = user;
        }
    }
}
