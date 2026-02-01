package com.springboot.finalprojcet.domain.spotify.service;

import java.util.Map;

public interface SpotifyService {
    Map<String, Object> getLoginUrl(String visitorId);

    Map<String, Object> exchangeToken(String code, String state, String redirectUri);

    // Direct token connection
    Map<String, Object> connectWithToken(String visitorId, String accessToken);

    Map<String, Object> getTokenStatus(String visitorId);

    void disconnectToken(String visitorId);

    // Data
    Map<String, Object> getPlaylists(String visitorId, int limit, int offset);

    Map<String, Object> getPlaylistTracks(String id, String visitorId, int limit, int offset);

    Map<String, Object> importPlaylist(String visitorId, String playlistId, Long userId);

    // Search & Audio Features (Client Credentials)
    Map<String, Object> searchTrackByIsrc(String isrc);

    Map<String, Object> getAudioFeatures(String isrc);
}
