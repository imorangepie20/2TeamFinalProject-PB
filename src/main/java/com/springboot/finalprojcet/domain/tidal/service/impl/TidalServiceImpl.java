package com.springboot.finalprojcet.domain.tidal.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.springboot.finalprojcet.domain.gms.repository.PlaylistRepository;
import com.springboot.finalprojcet.domain.tidal.config.TidalProperties;
import com.springboot.finalprojcet.domain.tidal.dto.*;
import com.springboot.finalprojcet.domain.tidal.repository.PlaylistTracksRepository;
import com.springboot.finalprojcet.domain.tidal.repository.TracksRepository;
import com.springboot.finalprojcet.domain.tidal.service.TidalService;
import com.springboot.finalprojcet.domain.tidal.store.TidalTokenStore;
import com.springboot.finalprojcet.domain.user.repository.UserRepository;
import com.springboot.finalprojcet.entity.PlaylistTracks;
import com.springboot.finalprojcet.entity.Playlists;
import com.springboot.finalprojcet.entity.Tracks;
import com.springboot.finalprojcet.entity.Users;
import com.springboot.finalprojcet.enums.SourceType;
import com.springboot.finalprojcet.enums.SpaceType;
import com.springboot.finalprojcet.enums.StatusFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TidalServiceImpl implements TidalService {

    private final TidalProperties tidalProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;
    private final TracksRepository tracksRepository;
    private final PlaylistTracksRepository playlistTracksRepository;
    private final TidalTokenStore tokenStore;

    @Override
    public TidalLoginUrlResponse getLoginUrl(String visitorId, String origin) {
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        if (visitorId != null && !visitorId.isEmpty()) {
            // Save PKCE verifier to Redis
            tokenStore.savePkceVerifier(visitorId, codeVerifier);
        }

        String redirectUri = resolveRedirectUri(origin);
        // Use new OAuth2 scopes only (mixing old and new causes error 1002)
        String scopes = "user.read playlists.read playlists.write collection.read";

        String authUrl = String.format(
                "https://login.tidal.com/authorize?response_type=code&client_id=%s&redirect_uri=%s&scope=%s&code_challenge=%s&code_challenge_method=S256",
                tidalProperties.getClientId(),
                URLEncoder.encode(redirectUri, StandardCharsets.UTF_8),
                URLEncoder.encode(scopes, StandardCharsets.UTF_8),
                codeChallenge);

        return TidalLoginUrlResponse.builder().authUrl(authUrl).build();
    }

    @Override
    public TidalExchangeResponse exchangeCode(TidalExchangeRequest request, String origin) {
        try {
            String codeVerifier = null;
            if (request.getVisitorId() != null) {
                // Get and remove PKCE verifier from Redis
                codeVerifier = tokenStore.removePkceVerifier(request.getVisitorId());
            }

            if (codeVerifier == null) {
                return TidalExchangeResponse.builder()
                        .success(false)
                        .error("PKCE verifier not found. Please restart login flow.")
                        .build();
            }

            String redirectUri = request.getRedirectUri() != null ? request.getRedirectUri()
                    : resolveRedirectUri(origin);
            String credentials = Base64.getEncoder().encodeToString(
                    (tidalProperties.getClientId() + ":" + tidalProperties.getClientSecret()).getBytes());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Authorization", "Basic " + credentials);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "authorization_code");
            body.add("code", request.getCode());
            body.add("redirect_uri", redirectUri);
            body.add("code_verifier", codeVerifier);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    tidalProperties.getAuthUrl() + "/token", HttpMethod.POST, entity, JsonNode.class);

            JsonNode tokenData = response.getBody();
            if (tokenData == null || tokenData.has("error")) {
                String error = tokenData != null
                        ? tokenData.path("error_description").asText(tokenData.path("error").asText())
                        : "Token exchange failed";
                return TidalExchangeResponse.builder().success(false).error(error).build();
            }

            String accessToken = tokenData.path("access_token").asText();
            String refreshToken = tokenData.path("refresh_token").asText();
            long expiresIn = tokenData.path("expires_in").asLong();

            // Get session info
            TidalAuthStatusResponse.TidalUserInfo userInfo = fetchSessionInfo(accessToken);

            // Store token to Redis for persistence
            if (request.getVisitorId() != null) {
                TidalTokenStore.TokenInfo tokenInfo = new TidalTokenStore.TokenInfo(
                        accessToken, refreshToken, expiresIn,
                        userInfo.getUserId(), userInfo.getCountryCode(), userInfo.getUsername());
                tokenStore.saveToken(request.getVisitorId(), tokenInfo);
            }

            return TidalExchangeResponse.builder()
                    .success(true)
                    .user(userInfo)
                    .visitorId(request.getVisitorId())
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(expiresIn)
                    .build();
        } catch (Exception e) {
            log.error("Exchange error", e);
            return TidalExchangeResponse.builder().success(false).error(e.getMessage()).build();
        }
    }

    @Override
    public TidalAuthStatusResponse getAuthStatus(String visitorId) {
        if (visitorId != null) {
            TidalTokenStore.TokenInfo tokenInfo = tokenStore.getToken(visitorId);
            if (tokenInfo != null) {
                if (System.currentTimeMillis() < tokenInfo.expiresAt) {
                    return TidalAuthStatusResponse.builder()
                            .authenticated(true)
                            .userConnected(true)
                            .user(TidalAuthStatusResponse.TidalUserInfo.builder()
                                    .userId(tokenInfo.userId)
                                    .countryCode(tokenInfo.countryCode)
                                    .username(tokenInfo.username)
                                    .build())
                            .build();
                } else {
                    // Token expired, remove from Redis
                    tokenStore.removeToken(visitorId);
                }
            }
        }

        return TidalAuthStatusResponse.builder()
                .authenticated(false)
                .userConnected(false)
                .type("None")
                .build();
    }

    @Override
    public void logout(String visitorId) {
        if (visitorId != null) {
            // Remove token from Redis
            tokenStore.removeToken(visitorId);
        }
    }

    @Override
    public TidalPlaylistResponse getUserPlaylists(String visitorId) {
        TidalTokenStore.TokenInfo tokenInfo = getValidToken(visitorId);
        if (tokenInfo == null) {
            return TidalPlaylistResponse.builder().playlists(Collections.emptyList()).build();
        }

        try {
            List<JsonNode> playlists = fetchTidalPlaylists(tokenInfo.accessToken, tokenInfo.userId,
                    tokenInfo.countryCode);
            List<TidalPlaylistResponse.TidalPlaylistItem> items = new ArrayList<>();

            for (JsonNode p : playlists) {
                String squareImage = p.path("squareImage").asText(null);
                String image = squareImage != null
                        ? "https://resources.tidal.com/images/" + squareImage.replace("-", "/") + "/320x320.jpg"
                        : null;

                items.add(TidalPlaylistResponse.TidalPlaylistItem.builder()
                        .uuid(p.path("uuid").asText())
                        .title(p.path("title").asText())
                        .numberOfTracks(p.path("numberOfTracks").asInt(0))
                        .trackCount(p.path("numberOfTracks").asInt(0))
                        .image(image)
                        .description(p.path("description").asText(null))
                        .build());
            }

            return TidalPlaylistResponse.builder().playlists(items).build();
        } catch (Exception e) {
            log.error("Failed to fetch playlists", e);
            return TidalPlaylistResponse.builder().playlists(Collections.emptyList()).build();
        }
    }

    @Override
    @Transactional
    public TidalImportResponse importPlaylist(TidalImportRequest request) {
        if (request.getUserId() == null) {
            return TidalImportResponse.builder().success(false).error("userId is required").build();
        }

        TidalTokenStore.TokenInfo tokenInfo = getValidToken(request.getVisitorId());
        if (tokenInfo == null) {
            return TidalImportResponse.builder().success(false).error("Not authenticated").build();
        }

        try {
            // Check for duplicate import
            String externalId = "tidal:" + request.getPlaylistId();
            if (playlistRepository.existsByExternalIdAndUserUserId(externalId, request.getUserId())) {
                log.info("[Tidal] Playlist already imported: {}", request.getPlaylistId());
                return TidalImportResponse.builder()
                        .success(true)
                        .message("Already imported")
                        .playlistId(0L)
                        .title("")
                        .importedTracks(0)
                        .totalTracks(0)
                        .build();
            }

            String countryCode = tokenInfo.countryCode != null ? tokenInfo.countryCode : "KR";

            // 1. Get playlist info
            JsonNode playlist = fetchPlaylistInfo(tokenInfo.accessToken, request.getPlaylistId(), countryCode);
            if (playlist == null) {
                return TidalImportResponse.builder().success(false).error("Failed to fetch playlist info").build();
            }

            // 2. Get playlist tracks
            List<JsonNode> tracks = fetchPlaylistTracks(tokenInfo.accessToken, request.getPlaylistId(), countryCode);

            log.info("[Tidal] Importing playlist \"{}\" with {} tracks", playlist.path("title").asText(),
                    tracks.size());

            // 3. Create playlist in DB
            Users user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String squareImage = playlist.path("squareImage").asText(null);
            String coverImage = squareImage != null
                    ? "https://resources.tidal.com/images/" + squareImage.replace("-", "/") + "/320x320.jpg"
                    : null;

            Playlists newPlaylist = Playlists.builder()
                    .user(user)
                    .title(playlist.path("title").asText())
                    .description(playlist.path("description").asText(""))
                    .coverImage(coverImage)
                    .sourceType(SourceType.Platform)
                    .externalId(externalId)
                    .spaceType(SpaceType.PMS)
                    .statusFlag(StatusFlag.Active)
                    .build();

            playlistRepository.save(newPlaylist);

            // 4. Insert tracks
            int importedCount = 0;
            for (int i = 0; i < tracks.size(); i++) {
                JsonNode item = tracks.get(i);
                JsonNode track = item.has("item") ? item.path("item") : item;

                if (!track.has("title"))
                    continue;

                try {
                    String tidalId = track.path("id").asText();
                    String artist = track.path("artist").path("name").asText(
                            track.path("artists").path(0).path("name").asText("Unknown"));
                    String albumCover = track.path("album").path("cover").asText(null);
                    String artwork = albumCover != null
                            ? "https://resources.tidal.com/images/" + albumCover.replace("-", "/") + "/320x320.jpg"
                            : null;

                    // Check if track exists
                    Tracks existingTrack = tracksRepository.findByTidalId(tidalId).orElse(null);
                    Tracks trackEntity;

                    if (existingTrack != null) {
                        trackEntity = existingTrack;
                    } else {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("tidalId", tidalId);
                        metadata.put("isrc", track.path("isrc").asText(null));

                        trackEntity = Tracks.builder()
                                .title(track.path("title").asText())
                                .artist(artist)
                                .album(track.path("album").path("title").asText("Unknown"))
                                .duration(track.path("duration").asInt(0))
                                .externalMetadata(objectMapper.writeValueAsString(metadata))
                                .artwork(artwork)
                                .build();

                        tracksRepository.save(trackEntity);
                    }

                    PlaylistTracks pt = PlaylistTracks.builder()
                            .playlist(newPlaylist)
                            .track(trackEntity)
                            .orderIndex(i)
                            .build();
                    playlistTracksRepository.save(pt);

                    importedCount++;
                } catch (Exception e) {
                    log.error("[Tidal] Failed to import track: {}", e.getMessage());
                }
            }

            log.info("[Tidal] Import complete: {}/{} tracks", importedCount, tracks.size());

            return TidalImportResponse.builder()
                    .success(true)
                    .playlistId(newPlaylist.getPlaylistId())
                    .title(newPlaylist.getTitle())
                    .importedTracks(importedCount)
                    .totalTracks(tracks.size())
                    .build();
        } catch (Exception e) {
            log.error("[Tidal] Import error", e);
            return TidalImportResponse.builder().success(false).error(e.getMessage()).build();
        }
    }

    @Override
    @Transactional
    public TidalSyncResponse syncTidal(Long userId, TidalSyncRequest request) {
        if (request.getTidalAuthData() == null || request.getTidalAuthData().getAccessToken() == null) {
            log.error("[Sync] Missing Tidal auth data. Request: {}", request);
            return TidalSyncResponse.builder().success(false).error("Tidal 인증 데이터가 필요합니다").build();
        }

        try {
            String token = request.getTidalAuthData().getAccessToken();
            String providedUserId = null;
            if (request.getTidalAuthData().getUser() != null) {
                Object uid = request.getTidalAuthData().getUser().get("userId");
                if (uid == null)
                    uid = request.getTidalAuthData().getUser().get("id");
                if (uid != null)
                    providedUserId = uid.toString();
            }

            List<JsonNode> playlists = fetchTidalPlaylists(token, providedUserId, "KR");
            log.info("[Sync] Found {} playlists for user {}", playlists.size(), userId);

            Users user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            int syncedCount = 0;
            for (JsonNode p : playlists) {
                try {
                    String playlistUuid = p.path("uuid").asText();
                    String externalId = "tidal:" + playlistUuid;

                    // Skip if already imported
                    if (playlistRepository.existsByExternalIdAndUserUserId(externalId, userId)) {
                        log.info("[Sync] Skipping already imported playlist: {}", playlistUuid);
                        continue;
                    }

                    String squareImage = p.path("squareImage").asText(null);
                    String coverImage = squareImage != null
                            ? "https://resources.tidal.com/images/" + squareImage.replace("-", "/") + "/320x320.jpg"
                            : null;

                    Playlists playlist = Playlists.builder()
                            .user(user)
                            .title(p.path("title").asText())
                            .description(p.path("description").asText("Tidal Playlist"))
                            .spaceType(SpaceType.PMS)
                            .statusFlag(StatusFlag.Active)
                            .sourceType(SourceType.Platform)
                            .externalId(externalId)
                            .coverImage(coverImage)
                            .build();

                    playlistRepository.save(playlist);

                    // Fetch and save tracks
                    List<JsonNode> tracks = fetchPlaylistTracks(token, p.path("uuid").asText(), "KR");
                    for (int i = 0; i < tracks.size(); i++) {
                        JsonNode item = tracks.get(i);
                        JsonNode track = item.has("item") ? item.path("item") : item;

                        if (!track.has("title") && !track.has("name"))
                            continue;

                        try {
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("tidalId", track.path("id").asText());
                            metadata.put("isrc", track.path("isrc").asText(null));

                            Tracks trackEntity = Tracks.builder()
                                    .title(track.path("title").asText(track.path("name").asText()))
                                    .artist(track.path("artist").path("name").asText(
                                            track.path("artists").path(0).path("name").asText("Unknown")))
                                    .album(track.path("album").path("title").asText("Unknown"))
                                    .duration(track.path("duration").asInt(0))
                                    .externalMetadata(objectMapper.writeValueAsString(metadata))
                                    .build();

                            tracksRepository.save(trackEntity);

                            PlaylistTracks pt = PlaylistTracks.builder()
                                    .playlist(playlist)
                                    .track(trackEntity)
                                    .orderIndex(i)
                                    .build();
                            playlistTracksRepository.save(pt);
                        } catch (Exception e) {
                            log.error("[Sync] Track insert failed: {}", e.getMessage());
                        }
                    }
                    syncedCount++;
                } catch (Exception e) {
                    log.error("[Sync] Playlist insert failed: {}", e.getMessage());
                }
            }

            return TidalSyncResponse.builder().success(true).syncedCount(syncedCount).build();
        } catch (Exception e) {
            log.error("Tidal Sync Error", e);
            return TidalSyncResponse.builder().success(false).error("동기화 중 오류가 발생했습니다: " + e.getMessage()).build();
        }
    }

    @Override
    public TidalDeviceAuthResponse initDeviceAuth() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", tidalProperties.getClientId());
            body.add("scope", "user.read playlists.read playlists.write collection.read");

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
            // Tidal Auth Base URL usually ends with /oauth2 or similar.
            // We assume tidalProperties.getAuthUrl() is "https://auth.tidal.com/v1/oauth2"
            String url = tidalProperties.getAuthUrl() + "/device_authorization";

            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, entity, JsonNode.class);
            JsonNode data = response.getBody();

            if (data == null || data.has("error")) {
                throw new RuntimeException("Device auth init failed");
            }

            return TidalDeviceAuthResponse.builder()
                    .deviceCode(data.path("deviceCode").asText())
                    .userCode(data.path("userCode").asText())
                    .verificationUri(data.path("verificationUri").asText())
                    .expiresIn(data.path("expiresIn").asInt())
                    .interval(data.path("interval").asInt())
                    .build();
        } catch (Exception e) {
            log.error("Device Auth Init Error", e);
            throw new RuntimeException("Failed to init device auth: " + e.getMessage());
        }
    }

    @Override
    public TidalTokenPollResponse pollToken(TidalTokenPollRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String credentials = Base64.getEncoder().encodeToString(
                    (tidalProperties.getClientId() + ":" + tidalProperties.getClientSecret()).getBytes());
            headers.set("Authorization", "Basic " + credentials);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", tidalProperties.getClientId());
            body.add("device_code", request.getDeviceCode());
            body.add("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            body.add("scope", "user.read playlists.read playlists.write collection.read");

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
            String url = tidalProperties.getAuthUrl() + "/token";

            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, entity, JsonNode.class);
            JsonNode data = response.getBody();

            if (data == null) {
                return TidalTokenPollResponse.builder().success(false).error("Empty response").build();
            }

            if (data.has("error")) {
                String error = data.path("error").asText();
                return TidalTokenPollResponse.builder()
                        .success(false)
                        .error(error)
                        .error_description(data.path("error_description").asText())
                        .build();
            }

            String accessToken = data.path("access_token").asText();
            TidalAuthStatusResponse.TidalUserInfo userInfo = fetchSessionInfo(accessToken);

            return TidalTokenPollResponse.builder()
                    .success(true)
                    .user(userInfo)
                    .accessToken(data.path("access_token").asText())
                    .refreshToken(data.path("refresh_token").asText(null))
                    .expiresIn(data.path("expires_in").asInt(0))
                    .build();

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            try {
                JsonNode errorBody = objectMapper.readTree(e.getResponseBodyAsString());
                String error = errorBody.path("error").asText();
                return TidalTokenPollResponse.builder()
                        .success(false)
                        .error(error)
                        .error_description(errorBody.path("error_description").asText())
                        .build();
            } catch (Exception ex) {
                return TidalTokenPollResponse.builder().success(false).error(e.getMessage()).build();
            }
        } catch (Exception e) {
            log.error("Token Poll Error", e);
            return TidalTokenPollResponse.builder().success(false).error(e.getMessage()).build();
        }
    }

    // --- Helper Methods ---

    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate code challenge", e);
        }
    }

    private String resolveRedirectUri(String origin) {
        return tidalProperties.getRedirectUri();
    }

    private TidalTokenStore.TokenInfo getValidToken(String visitorId) {
        if (visitorId == null)
            return null;
        TidalTokenStore.TokenInfo tokenInfo = tokenStore.getToken(visitorId);
        if (tokenInfo == null || System.currentTimeMillis() >= tokenInfo.expiresAt) {
            if (tokenInfo != null)
                tokenStore.removeToken(visitorId);
            return null;
        }
        return tokenInfo;
    }

    private TidalAuthStatusResponse.TidalUserInfo fetchSessionInfo(String accessToken) {
        // First, try to extract userId from JWT token directly
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                JsonNode jwt = objectMapper.readTree(payload);

                // JWT contains "uid" (user id) and "cc" (country code)
                String userId = jwt.has("uid") ? String.valueOf(jwt.path("uid").asLong()) : null;
                String countryCode = jwt.path("cc").asText(null);

                if (userId != null && !userId.isEmpty() && !userId.equals("0")) {
                    log.info("[Tidal] Extracted from JWT - userId: {}, countryCode: {}", userId, countryCode);
                    return TidalAuthStatusResponse.TidalUserInfo.builder()
                            .userId(userId)
                            .countryCode(countryCode)
                            .username("Tidal User")
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("[Tidal] Failed to parse JWT: {}", e.getMessage());
        }

        // Fallback: try API endpoints
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.tidal.v1+json");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String[] endpoints = { "/users/me", "/me", "/sessions" };

        for (String endpoint : endpoints) {
            try {
                ResponseEntity<JsonNode> response = restTemplate.exchange(
                        tidalProperties.getApiUrl() + endpoint,
                        HttpMethod.GET, entity, JsonNode.class);

                JsonNode data = response.getBody();
                log.info("[Tidal] {} response: {}", endpoint, data);

                if (data != null) {
                    String userId = null;
                    if (data.has("userId")) {
                        userId = data.path("userId").asText();
                    } else if (data.has("user_id")) {
                        userId = data.path("user_id").asText();
                    } else if (data.has("id")) {
                        userId = data.path("id").asText();
                    }

                    String countryCode = data.path("countryCode").asText(
                            data.path("country_code").asText(
                                    data.path("country").asText(null)));

                    String username = data.path("username").asText(
                            data.path("name").asText(
                                    data.path("firstName").asText("Tidal User")));

                    if (userId != null && !userId.isEmpty()) {
                        log.info("[Tidal] Found user info - userId: {}, countryCode: {}, username: {}",
                                userId, countryCode, username);
                        return TidalAuthStatusResponse.TidalUserInfo.builder()
                                .userId(userId)
                                .countryCode(countryCode)
                                .username(username)
                                .build();
                    }
                }
            } catch (Exception e) {
                log.warn("[Tidal] {} failed: {}", endpoint, e.getMessage());
            }
        }

        log.warn("[Tidal] Could not fetch user info from any endpoint");
        return TidalAuthStatusResponse.TidalUserInfo.builder().username("Tidal User").build();
    }

    private List<JsonNode> fetchTidalPlaylists(String token, String tidalUserId, String countryCode) {
        try {
            if (tidalUserId == null) {
                TidalAuthStatusResponse.TidalUserInfo info = fetchSessionInfo(token);
                tidalUserId = info.getUserId();
                if (countryCode == null || countryCode.equals("KR")) {
                    countryCode = info.getCountryCode() != null ? info.getCountryCode() : "US";
                }
            }

            if (tidalUserId == null) {
                log.error("[Tidal] Failed to resolve user identity");
                return Collections.emptyList();
            }

            // Try alternative APIs first (works with new OAuth2 scopes)
            List<JsonNode> altResult = fetchPlaylistsViaAlternativeApis(token, tidalUserId, countryCode);
            if (!altResult.isEmpty()) {
                return altResult;
            }

            // Fallback to v1 API (requires r_usr scope - may fail with new OAuth2)
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Accept", "application/vnd.tidal.v1+json");

            String[] endpoints = {
                    "/users/" + tidalUserId + "/playlists",
                    "/users/" + tidalUserId + "/favorites/playlists"
            };

            for (String endpoint : endpoints) {
                try {
                    String url = tidalProperties.getApiUrl() + endpoint + "?countryCode=" + countryCode + "&limit=50";
                    log.info("[Tidal] Trying v1 endpoint: {}", url);
                    HttpEntity<Void> entity = new HttpEntity<>(headers);
                    ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity,
                            JsonNode.class);

                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        JsonNode data = response.getBody();
                        JsonNode items = data.has("items") ? data.path("items") : data.path("data");
                        if (items.isArray() && items.size() > 0) {
                            log.info("[Tidal] Found {} playlists via v1 {}", items.size(), endpoint);
                            List<JsonNode> result = new ArrayList<>();
                            items.forEach(result::add);
                            return result;
                        } else {
                            log.info("[Tidal] v1 endpoint {} returned empty playlists", endpoint);
                        }
                    }
                } catch (org.springframework.web.client.HttpClientErrorException e) {
                    log.warn("[Tidal] v1 Endpoint {} returned {}: {}", endpoint, e.getStatusCode(),
                            e.getResponseBodyAsString());
                } catch (Exception e) {
                    log.warn("[Tidal] v1 Endpoint {} failed: {}", endpoint, e.getMessage());
                }
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[Tidal] fetchTidalPlaylists error", e);
            return Collections.emptyList();
        }
    }

    /**
     * Fetch playlists via multiple API approaches (try different endpoints)
     * Updated for Tidal API v2 with proper headers and endpoint patterns
     */
    private List<JsonNode> fetchPlaylistsViaAlternativeApis(String token, String tidalUserId, String countryCode) {
        // Try multiple endpoint patterns - prioritize official OpenAPI v2 endpoints
        String[][] endpointConfigs = {
                // {url, accept header, use client-id header}
                // Official Tidal OpenAPI v2 endpoints
                { "https://openapi.tidal.com/v2/me/playlists?countryCode=" + countryCode + "&limit=50",
                        "application/vnd.api+json", "true" },
                { "https://openapi.tidal.com/v2/playlists/me?countryCode=" + countryCode + "&limit=50",
                        "application/vnd.api+json", "true" },
                // Alternative API base URLs
                { "https://api.tidal.com/v2/me/playlists?countryCode=" + countryCode + "&limit=50", "application/json",
                        "true" },
                { "https://api.tidal.com/v2/my-collection/playlists?countryCode=" + countryCode + "&limit=50",
                        "application/json", "true" },
                { "https://api.tidal.com/v2/users/" + tidalUserId + "/playlists?countryCode=" + countryCode
                        + "&limit=50", "application/json", "false" },
                // Legacy v1 endpoints as fallback
                { "https://listen.tidal.com/v1/users/" + tidalUserId + "/playlists?countryCode=" + countryCode
                        + "&limit=50", "application/vnd.tidal.v1+json", "false" },
                { "https://api.tidalhifi.com/v1/users/" + tidalUserId + "/playlists?countryCode=" + countryCode
                        + "&limit=50", "application/vnd.tidal.v1+json", "false" },
        };

        for (String[] config : endpointConfigs) {
            try {
                String url = config[0];
                HttpHeaders reqHeaders = new HttpHeaders();
                reqHeaders.setBearerAuth(token);
                reqHeaders.set("Accept", config[1]);

                // For OpenAPI v2, use Client-Id header instead of x-tidal-token
                if ("true".equals(config[2])) {
                    reqHeaders.set("Client-Id", tidalProperties.getClientId());
                } else {
                    reqHeaders.set("x-tidal-token", tidalProperties.getClientId());
                }

                log.info("[Tidal] Trying alternative endpoint: {}", url);
                HttpEntity<Void> entity = new HttpEntity<>(reqHeaders);
                ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode data = response.getBody();
                    log.info("[Tidal] Alternative endpoint success: {} - Response keys: {}", url, data.fieldNames());

                    // Handle various response structures from Tidal API
                    JsonNode items = null;

                    // OpenAPI v2 uses JSON:API format with "data" array
                    if (data.has("data") && data.path("data").isArray()) {
                        items = data.path("data");
                    } else if (data.has("items") && data.path("items").isArray()) {
                        items = data.path("items");
                    } else if (data.isArray()) {
                        items = data;
                    }

                    if (items != null && items.size() > 0) {
                        log.info("[Tidal] Found {} playlists via {}", items.size(), url);
                        List<JsonNode> result = new ArrayList<>();

                        for (JsonNode item : items) {
                            // OpenAPI v2 JSON:API format has nested attributes
                            if (item.has("attributes")) {
                                // Merge id and attributes for compatibility
                                ObjectNode merged = objectMapper.createObjectNode();
                                merged.put("uuid", item.path("id").asText());
                                JsonNode attrs = item.path("attributes");
                                attrs.fieldNames().forEachRemaining(field -> merged.set(field, attrs.path(field)));
                                result.add(merged);
                            } else {
                                result.add(item);
                            }
                        }
                        return result;
                    } else {
                        log.info("[Tidal] Endpoint returned empty or no playlists: {}", url);
                    }
                }
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                log.info("[Tidal] Alternative endpoint {} returned {}: {}", config[0], e.getStatusCode(),
                        e.getMessage());
            } catch (Exception e) {
                log.debug("[Tidal] Alternative endpoint failed: {}", e.getMessage());
            }
        }

        return Collections.emptyList();
    }

    private JsonNode fetchPlaylistInfo(String token, String playlistId, String countryCode) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Accept", "application/vnd.tidal.v1+json");

            String url = tidalProperties.getApiUrl() + "/playlists/" + playlistId + "?countryCode=" + countryCode;
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

            return response.getBody();
        } catch (Exception e) {
            log.error("[Tidal] Failed to fetch playlist info", e);
            return null;
        }
    }

    private List<JsonNode> fetchPlaylistTracks(String token, String playlistId, String countryCode) {
        List<JsonNode> allItems = new ArrayList<>();
        int offset = 0;
        int limit = 100;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Accept", "application/vnd.tidal.v1+json");

            while (true) {
                String url = String.format("%s/playlists/%s/items?limit=%d&offset=%d&countryCode=%s",
                        tidalProperties.getApiUrl(), playlistId, limit, offset, countryCode);

                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    break;
                }

                JsonNode data = response.getBody();
                JsonNode items = data.path("items");
                if (!items.isArray() || items.isEmpty())
                    break;

                items.forEach(allItems::add);

                int total = data.path("totalNumberOfItems").asInt(items.size());
                offset += items.size();
                if (offset >= total)
                    break;
            }
        } catch (Exception e) {
            log.error("[Tidal] fetchPlaylistTracks error", e);
        }

        log.info("[Tidal] Total tracks fetched: {}", allItems.size());
        return allItems;
    }
    // --- Featured Playlists ---

    @Override
    public TidalFeaturedResponse getFeatured() {
        // Since we don't have a direct editorial API access without user token often,
        // we will try to fetch some popular charts or staff picks if possible.
        // For now, to solve 404, we return an empty or mock structure.

        // Mock structure matching frontend expectations
        List<TidalFeaturedResponse.FeaturedGroup> featuredGroups = new ArrayList<>();

        // Group 1: K-POP
        List<TidalPlaylist> kpopPlaylists = new ArrayList<>();
        kpopPlaylists.add(TidalPlaylist.builder()
                .uuid("33b4d455-837b-40b9-8e50-2f9540c1e828") // Dummy UUID
                .title("K-Pop Hits")
                .description("Latest K-Pop hits.")
                .numberOfTracks(20)
                .image("https://resources.tidal.com/images/5dcd5f75/404a/447a/8526/4243673f8be0/320x320.jpg") // Example
                                                                                                              // image
                .build());

        featuredGroups.add(TidalFeaturedResponse.FeaturedGroup.builder()
                .genre("K-POP")
                .playlists(kpopPlaylists)
                .build());

        return TidalFeaturedResponse.builder()
                .featured(featuredGroups)
                .build();
    }

    @Override
    public Object getPlaylistTracks(String id, String countryCode, int limit, int offset) {
        // Mock data for the dummy Featured Playlist
        if ("33b4d455-837b-40b9-8e50-2f9540c1e828".equals(id)) {
            List<Map<String, Object>> items = new ArrayList<>();
            items.add(Map.of("title", "Super Shy", "artist", Map.of("name", "NewJeans")));
            items.add(Map.of("title", "Seven", "artist", Map.of("name", "Jung Kook")));
            items.add(Map.of("title", "ETA", "artist", Map.of("name", "NewJeans")));
            items.add(Map.of("title", "I AM", "artist", Map.of("name", "IVE")));
            items.add(Map.of("title", "Fast Forward", "artist", Map.of("name", "Jeon Somi")));

            return Map.of(
                    "limit", limit,
                    "offset", offset,
                    "totalNumberOfItems", 5,
                    "items", items);
        }

        // For real playlists, try to fetch if we have a valid token (requires visitorId
        // context usually,
        // but here we might lack it if this is a public call.
        // Tidal API requires User Token for most playlist operations.
        // If we don't have a token, return empty or error.

        // Since this endpoint is public/proxied without specific visitorId param in the
        // path usually,
        // we might check if there's a default token or just return empty for now to
        // avoid crashes.
        return Map.of("items", Collections.emptyList(), "totalNumberOfItems", 0);
    }

    @Override
    public TidalSearchResponse search(String query, String type, int limit, String countryCode, String visitorId) {
        TidalTokenStore.TokenInfo tokenInfo = getValidToken(visitorId);
        String token = (tokenInfo != null) ? tokenInfo.accessToken : null;

        // If strict on user token, we might return empty if null.
        // But for search, we might want to try Client Credentials if implemented,
        // OR just require user login as per current design relying on visitorTokens.
        if (token == null) {
            // For now, return empty if no user token, or could implement Client Auth
            // fallback
            return TidalSearchResponse.builder().playlists(Collections.emptyList()).tracks(Collections.emptyList())
                    .build();
        }

        if (countryCode == null) {
            countryCode = (tokenInfo.countryCode != null) ? tokenInfo.countryCode : "US";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Accept", "application/vnd.tidal.v1+json");

            String url = String.format("%s/search?query=%s&types=%s&limit=%d&countryCode=%s",
                    tidalProperties.getApiUrl(),
                    URLEncoder.encode(query, StandardCharsets.UTF_8),
                    type != null ? type : "TRACKS,PLAYLISTS",
                    limit,
                    countryCode);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode data = response.getBody();

            List<TidalPlaylist> playlists = new ArrayList<>();
            List<TidalTrack> tracks = new ArrayList<>();

            if (data != null) {
                // Parse Playlists
                if (data.has("playlists")) {
                    JsonNode playlistItems = data.path("playlists").path("items");
                    playlistItems.forEach(p -> {
                        String squareImage = p.path("squareImage").asText(null);
                        String image = squareImage != null
                                ? "https://resources.tidal.com/images/" + squareImage.replace("-", "/") + "/320x320.jpg"
                                : null;

                        playlists.add(TidalPlaylist.builder()
                                .uuid(p.path("uuid").asText())
                                .title(p.path("title").asText())
                                .numberOfTracks(p.path("numberOfTracks").asInt())
                                .image(image)
                                .description(p.path("description").asText(null))
                                .build());
                    });
                }

                // Parse Tracks
                if (data.has("tracks")) {
                    JsonNode trackItems = data.path("tracks").path("items");
                    trackItems.forEach(t -> {
                        String albumCover = t.path("album").path("cover").asText(null);
                        String artwork = albumCover != null
                                ? "https://resources.tidal.com/images/" + albumCover.replace("-", "/") + "/320x320.jpg"
                                : null;

                        tracks.add(TidalTrack.builder()
                                .id(t.path("id").asText())
                                .title(t.path("title").asText())
                                .artist(t.path("artist").path("name")
                                        .asText(t.path("artists").path(0).path("name").asText("Unknown")))
                                .album(t.path("album").path("title").asText("Unknown"))
                                .duration(t.path("duration").asInt())
                                .artwork(artwork)
                                .isrc(t.path("isrc").asText(null))
                                .allowStreaming(t.path("allowStreaming").asBoolean(true))
                                .build());
                    });
                }
            }

            return TidalSearchResponse.builder()
                    .playlists(playlists)
                    .tracks(tracks)
                    .build();

        } catch (Exception e) {
            log.error("[Tidal] Search failed", e);
            return TidalSearchResponse.builder().playlists(Collections.emptyList()).tracks(Collections.emptyList())
                    .build();
        }
    }
}
