package com.springboot.finalprojcet.domain.youtube.service;

import java.util.Map;

public interface YoutubeService {
    // Public Search
    Map<String, Object> searchVideo(String query, int maxResults);

    // OAuth
    Map<String, Object> getLoginUrl(String visitorId, String redirectUri);

    Map<String, Object> exchangeToken(String code, String state, String redirectUri);

    Map<String, Object> getAuthStatus(String visitorId);

    void logout(String visitorId);

    // User Data
    Map<String, Object> getPlaylists(String visitorId, int maxResults, String pageToken);

    Map<String, Object> getPlaylistItems(String playlistId, String visitorId, int maxResults, String pageToken);

    Map<String, Object> getLikedVideos(String visitorId, int maxResults, String pageToken);

    // Import
    Map<String, Object> importPlaylist(String visitorId, String playlistId, Long userId);
}
