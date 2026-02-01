package com.springboot.finalprojcet.domain.stats.service;

import com.springboot.finalprojcet.domain.stats.dto.HomeStatsResponseDto;
import com.springboot.finalprojcet.domain.stats.dto.StatsRequestDto;

import java.util.Map;

public interface StatsService {
    void recordView(StatsRequestDto request);

    void recordPlay(StatsRequestDto request);

    void toggleLike(StatsRequestDto request);

    HomeStatsResponseDto getHomeStats();

    Map<String, Object> getBestPlaylists(int limit, String sortBy);

    Map<String, Object> getBestTracks(int limit, String sortBy);

    Map<String, Object> getBestArtists(int limit, String sortBy);

    Map<String, Object> getBestAlbums(int limit);
}
