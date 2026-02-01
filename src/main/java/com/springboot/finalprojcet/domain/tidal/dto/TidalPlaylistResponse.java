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
public class TidalPlaylistResponse {
    private List<TidalPlaylistItem> playlists;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TidalPlaylistItem {
        private String uuid;
        private String title;
        private Integer numberOfTracks;
        private Integer trackCount;
        private String image;
        private String description;
    }
}
