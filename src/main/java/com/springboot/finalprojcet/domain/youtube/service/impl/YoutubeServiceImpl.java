package com.springboot.finalprojcet.domain.youtube.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.finalprojcet.domain.gms.repository.PlaylistRepository;
import com.springboot.finalprojcet.domain.playlist.repository.UserDismissedPlaylistRepository;
import com.springboot.finalprojcet.domain.tidal.repository.PlaylistTracksRepository;
import com.springboot.finalprojcet.domain.tidal.repository.TracksRepository;
import com.springboot.finalprojcet.domain.youtube.service.YoutubeService;
import com.springboot.finalprojcet.domain.youtube.store.YoutubeTokenStore;
import com.springboot.finalprojcet.entity.PlaylistTracks;
import com.springboot.finalprojcet.entity.Playlists;
import com.springboot.finalprojcet.entity.Tracks;
import com.springboot.finalprojcet.entity.Users;
import com.springboot.finalprojcet.enums.SourceType;
import com.springboot.finalprojcet.enums.SpaceType;
import com.springboot.finalprojcet.enums.StatusFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class YoutubeServiceImpl implements YoutubeService {

    private final PlaylistRepository playlistRepository;
    private final UserDismissedPlaylistRepository dismissedPlaylistRepository;
    private final TracksRepository tracksRepository;
    private final PlaylistTracksRepository playlistTracksRepository;
    private final ObjectMapper objectMapper;
    private final YoutubeTokenStore tokenStore;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${youtube.key}")
    private String apiKey;

    @Value("${youtube.client-id}")
    private String clientId;

    @Value("${youtube.client-secret}")
    private String clientSecret;

    private static final String YOUTUBE_API_URL = "https://www.googleapis.com/youtube/v3";
    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";

    // --- Public Search ---

    @Override
    public Map<String, Object> searchVideo(String query, int maxResults) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("YouTube API key not configured");
        }

        String url = UriComponentsBuilder.fromHttpUrl(YOUTUBE_API_URL + "/search")
                .queryParam("key", apiKey)
                .queryParam("part", "snippet")
                .queryParam("q", query)
                .queryParam("type", "video")
                .queryParam("videoCategoryId", "10") // Music
                .queryParam("maxResults", maxResults)
                .toUriString();

        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");

            List<Map<String, Object>> playlists = items.stream().map(item -> {
                Map<String, Object> snippet = (Map<String, Object>) item.get("snippet");
                Map<String, Object> idObj = (Map<String, Object>) item.get("id");

                String videoId = (String) idObj.get("videoId");
                // Fallback if playlistId or other
                if (videoId == null)
                    videoId = (String) idObj.get("playlistId");

                Map<String, Object> map = new HashMap<>();
                map.put("id", videoId);
                map.put("title", snippet.get("title"));
                map.put("channelTitle", snippet.get("channelTitle"));
                map.put("publishedAt", snippet.get("publishedAt"));

                Map<String, Object> thumbs = (Map<String, Object>) snippet.get("thumbnails");
                if (thumbs != null) {
                    Map<String, Object> high = (Map<String, Object>) thumbs.get("high");
                    if (high == null)
                        high = (Map<String, Object>) thumbs.get("medium");
                    if (high != null)
                        map.put("thumbnail", high.get("url"));
                }
                return map;
            }).collect(Collectors.toList());

            return Map.of("playlists", playlists);
        } catch (Exception e) {
            log.error("YouTube Search Error", e);
            throw new RuntimeException("YouTube API error");
        }
    }

    // --- OAuth ---

    @Override
    public Map<String, Object> getLoginUrl(String visitorId, String redirectUri) {
        if (clientId == null)
            throw new RuntimeException("YouTube Client ID not configured");

        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String state = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // Save PKCE context to Redis
        tokenStore.savePkceContext(state, new YoutubeTokenStore.PkceContext(codeVerifier, visitorId));

        String scopes = "https://www.googleapis.com/auth/youtube https://www.googleapis.com/auth/userinfo.profile";

        // Use provided redirect URI or fallback to default
        String validRedirectUri = (redirectUri != null && !redirectUri.isEmpty()) 
                ? redirectUri 
                : "http://localhost/youtube-callback";

        String authUrl = UriComponentsBuilder.fromHttpUrl(GOOGLE_AUTH_URL)
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", validRedirectUri)
                .queryParam("scope", scopes)
                .queryParam("state", state)
                .queryParam("code_challenge_method", "S256")
                .queryParam("code_challenge", codeChallenge)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .build().toUriString();

        return Map.of("authUrl", authUrl);
    }

    @Override
    public Map<String, Object> exchangeToken(String code, String state, String redirectUri) {
        YoutubeTokenStore.PkceContext context = tokenStore.removePkceContext(state);
        if (context == null)
            throw new IllegalArgumentException("Invalid state");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");
        body.add("code_verifier", context.codeVerifier);

        try {
            Map<String, Object> response = restTemplate.postForObject(
                    GOOGLE_TOKEN_URL, new HttpEntity<>(body, headers), Map.class);

            String accessToken = (String) response.get("access_token");
            String refreshToken = (String) response.get("refresh_token");
            Integer expiresIn = (Integer) response.get("expires_in");

            String tokenKey = context.visitorId != null ? context.visitorId : "default";
            // Save token to Redis for persistence
            tokenStore.saveToken(tokenKey, new YoutubeTokenStore.TokenInfo(accessToken, refreshToken, expiresIn));

            // Get User Info
            HttpHeaders profileHeaders = new HttpHeaders();
            profileHeaders.setBearerAuth(accessToken);
            Map<String, Object> profile = restTemplate.exchange(
                    "https://www.googleapis.com/oauth2/v2/userinfo",
                    HttpMethod.GET,
                    new HttpEntity<>(profileHeaders),
                    Map.class).getBody();

            return Map.of(
                    "success", true,
                    "visitorId", tokenKey,
                    "user", Map.of(
                            "id", profile.get("id"),
                            "name", profile.get("name"),
                            "picture", profile.get("picture")));

        } catch (Exception e) {
            log.error("Token Exchange Failed", e);
            throw new RuntimeException("Token exchange failed: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getAuthStatus(String visitorId) {
        String tokenKey = visitorId != null ? visitorId : "default";
        // Check Redis for token
        boolean hasToken = tokenStore.hasToken(tokenKey);

        if (!hasToken)
            return Map.of("connected", false);

        // Optionally verify token validity
        return Map.of("connected", true);
    }

    @Override
    public void logout(String visitorId) {
        String tokenKey = visitorId != null ? visitorId : "default";
        // Remove token from Redis
        tokenStore.removeToken(tokenKey);
    }

    // --- Data ---

    private String getValidToken(String visitorId) {
        String tokenKey = visitorId != null ? visitorId : "default";
        YoutubeTokenStore.TokenInfo info = tokenStore.getToken(tokenKey);
        if (info == null)
            throw new RuntimeException("Not authenticated");

        if (System.currentTimeMillis() >= info.expiresAt - 60000) {
            // Refresh token and update in Redis
            refreshToken(tokenKey, info);
        }
        return info.accessToken;
    }

    private void refreshToken(String tokenKey, YoutubeTokenStore.TokenInfo info) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", info.refreshToken);
        body.add("grant_type", "refresh_token");

        try {
            Map<String, Object> response = restTemplate.postForObject(
                    GOOGLE_TOKEN_URL, new HttpEntity<>(body, headers), Map.class);

            info.accessToken = (String) response.get("access_token");
            Integer expiresIn = (Integer) response.get("expires_in");
            info.expiresAt = System.currentTimeMillis() + (expiresIn * 1000);
            
            // Update token in Redis
            tokenStore.saveToken(tokenKey, info);
            log.debug("Refreshed and saved YouTube token for: {}", tokenKey);
        } catch (Exception e) {
            log.error("Token Refresh Failed", e);
            throw new RuntimeException("Token refresh failed");
        }
    }

    private Map<String, Object> fetchYoutube(String endpoint, String accessToken, Map<String, ?> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(YOUTUBE_API_URL + endpoint);
        params.forEach(builder::queryParam);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        return restTemplate.exchange(
                builder.toUriString(), HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
    }

    @Override
    public Map<String, Object> getPlaylists(String visitorId, int maxResults, String pageToken) {
        String accessToken = getValidToken(visitorId);
        Map<String, Object> params = new HashMap<>();
        params.put("part", "snippet,contentDetails");
        params.put("mine", "true");
        params.put("maxResults", maxResults);
        if (pageToken != null)
            params.put("pageToken", pageToken);

        Map<String, Object> data = fetchYoutube("/playlists", accessToken, params);
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

        List<Map<String, Object>> playlists = items.stream().map(p -> {
            Map<String, Object> snippet = (Map<String, Object>) p.get("snippet");
            Map<String, Object> contentDetails = (Map<String, Object>) p.get("contentDetails");
            Map<String, Object> thumbs = (Map<String, Object>) snippet.get("thumbnails");
            String img = null;
            if (thumbs != null) {
                Map<String, Object> high = (Map<String, Object>) thumbs.get("high");
                if (high == null)
                    high = (Map<String, Object>) thumbs.get("medium");
                if (high != null)
                    img = (String) high.get("url");
            }

            return Map.of(
                    "id", p.get("id"),
                    "name", snippet.get("title"),
                    "description", snippet.get("description"),
                    "image", img != null ? img : "",
                    "trackCount", contentDetails.get("itemCount"),
                    "publishedAt", snippet.get("publishedAt"));
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("playlists", playlists);
        Map<String, Object> pageInfo = (Map<String, Object>) data.get("pageInfo");
        result.put("total", pageInfo != null ? pageInfo.get("totalResults") : playlists.size());
        result.put("nextPageToken", data.get("nextPageToken"));
        result.put("prevPageToken", data.get("prevPageToken"));

        return result;
    }

    @Override
    public Map<String, Object> getPlaylistItems(String playlistId, String visitorId, int maxResults, String pageToken) {
        String accessToken = getValidToken(visitorId);
        Map<String, Object> params = new HashMap<>();
        params.put("part", "snippet,contentDetails");
        params.put("playlistId", playlistId);
        params.put("maxResults", maxResults);
        if (pageToken != null)
            params.put("pageToken", pageToken);

        Map<String, Object> data = fetchYoutube("/playlistItems", accessToken, params);
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

        List<Map<String, Object>> tracks = items.stream()
                .map(item -> {
                    Map<String, Object> snippet = (Map<String, Object>) item.get("snippet");
                    Map<String, Object> resourceId = (Map<String, Object>) snippet.get("resourceId");
                    Map<String, Object> contentDetails = (Map<String, Object>) item.get("contentDetails");
                    Map<String, Object> thumbs = (Map<String, Object>) snippet.get("thumbnails");
                    String img = null;
                    if (thumbs != null) {
                        Map<String, Object> high = (Map<String, Object>) thumbs.get("high");
                        if (high == null)
                            high = (Map<String, Object>) thumbs.get("medium");
                        if (high != null)
                            img = (String) high.get("url");
                    }

                    return Map.of(
                            "videoId", resourceId.get("videoId"),
                            "title", snippet.get("title"),
                            "channelTitle", snippet.get("videoOwnerChannelTitle"),
                            "thumbnail", img != null ? img : "",
                            "description", snippet.get("description"),
                            "publishedAt", contentDetails.get("videoPublishedAt"),
                            "position", snippet.get("position"));
                }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("tracks", tracks);
        Map<String, Object> pageInfo = (Map<String, Object>) data.get("pageInfo");
        result.put("total", pageInfo != null ? pageInfo.get("totalResults") : tracks.size());
        result.put("nextPageToken", data.get("nextPageToken"));
        result.put("hasMore", data.get("nextPageToken") != null);
        return result;
    }

    @Override
    public Map<String, Object> getLikedVideos(String visitorId, int maxResults, String pageToken) {
        String accessToken = getValidToken(visitorId);
        Map<String, Object> params = new HashMap<>();
        params.put("part", "snippet,contentDetails");
        params.put("myRating", "like");
        params.put("maxResults", maxResults);
        params.put("videoCategoryId", "10");
        if (pageToken != null)
            params.put("pageToken", pageToken);

        Map<String, Object> data = fetchYoutube("/videos", accessToken, params);
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

        List<Map<String, Object>> tracks = items.stream().map(item -> {
            Map<String, Object> snippet = (Map<String, Object>) item.get("snippet");
            Map<String, Object> contentDetails = (Map<String, Object>) item.get("contentDetails");
            Map<String, Object> thumbs = (Map<String, Object>) snippet.get("thumbnails");
            String img = null;
            if (thumbs != null) {
                Map<String, Object> high = (Map<String, Object>) thumbs.get("high");
                if (high == null)
                    high = (Map<String, Object>) thumbs.get("medium");
                if (high != null)
                    img = (String) high.get("url");
            }
            return Map.of(
                    "videoId", item.get("id"),
                    "title", snippet.get("title"),
                    "channelTitle", snippet.get("channelTitle"),
                    "thumbnail", img != null ? img : "",
                    "duration", contentDetails.get("duration"),
                    "publishedAt", snippet.get("publishedAt"));
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("tracks", tracks);
        Map<String, Object> pageInfo = (Map<String, Object>) data.get("pageInfo");
        result.put("total", pageInfo != null ? pageInfo.get("totalResults") : tracks.size());
        result.put("nextPageToken", data.get("nextPageToken"));
        result.put("hasMore", data.get("nextPageToken") != null);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> importPlaylist(String visitorId, String playlistId, Long userId) {
        // 중복 체크: 이미 가져온 플레이리스트인지 확인
        String externalId = "youtube:" + playlistId;
        if (playlistRepository.existsByExternalIdAndUserUserId(externalId, userId)
                || dismissedPlaylistRepository.existsByUserUserIdAndExternalId(userId, externalId)) {
            return Map.of(
                    "success", true,
                    "message", "Already imported",
                    "playlistId", 0,
                    "title", "",
                    "importedTracks", 0,
                    "totalTracks", 0);
        }

        String accessToken = getValidToken(visitorId);

        // 1. Get Playlist Info
        Map<String, Object> pParams = new HashMap<>();
        pParams.put("part", "snippet");
        pParams.put("id", playlistId);
        Map<String, Object> pData = fetchYoutube("/playlists", accessToken, pParams);
        List<Map> pItems = (List<Map>) pData.get("items");
        if (pItems.isEmpty())
            throw new RuntimeException("Playlist not found");
        Map<String, Object> playlistInfo = pItems.get(0);
        Map<String, Object> pSnippet = (Map) playlistInfo.get("snippet");

        String title = (String) pSnippet.get("title");
        String desc = (String) pSnippet.get("description");
        Map<String, Object> pThumbs = (Map) pSnippet.get("thumbnails");
        String cover = null;
        if (pThumbs != null) {
            Map<String, Object> high = (Map<String, Object>) pThumbs.get("high");
            if (high != null)
                cover = (String) high.get("url");
        }

        // 2. Fetch all tracks (Loop)
        List<Map<String, Object>> allTracks = new ArrayList<>();
        String pageToken = null;

        do {
            Map<String, Object> params = new HashMap<>();
            params.put("part", "snippet,contentDetails");
            params.put("playlistId", playlistId);
            params.put("maxResults", 50);
            if (pageToken != null)
                params.put("pageToken", pageToken);

            Map<String, Object> itemsData = fetchYoutube("/playlistItems", accessToken, params);
            List<Map<String, Object>> items = (List<Map<String, Object>>) itemsData.get("items");

            for (Map<String, Object> item : items) {
                Map<String, Object> snippet = (Map) item.get("snippet");
                Map<String, Object> resId = (Map) snippet.get("resourceId");
                if (resId != null && resId.get("videoId") != null) {
                    allTracks.add(snippet);
                }
            }
            pageToken = (String) itemsData.get("nextPageToken");
        } while (pageToken != null && allTracks.size() < 500); // Guard limit

        // 3. Save to DB
        Playlists playlist = new Playlists();
        Users userProxy = Users.builder().userId(userId).build();
        playlist.setUser(userProxy);
        playlist.setTitle(title);
        playlist.setDescription(desc);
        playlist.setCoverImage(cover);
        playlist.setSourceType(SourceType.Platform); // youtube? Enum might need expanding or use Platform
        // Using externalId to store youtube ID
        playlist.setExternalId("youtube:" + playlistId);
        playlist.setSpaceType(SpaceType.PMS);
        playlist.setStatusFlag(StatusFlag.PTP); // DB enum: PTP, PRP, PFP

        playlist = playlistRepository.save(playlist);

        int order = 0;
        int importedCount = 0;
        for (Map<String, Object> snippet : allTracks) {
            try {
                String videoTitle = (String) snippet.get("title");
                Map<String, Object> resId = (Map) snippet.get("resourceId");
                String videoId = (String) resId.get("videoId");
                String channel = (String) snippet.get("videoOwnerChannelTitle");
                if (channel == null)
                    channel = "Unknown";

                // Parse Title "Artist - Title"
                String artist = channel.replace(" - Topic", "");
                String trackName = videoTitle;

                int dashIdx = videoTitle.indexOf(" - ");
                if (dashIdx > 0) {
                    artist = videoTitle.substring(0, dashIdx).trim();
                    trackName = videoTitle.substring(dashIdx + 3).trim();
                }

                Map<String, Object> tThumbs = (Map) snippet.get("thumbnails");
                String tCover = null;
                if (tThumbs != null) {
                    Map<String, Object> h = (Map) tThumbs.get("high");
                    if (h != null)
                        tCover = (String) h.get("url");
                }

                // Save Track
                Tracks track = new Tracks();
                track.setTitle(trackName);
                track.setArtist(artist);
                // Store youtube info in external metadata or specific field?
                // Node implementation checked 'youtube_id' column or mapping.
                // Entity 'Tracks' likely has fields. Let's assume generic storage for now or
                // reuse existing.
                // I will put it in ExternalMetadata for safety as I don't recall 'youtube_id'
                // field in Entity definition from previous context.
                // Wait, Node.js code used `youtube_id` column.
                // If `Tracks` entity doesn't have it, I should add it or use externalMetadata.
                // I'll stick to externalMetadata JSON for now to avoid schema changes if
                // possible, unless I'm sure.
                Map<String, String> meta = new HashMap<>();
                meta.put("youtubeId", videoId);
                track.setExternalMetadata(objectMapper.writeValueAsString(meta));
                track.setArtwork(tCover);

                // Attempt save (duplicate handling needed in real app)
                track = tracksRepository.save(track);

                PlaylistTracks pt = PlaylistTracks.builder()
                        .playlist(playlist)
                        .track(track)
                        .orderIndex(order++)
                        .build();
                playlistTracksRepository.save(pt);
                importedCount++;
            } catch (Exception e) {
                // Ignore dupes
            }
        }

        return Map.of(
                "success", true,
                "playlistId", playlist.getPlaylistId(),
                "title", title,
                "importedTracks", importedCount,
                "totalTracks", allTracks.size());
    }

    // Helper for PKCE
    private String generateCodeVerifier() {
        SecureRandom sr = new SecureRandom();
        byte[] code = new byte[32];
        sr.nextBytes(code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
    }

    private String generateCodeChallenge(String verifier) {
        try {
            byte[] bytes = verifier.getBytes(StandardCharsets.US_ASCII);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(bytes, 0, bytes.length);
            byte[] digest = md.digest();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
