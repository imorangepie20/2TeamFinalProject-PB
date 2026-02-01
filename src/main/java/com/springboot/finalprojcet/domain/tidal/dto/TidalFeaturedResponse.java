package com.springboot.finalprojcet.domain.tidal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TidalFeaturedResponse {
    private List<FeaturedGroup> featured;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FeaturedGroup {
        private String genre;
        private List<TidalPlaylist> playlists;
    }
}
