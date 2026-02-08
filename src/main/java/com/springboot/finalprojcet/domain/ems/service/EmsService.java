package com.springboot.finalprojcet.domain.ems.service;

import java.util.Map;

public interface EmsService {
    Map<String, Object> getEmsStats(Long userId);

    Map<String, Object> getRecommendations(Long userId, int limit);

    Map<String, Object> getSpotifySpecial();

    Object exportEmsData(Long userId, String format);

    Object exportPlaylist(Long playlistId, String format);

    Map<String, Object> getPlaylistLinks(Long userId);

    // Migration: Update EMS tracks without duration using Tidal API
    Map<String, Object> migrateEmsTracks();
}
