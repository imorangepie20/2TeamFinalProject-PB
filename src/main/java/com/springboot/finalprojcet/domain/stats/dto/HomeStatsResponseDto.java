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
public class HomeStatsResponseDto {
    private long totalPlaylists;
    private long totalTracks;
    private long aiPending;
    private long likes;
}
