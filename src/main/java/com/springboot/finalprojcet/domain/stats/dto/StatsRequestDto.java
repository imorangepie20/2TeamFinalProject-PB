package com.springboot.finalprojcet.domain.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StatsRequestDto {
    private String contentType; // playlist, track, album, artist
    private Long contentId; // for non-artist
    private String artistName; // for artist or track's artist
    private Boolean isLiked; // for like toggle
}
