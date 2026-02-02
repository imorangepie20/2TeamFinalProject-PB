package com.springboot.finalprojcet.domain.tidal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TidalSearchResponse {
    private List<TidalPlaylist> playlists;
    private List<TidalTrack> tracks;
}
