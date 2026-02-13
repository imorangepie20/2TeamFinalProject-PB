package com.springboot.finalprojcet.domain.tidal.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.springboot.finalprojcet.domain.common.service.ImageService;
import com.springboot.finalprojcet.domain.gms.repository.PlaylistRepository;
import com.springboot.finalprojcet.domain.playlist.repository.UserDismissedPlaylistRepository;
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
import java.time.Duration;
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
    private final UserDismissedPlaylistRepository dismissedPlaylistRepository;
    private final TracksRepository tracksRepository;
    private final PlaylistTracksRepository playlistTracksRepository;
    private final TidalTokenStore tokenStore;
    private final ImageService imageService;

    @Override
    public TidalTokenStore getTokenStore() {
        return tokenStore;
    }

    @Override
    public TidalLoginUrlResponse getLoginUrl(String visitorId, String origin) {
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        if (visitorId != null && !visitorId.isEmpty()) {
            // Save PKCE verifier to Redis
            tokenStore.savePkceVerifier(visitorId, codeVerifier);
        }

        String redirectUri = resolveRedirectUri(origin);
        // python-tidal (tidalapi) compatible scopes for streaming support
        String scopes = "r_usr w_usr w_sub";

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
            String countryCode = tokenInfo.countryCode != null ? tokenInfo.countryCode : "US";

            // Debug: Log first playlist structure to understand image fields
            if (!playlists.isEmpty()) {
                JsonNode first = playlists.get(0);
                log.info(
                        "[Tidal] Sample playlist raw data - uuid:{}, title:{}, squareImage:{}, image:{}, picture:{}, imageLinks:{}",
                        first.path("uuid").asText(),
                        first.path("title").asText(),
                        first.path("squareImage").asText("NULL"),
                        first.path("image"),
                        first.path("picture").asText("NULL"),
                        first.path("imageLinks"));
            }

            for (JsonNode p : playlists) {
                String image = resolvePlaylistImage(p);
                String uuid = p.path("uuid").asText();
                int trackCount = p.path("numberOfTracks").asInt(
                        p.path("trackCount").asInt(
                                p.path("totalNumberOfItems").asInt(0)));
                String title = p.path("title").asText(p.path("name").asText("Untitled"));

                // If image is null, try to fetch individual playlist info for cover image
                if (image == null && uuid != null && !uuid.isEmpty()) {
                    try {
                        JsonNode playlistInfo = fetchPlaylistInfo(tokenInfo.accessToken, uuid, countryCode);
                        if (playlistInfo != null) {
                            // Use the same resolvePlaylistImage for consistency
                            image = resolvePlaylistImage(playlistInfo);

                            // Final fallback to squareImage if still null
                            if (image == null) {
                                String squareImage = playlistInfo.path("squareImage").asText(null);
                                if (squareImage != null && !squareImage.isEmpty()) {
                                    if (squareImage.startsWith("http")) {
                                        image = squareImage;
                                    } else {
                                        image = "https://resources.tidal.com/images/" + squareImage.replace("-", "/")
                                                + "/320x320.jpg";
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[Tidal] Failed to fetch individual playlist image for {}: {}", uuid, e.getMessage());
                    }
                }

                // Final fallback: Fetch first track and use its album cover
                if (image == null && uuid != null && !uuid.isEmpty()) {
                    try {
                        List<JsonNode> playlistTracks = fetchPlaylistTracks(tokenInfo.accessToken, uuid, countryCode);
                        if (!playlistTracks.isEmpty()) {
                            JsonNode firstTrack = playlistTracks.get(0);
                            JsonNode track = firstTrack.has("item") ? firstTrack.path("item") : firstTrack;
                            String albumCover = track.path("album").path("cover").asText(null);
                            if (albumCover != null && !albumCover.isEmpty()) {
                                image = "https://resources.tidal.com/images/" + albumCover.replace("-", "/")
                                        + "/320x320.jpg";
                                log.info("[Tidal syncPlaylists] Using first track's album cover for '{}': {}", title,
                                        image);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[Tidal] Failed to fetch tracks for image fallback: {}", e.getMessage());
                    }
                }

                // Log the image status for debugging
                if (image == null) {
                    log.debug("[Tidal] No image found for playlist: {} ({})", title, uuid);
                }

                items.add(TidalPlaylistResponse.TidalPlaylistItem.builder()
                        .uuid(uuid)
                        .title(title)
                        .numberOfTracks(trackCount)
                        .trackCount(trackCount)
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
            // Check for duplicate import (including dismissed playlists)
            String externalId = "tidal:" + request.getPlaylistId();
            if (playlistRepository.existsByExternalIdAndUserUserId(externalId, request.getUserId())
                    || dismissedPlaylistRepository.existsByUserUserIdAndExternalId(request.getUserId(), externalId)) {
                log.info("[Tidal] Playlist already imported or dismissed: {}", request.getPlaylistId());
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

            // Extract cover image (similar to Node.js implementation)
            String coverImage = extractTidalCoverImage(playlist);
            log.info("[Tidal] Extracted coverImage for '{}': {}", playlist.path("title").asText(), coverImage);

            // Fallback: Use first track's album cover if no playlist image
            if ((coverImage == null || coverImage.isEmpty()) && !tracks.isEmpty()) {
                JsonNode firstTrack = tracks.get(0);
                JsonNode track = firstTrack.has("item") ? firstTrack.path("item") : firstTrack;
                String albumCover = track.path("album").path("cover").asText(null);
                if (albumCover != null && !albumCover.isEmpty()) {
                    coverImage = "https://resources.tidal.com/images/" + albumCover.replace("-", "/") + "/320x320.jpg";
                    log.info("[Tidal] Using first track's album cover as fallback: {}", coverImage);
                }
            }

            // Download image to local storage
            if (coverImage != null && !coverImage.isEmpty() && coverImage.startsWith("http")) {
                try {
                    String localPath = imageService.downloadImage(coverImage, "playlists");
                    log.info("[Tidal] Downloaded playlist cover to local: {} -> {}", coverImage, localPath);
                    coverImage = localPath;
                } catch (Exception e) {
                    log.warn("[Tidal] Failed to download playlist cover, using remote URL: {}", e.getMessage());
                }
            }

            Playlists newPlaylist = Playlists.builder()
                    .user(user)
                    .title(playlist.path("title").asText())
                    .description(playlist.path("description").asText(""))
                    .coverImage(coverImage)
                    .sourceType(SourceType.Platform)
                    .externalId(externalId)
                    .spaceType(SpaceType.PMS)
                    .statusFlag(StatusFlag.PRP)
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
                    // fallback: artist가 Unknown이면 v1 API로 개별 조회
                    if ("Unknown".equals(artist) && tidalId != null && !tidalId.isEmpty()) {
                        String resolved = fetchArtistFromTidalV1(tokenInfo.accessToken, tidalId, countryCode);
                        if (resolved != null) {
                            artist = resolved;
                            log.info("[Tidal] Resolved artist for '{}': {}", track.path("title").asText(), artist);
                        }
                    }
                    String albumCover = track.path("album").path("cover").asText(null);
                    String artwork = null;
                    if (albumCover != null && !albumCover.isEmpty()) {
                        String tidalUrl = "https://resources.tidal.com/images/" + albumCover.replace("-", "/")
                                + "/320x320.jpg";
                        try {
                            artwork = imageService.downloadImage(tidalUrl, "tracks");
                            log.debug("[Tidal] Downloaded track artwork: {} -> {}", tidalUrl, artwork);
                        } catch (Exception e) {
                            log.warn("[Tidal] Failed to download track artwork, using remote URL: {}", e.getMessage());
                            artwork = tidalUrl;
                        }
                    }

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
    @Transactional(noRollbackFor = Exception.class)
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
            int failedCount = 0;
            
            for (JsonNode p : playlists) {
                String playlistUuid = p.path("uuid").asText();
                String externalId = "tidal:" + playlistUuid;

                // Skip if already imported or dismissed
                if (playlistRepository.existsByExternalIdAndUserUserId(externalId, userId)
                        || dismissedPlaylistRepository.existsByUserUserIdAndExternalId(userId, externalId)) {
                    log.info("[Sync] Skipping already imported/dismissed playlist: {}", playlistUuid);
                    continue;
                }

                try {
                    // Fetch individual playlist info to get cover image from included array
                    String coverImage = null;
                    try {
                        JsonNode playlistInfo = fetchPlaylistInfo(token, playlistUuid, "KR");
                        if (playlistInfo != null) {
                            coverImage = extractTidalCoverImage(playlistInfo);
                            log.info("[Sync] Extracted coverImage for '{}': {}", p.path("title").asText(), coverImage);
                        }
                    } catch (Exception e) {
                        log.warn("[Sync] Failed to fetch playlist info for cover: {}", e.getMessage());
                    }

                    // Fallback: try extracting from list item (unlikely to have image)
                    if (coverImage == null || coverImage.isEmpty()) {
                        coverImage = extractTidalCoverImage(p);
                    }

                    // Download playlist cover to local storage
                    if (coverImage != null && !coverImage.isEmpty() && coverImage.startsWith("http")) {
                        try {
                            coverImage = imageService.downloadImage(coverImage, "playlists");
                        } catch (Exception e) {
                            log.warn("[Sync] Failed to download playlist cover: {}", e.getMessage());
                        }
                    }

                    Playlists playlist = Playlists.builder()
                            .user(user)
                            .title(p.path("title").asText())
                            .description(p.path("description").asText("Tidal Playlist"))
                            .spaceType(SpaceType.PMS)
                            .statusFlag(StatusFlag.PRP)
                            .sourceType(SourceType.Platform)
                            .externalId(externalId)
                            .coverImage(coverImage)
                            .build();

                    playlistRepository.save(playlist);

                    // Fetch and save tracks
                    List<JsonNode> tracks = fetchPlaylistTracks(token, p.path("uuid").asText(), "KR");
                    int tracksSaved = 0;
                    
                    for (int i = 0; i < tracks.size(); i++) {
                        JsonNode item = tracks.get(i);
                        JsonNode track = item.has("item") ? item.path("item") : item;

                        if (!track.has("title") && !track.has("name"))
                            continue;

                        try {
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("tidalId", track.path("id").asText());
                            metadata.put("isrc", track.path("isrc").asText(null));

                            // Extract and download track artwork
                            String albumCover = track.path("album").path("cover").asText(null);
                            String artwork = null;
                            if (albumCover != null && !albumCover.isEmpty()) {
                                String tidalUrl = "https://resources.tidal.com/images/" + albumCover.replace("-", "/")
                                        + "/320x320.jpg";
                                try {
                                    artwork = imageService.downloadImage(tidalUrl, "tracks");
                                } catch (Exception e) {
                                    log.warn("[Sync] Failed to download track artwork: {}", e.getMessage());
                                    artwork = tidalUrl;
                                }
                            }

                            String syncArtist = track.path("artist").path("name").asText(
                                    track.path("artists").path(0).path("name").asText("Unknown"));
                            // fallback: artist가 Unknown이면 v1 API로 개별 조회
                            if ("Unknown".equals(syncArtist)) {
                                String tidalTrackId = track.path("id").asText();
                                if (tidalTrackId != null && !tidalTrackId.isEmpty()) {
                                    String resolved = fetchArtistFromTidalV1(token, tidalTrackId, "KR");
                                    if (resolved != null) {
                                        syncArtist = resolved;
                                        log.info("[Sync] Resolved artist for '{}': {}", track.path("title").asText(), syncArtist);
                                    }
                                }
                            }

                            Tracks trackEntity = Tracks.builder()
                                    .title(track.path("title").asText(track.path("name").asText()))
                                    .artist(syncArtist)
                                    .album(track.path("album").path("title").asText("Unknown"))
                                    .duration(track.path("duration").asInt(0))
                                    .externalMetadata(objectMapper.writeValueAsString(metadata))
                                    .artwork(artwork)
                                    .build();

                            tracksRepository.save(trackEntity);

                            PlaylistTracks pt = PlaylistTracks.builder()
                                    .playlist(playlist)
                                    .track(trackEntity)
                                    .orderIndex(i)
                                    .build();
                            playlistTracksRepository.save(pt);
                            tracksSaved++;
                        } catch (Exception e) {
                            log.warn("[Sync] Track insert failed (continuing): {}", e.getMessage());
                            // Continue with next track instead of failing entire sync
                        }
                    }
                    
                    log.info("[Sync] Playlist '{}' synced with {}/{} tracks", 
                            p.path("title").asText(), tracksSaved, tracks.size());
                    syncedCount++;
                } catch (Exception e) {
                    log.error("[Sync] Playlist insert failed (continuing): {} - {}", playlistUuid, e.getMessage());
                    failedCount++;
                    // Continue with next playlist instead of failing entire sync
                }
            }

            String message = String.format("%d개 플레이리스트 동기화 완료", syncedCount);
            if (failedCount > 0) {
                message += String.format(" (%d개 실패)", failedCount);
            }
            
            return TidalSyncResponse.builder()
                    .success(true)
                    .syncedCount(syncedCount)
                    .message(message)
                    .build();
        } catch (Exception e) {
            log.error("Tidal Sync Error", e);
            return TidalSyncResponse.builder().success(false).error("동기화 중 오류가 발생했습니다: " + e.getMessage()).build();
        }
    }

    // python-tidal (tidalapi) library credentials - required for Device Authorization Flow
    private static final String TIDAL_DEVICE_CLIENT_ID = "fX2JxdmntZWK0ixT";
    private static final String TIDAL_DEVICE_CLIENT_SECRET = "1Nn9AfDAjxrgJFJbKNWLeAyKGVGmINuXPPLHVXAvxAg=";

    @Override
    public TidalDeviceAuthResponse initDeviceAuth() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // Device Authorization Flow requires python-tidal's built-in credentials
            // Regular Tidal Developer Portal apps don't support Device Flow
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", TIDAL_DEVICE_CLIENT_ID);
            body.add("scope", "r_usr w_usr w_sub");

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
            String url = tidalProperties.getAuthUrl() + "/device_authorization";

            log.info("[Tidal] Initiating Device Auth with python-tidal credentials");
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, entity, JsonNode.class);
            JsonNode data = response.getBody();

            if (data == null || data.has("error")) {
                String error = data != null ? data.path("error_description").asText(data.path("error").asText()) : "Empty response";
                log.error("[Tidal] Device auth failed: {}", error);
                throw new RuntimeException("Device auth init failed: " + error);
            }

            log.info("[Tidal] Device Auth initiated - userCode: {}", data.path("userCode").asText());

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
            // Use python-tidal credentials for Device Flow token exchange
            String credentials = Base64.getEncoder().encodeToString(
                    (TIDAL_DEVICE_CLIENT_ID + ":" + TIDAL_DEVICE_CLIENT_SECRET).getBytes());
            headers.set("Authorization", "Basic " + credentials);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", TIDAL_DEVICE_CLIENT_ID);
            body.add("device_code", request.getDeviceCode());
            body.add("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            body.add("scope", "r_usr w_usr w_sub");

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
            String refreshToken = data.path("refresh_token").asText(null);
            long expiresIn = data.path("expires_in").asLong(3600);
            
            TidalAuthStatusResponse.TidalUserInfo userInfo = fetchSessionInfo(accessToken);

            // Store token to Redis for persistence (if visitorId provided)
            if (request.getVisitorId() != null && !request.getVisitorId().isEmpty()) {
                TidalTokenStore.TokenInfo tokenInfo = new TidalTokenStore.TokenInfo(
                        accessToken, refreshToken, expiresIn,
                        userInfo.getUserId(), userInfo.getCountryCode(), userInfo.getUsername());
                tokenStore.saveToken(request.getVisitorId(), tokenInfo);
                log.info("[Tidal] Token stored for visitorId: {}", request.getVisitorId());
            }

            return TidalTokenPollResponse.builder()
                    .success(true)
                    .user(userInfo)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn((int) expiresIn)
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
        if (origin != null && !origin.isEmpty()) {
            // Force localhost without port to match Tidal Portal whitelist
            if (origin.contains("localhost") || origin.contains("127.0.0.1")) {
                origin = "http://localhost";
            }
            return origin + "/tidal-callback";
        }
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

    @Override
    public TidalStreamUrlResponse getStreamUrl(String trackId, String visitorId, String quality) {
        TidalTokenStore.TokenInfo tokenInfo = getValidToken(visitorId);
        if (tokenInfo == null) {
            return TidalStreamUrlResponse.builder()
                    .success(false)
                    .error("Not authenticated with Tidal")
                    .build();
        }

        try {
            String countryCode = tokenInfo.countryCode != null ? tokenInfo.countryCode : "KR";

            // Tidal v1 API for playback URL
            // https://api.tidal.com/v1/tracks/{trackId}/playbackinfo
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(tokenInfo.accessToken);
            headers.set("Accept", "application/json");

            // Quality mapping: LOW(64), NORMAL(96), HIGH(320), LOSSLESS(FLAC),
            // HI_RES(MQA/FLAC)
            String audioQuality = quality != null ? quality : "LOSSLESS";

            String url = tidalProperties.getApiUrl() + "/tracks/" + trackId + "/playbackinfo"
                    + "?audioquality=" + audioQuality
                    + "&playbackmode=STREAM"
                    + "&assetpresentation=FULL"
                    + "&countryCode=" + countryCode;

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            try {
                ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode body = response.getBody();

                    // Extract stream URL from manifest
                    String trackUrl = body.path("manifest").asText(null);
                    String audioQualityRes = body.path("audioQuality").asText("LOSSLESS");
                    String codec = body.path("codec").asText("AAC");
                    int bitRate = body.path("bitRate").asInt(0);
                    int sampleRate = body.path("sampleRate").asInt(44100);
                    int bitDepth = body.path("bitDepth").asInt(16);

                    // Manifest can be base64 encoded or direct URL
                    if (trackUrl != null && !trackUrl.startsWith("http")) {
                        // Decode base64 manifest to get actual URL
                        try {
                            String decoded = new String(Base64.getDecoder().decode(trackUrl), StandardCharsets.UTF_8);
                            // Parse manifest XML/JSON to get URL
                            if (decoded.contains("http")) {
                                // Simple extraction - find URL in decoded content
                                int urlStart = decoded.indexOf("http");
                                int urlEnd = decoded.indexOf("\"", urlStart);
                                if (urlEnd == -1)
                                    urlEnd = decoded.indexOf("<", urlStart);
                                if (urlEnd > urlStart) {
                                    trackUrl = decoded.substring(urlStart, urlEnd);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("[Tidal Stream] Failed to decode manifest: {}", e.getMessage());
                        }
                    }

                    if (trackUrl != null && trackUrl.startsWith("http")) {
                        log.info("[Tidal Stream] Got stream URL for track {}: quality={}", trackId, audioQualityRes);
                        return TidalStreamUrlResponse.builder()
                                .success(true)
                                .streamUrl(trackUrl)
                                .quality(audioQualityRes)
                                .codec(codec)
                                .bitRate(bitRate)
                                .sampleRate(sampleRate)
                                .bitDepth(bitDepth)
                                .build();
                    }
                }
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                log.warn("[Tidal Stream] Failed to get stream URL: {} - {}", e.getStatusCode(), e.getMessage());

                // If 403 or 401, token may be invalid or missing required scope
                if (e.getStatusCode().value() == 403 || e.getStatusCode().value() == 401) {
                    return TidalStreamUrlResponse.builder()
                            .success(false)
                            .error("Tidal API access denied. Your subscription may not support streaming.")
                            .fallbackSource("YOUTUBE")
                            .build();
                }
            }

            // Fallback: return error with suggestion to use YouTube
            return TidalStreamUrlResponse.builder()
                    .success(false)
                    .error("Could not get Tidal stream URL")
                    .fallbackSource("YOUTUBE")
                    .build();

        } catch (Exception e) {
            log.error("[Tidal Stream] Error getting stream URL for {}: {}", trackId, e.getMessage());
            return TidalStreamUrlResponse.builder()
                    .success(false)
                    .error("Failed to get stream URL: " + e.getMessage())
                    .fallbackSource("YOUTUBE")
                    .build();
        }
    }

    /**
     * Resolve playlist image from various formats in Tidal API responses.
     * Matches Node.js implementation's extractTidalImage function.
     */
    private String resolvePlaylistImage(JsonNode p) {
        String title = p.path("title").asText(p.path("name").asText("unknown"));

        // 1. Check squareImage field (v1 API format)
        String squareImage = p.path("squareImage").asText(null);
        log.info("[Tidal resolveImage] Playlist '{}' - squareImage={}", title, squareImage);

        if (squareImage != null && !squareImage.isEmpty()) {
            String result;
            if (squareImage.startsWith("http")) {
                result = squareImage;
            } else {
                result = "https://resources.tidal.com/images/" + squareImage.replace("-", "/") + "/320x320.jpg";
            }
            log.info("[Tidal resolveImage] -> Found via squareImage: {}", result);
            return result;
        }

        // 2. Check image field (might be full URL or UUID-style ID)
        JsonNode imageNode = p.path("image");
        if (!imageNode.isMissingNode()) {
            if (imageNode.isTextual()) {
                String imageField = imageNode.asText(null);
                if (imageField != null && !imageField.isEmpty()) {
                    if (imageField.startsWith("http")) {
                        return imageField;
                    }
                    return "https://resources.tidal.com/images/" + imageField.replace("-", "/") + "/320x320.jpg";
                }
            } else if (imageNode.isObject()) {
                // image might be an object with url property
                String url = imageNode.path("url").asText(null);
                if (url != null && !url.isEmpty()) {
                    return url;
                }
            }
        }

        // 3. Check picture field
        String picture = p.path("picture").asText(null);
        if (picture != null && !picture.isEmpty()) {
            if (picture.startsWith("http")) {
                return picture;
            }
            return "https://resources.tidal.com/images/" + picture.replace("-", "/") + "/320x320.jpg";
        }

        // 4. Check images array (Tidal API v2 style)
        JsonNode images = p.path("images");
        if (images.isArray() && images.size() > 0) {
            // Try to find image with width >= 320, or use first one
            for (JsonNode img : images) {
                int width = img.path("width").asInt(0);
                if (width >= 320) {
                    String url = img.path("url").asText(img.path("href").asText(null));
                    if (url != null && !url.isEmpty()) {
                        return url;
                    }
                }
            }
            // Fallback to first image
            JsonNode firstImg = images.get(0);
            String url = firstImg.path("url").asText(firstImg.path("href").asText(null));
            if (url != null && !url.isEmpty()) {
                return url;
            }
        }

        // 5. Check imageLinks array (v2 API format)
        JsonNode imageLinks = p.path("imageLinks");
        if (imageLinks.isArray() && imageLinks.size() > 0) {
            for (JsonNode imgLink : imageLinks) {
                String href = imgLink.path("href").asText(null);
                if (href != null && href.startsWith("http")) {
                    return href;
                }
            }
        }

        // 6. Check promotedArtists - use first artist's picture as fallback
        JsonNode promotedArtists = p.path("promotedArtists");
        if (promotedArtists.isArray() && promotedArtists.size() > 0) {
            JsonNode artist = promotedArtists.get(0);
            String artistPicture = artist.path("picture").asText(null);
            if (artistPicture != null && !artistPicture.isEmpty()) {
                if (artistPicture.startsWith("http")) {
                    return artistPicture;
                }
                return "https://resources.tidal.com/images/" + artistPicture.replace("-", "/") + "/320x320.jpg";
            }
        }

        // 7. Check creator's picture
        JsonNode creator = p.path("creator");
        if (!creator.isMissingNode()) {
            String creatorPicture = creator.path("picture").asText(null);
            if (creatorPicture != null && !creatorPicture.isEmpty()) {
                if (creatorPicture.startsWith("http")) {
                    return creatorPicture;
                }
                return "https://resources.tidal.com/images/" + creatorPicture.replace("-", "/") + "/320x320.jpg";
            }
        }

        // Log all available fields for debugging (only at info level for
        // troubleshooting)
        log.warn("[Tidal resolveImage] NO IMAGE FOUND for playlist '{}' (uuid:{}). Available fields: {}",
                title, p.path("uuid").asText("?"), p.fieldNames());
        return null;
    }

    /**
     * Extract cover image from Tidal playlist JSON node.
     * This method is an alias for resolvePlaylistImage that provides a cleaner name
     * when extracting cover images for database storage.
     */
    private String extractTidalCoverImage(JsonNode playlist) {
        return resolvePlaylistImage(playlist);
    }

    /**
     * Fetch playlist cover art from Tidal v2 API
     */
    private String fetchPlaylistCoverArt(String playlistId, String token, String countryCode) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Accept", "application/vnd.api+json");
            headers.set("Client-Id", tidalProperties.getClientId());

            String url = "https://openapi.tidal.com/v2/playlists/" + playlistId + "?countryCode=" + countryCode
                    + "&include=coverArt";
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();

                // Check included array for coverArt - actual type is "artworks" with files
                // array
                if (body.has("included") && body.path("included").isArray()) {
                    for (JsonNode inc : body.path("included")) {
                        String type = inc.path("type").asText("");
                        // Handle both "images" and "artworks" types
                        if ("images".equals(type) || "artworks".equals(type)) {
                            // First try direct href
                            String href = inc.path("attributes").path("href").asText(null);
                            if (href != null && !href.isEmpty()) {
                                log.debug("[Tidal] Found coverArt direct href: {}", href);
                                return href;
                            }
                            // Then try files array (actual Tidal v2 structure)
                            JsonNode files = inc.path("attributes").path("files");
                            if (files.isArray() && files.size() > 0) {
                                // Find 320x320 or the first available
                                for (JsonNode file : files) {
                                    int width = file.path("meta").path("width").asInt(0);
                                    if (width == 320) {
                                        href = file.path("href").asText(null);
                                        if (href != null && !href.isEmpty()) {
                                            log.debug("[Tidal] Found coverArt 320x320: {}", href);
                                            return href;
                                        }
                                    }
                                }
                                // Fallback: first file
                                href = files.get(0).path("href").asText(null);
                                if (href != null && !href.isEmpty()) {
                                    log.debug("[Tidal] Found coverArt first file: {}", href);
                                    return href;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[Tidal] Failed to fetch coverArt for {}: {}", playlistId, e.getMessage());
        }
        return null;
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

            // 1) Primary: openapi.tidal.com v2 (works with new OAuth2 scopes)
            List<JsonNode> v2Result = fetchPlaylistsViaV2(token, tidalUserId, countryCode);
            if (!v2Result.isEmpty()) {
                return v2Result;
            }

            // 2) Fallback: v1 endpoints (may work if token has legacy scope access)
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Accept", "application/vnd.tidal.v1+json");

            String[] v1Endpoints = {
                    tidalProperties.getApiUrl() + "/users/" + tidalUserId + "/playlists?countryCode=" + countryCode
                            + "&limit=50",
                    "https://listen.tidal.com/v1/users/" + tidalUserId + "/playlists?countryCode=" + countryCode
                            + "&limit=50"
            };

            for (String url : v1Endpoints) {
                try {
                    log.info("[Tidal] Trying v1 endpoint: {}", url);
                    HttpEntity<Void> entity = new HttpEntity<>(headers);
                    ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity,
                            JsonNode.class);

                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        JsonNode items = response.getBody().path("items");
                        if (items.isArray() && items.size() > 0) {
                            log.info("[Tidal] Found {} playlists via v1: {}", items.size(), url);
                            List<JsonNode> result = new ArrayList<>();
                            items.forEach(result::add);
                            return result;
                        }
                    }
                } catch (org.springframework.web.client.HttpClientErrorException e) {
                    log.warn("[Tidal] v1 {} returned {}", url, e.getStatusCode());
                } catch (Exception e) {
                    log.warn("[Tidal] v1 {} failed: {}", url, e.getMessage());
                }
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[Tidal] fetchTidalPlaylists error", e);
            return Collections.emptyList();
        }
    }

    /**
     * Fetch playlists via Tidal OpenAPI v2
     * Confirmed endpoint from tidal-sdk-web: GET
     * /userCollections/{userId}/relationships/playlists
     */
    private List<JsonNode> fetchPlaylistsViaV2(String token, String tidalUserId, String countryCode) {
        String[][] endpointConfigs = {
                // {url, accept header}
                // Official OpenAPI v2: userCollections/{id}/relationships/playlists (confirmed
                // from tidal-sdk-web)
                // Include playlists.coverArt to get images in one request
                { "https://openapi.tidal.com/v2/userCollections/" + tidalUserId
                        + "/relationships/playlists?include=playlists,playlists.coverArt&countryCode=" + countryCode,
                        "application/vnd.api+json" },
                // GET /playlists with no filter returns user's own playlists
                { "https://openapi.tidal.com/v2/playlists?countryCode=" + countryCode,
                        "application/vnd.api+json" },
        };

        for (String[] config : endpointConfigs) {
            try {
                String url = config[0];
                HttpHeaders reqHeaders = new HttpHeaders();
                reqHeaders.setBearerAuth(token);
                reqHeaders.set("Accept", config[1]);
                reqHeaders.set("Client-Id", tidalProperties.getClientId());

                log.info("[Tidal] Trying v2 endpoint: {}", url);
                HttpEntity<Void> entity = new HttpEntity<>(reqHeaders);
                ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode data = response.getBody();
                    log.info("[Tidal] v2 endpoint success: {} - keys: {}", url,
                            data.fieldNames() != null
                                    ? data.toString().substring(0, Math.min(500, data.toString().length()))
                                    : "null");

                    List<JsonNode> result = parseV2PlaylistResponse(data);
                    if (!result.isEmpty()) {
                        log.info("[Tidal] Found {} playlists via v2: {}", result.size(), url);
                        return result;
                    }
                }
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                log.info("[Tidal] v2 {} returned {}: {}", config[0], e.getStatusCode(),
                        e.getResponseBodyAsString().substring(0, Math.min(200, e.getResponseBodyAsString().length())));
            } catch (Exception e) {
                log.info("[Tidal] v2 {} failed: {}", config[0], e.getMessage());
            }
        }

        return Collections.emptyList();
    }

    /**
     * Parse various v2 playlist response formats into normalized playlist nodes.
     */
    private List<JsonNode> parseV2PlaylistResponse(JsonNode data) {
        List<JsonNode> result = new ArrayList<>();

        // Format 1: my-collection/playlists/folders response
        // Contains "items" array with objects having "data" and "trn" (tidal resource
        // name)
        if (data.has("items") && data.path("items").isArray()) {
            for (JsonNode item : data.path("items")) {
                JsonNode playlist = extractPlaylistFromFolderItem(item);
                if (playlist != null) {
                    result.add(playlist);
                }
            }
            if (!result.isEmpty())
                return result;
        }

        // Format 2: JSON:API format with "data" array
        if (data.has("data") && data.path("data").isArray()) {
            // Build a map of included resources for reference resolution
            Map<String, JsonNode> includedMap = new HashMap<>();
            if (data.has("included") && data.path("included").isArray()) {
                for (JsonNode inc : data.path("included")) {
                    String type = inc.path("type").asText();
                    String id = inc.path("id").asText();
                    includedMap.put(type + ":" + id, inc);
                }
            }

            for (JsonNode item : data.path("data")) {
                ObjectNode merged = objectMapper.createObjectNode();
                String itemId = item.path("id").asText();
                merged.put("uuid", itemId);

                if (item.has("attributes")) {
                    JsonNode attrs = item.path("attributes");
                    attrs.fieldNames().forEachRemaining(field -> merged.set(field, attrs.path(field)));
                }

                // Map "name" to "title" if needed
                if (!merged.has("title") && merged.has("name")) {
                    merged.put("title", merged.path("name").asText());
                }

                // Handle numberOfTracks from various sources
                if (!merged.has("numberOfTracks") || merged.path("numberOfTracks").asInt(0) == 0) {
                    JsonNode attrs = item.path("attributes");
                    int trackCount = attrs.path("numberOfTracks").asInt(
                            attrs.path("numberOfItems").asInt(
                                    attrs.path("trackCount").asInt(
                                            attrs.path("totalNumberOfItems").asInt(0))));
                    merged.put("numberOfTracks", trackCount);
                }

                // Handle image from imageLinks or squareImage
                if (!merged.has("squareImage") || merged.path("squareImage").isNull()) {
                    JsonNode imageLinks = item.path("attributes").path("imageLinks");
                    if (imageLinks.isArray() && imageLinks.size() > 0) {
                        // Find square image or use first available
                        for (JsonNode imgLink : imageLinks) {
                            String meta = imgLink.path("meta").asText("");
                            if (meta.contains("320x320") || meta.contains("square")) {
                                merged.put("squareImage", imgLink.path("href").asText());
                                break;
                            }
                        }
                        if (!merged.has("squareImage") || merged.path("squareImage").asText("").isEmpty()) {
                            merged.put("squareImage", imageLinks.path(0).path("href").asText());
                        }
                    }
                }

                // Try to resolve from included resources if playlist data is sparse
                JsonNode includedPlaylist = includedMap.get("playlists:" + itemId);
                if (includedPlaylist != null && includedPlaylist.has("attributes")) {
                    JsonNode incAttrs = includedPlaylist.path("attributes");
                    if (!merged.has("title") || merged.path("title").asText("").isEmpty()) {
                        merged.put("title", incAttrs.path("name").asText(incAttrs.path("title").asText("")));
                    }
                    if (merged.path("numberOfTracks").asInt(0) == 0) {
                        merged.put("numberOfTracks", incAttrs.path("numberOfTracks").asInt(
                                incAttrs.path("numberOfItems").asInt(
                                        incAttrs.path("totalNumberOfItems").asInt(0))));
                    }
                    if (!merged.has("squareImage") || merged.path("squareImage").asText("").isEmpty()) {
                        JsonNode incImageLinks = incAttrs.path("imageLinks");
                        if (incImageLinks.isArray() && incImageLinks.size() > 0) {
                            merged.put("squareImage", incImageLinks.path(0).path("href").asText());
                        }
                    }
                    if (!merged.has("description") || merged.path("description").asText("").isEmpty()) {
                        merged.put("description", incAttrs.path("description").asText(""));
                    }

                    // Try to get coverArt from relationships
                    if (!merged.has("squareImage") || merged.path("squareImage").asText("").isEmpty()) {
                        JsonNode coverArtRel = includedPlaylist.path("relationships").path("coverArt").path("data");
                        if (coverArtRel.has("id")) {
                            String coverArtId = coverArtRel.path("id").asText();
                            JsonNode coverArtImage = includedMap.get("images:" + coverArtId);
                            if (coverArtImage != null) {
                                String href = coverArtImage.path("attributes").path("href").asText("");
                                if (!href.isEmpty()) {
                                    merged.put("squareImage", href);
                                }
                            }
                        }
                    }
                }

                result.add(merged);
            }
            if (!result.isEmpty())
                return result;
        }

        // Format 3: Direct array
        if (data.isArray()) {
            data.forEach(result::add);
        }

        return result;
    }

    /**
     * Extract playlist data from a folder item response.
     * Folder items may contain nested playlist objects.
     */
    private JsonNode extractPlaylistFromFolderItem(JsonNode item) {
        // Check if item itself is a playlist
        if (item.has("uuid") && item.has("title")) {
            return item;
        }

        // Folder item may have "data" containing the playlist
        JsonNode itemData = item.path("data");
        if (itemData.has("uuid") && itemData.has("title")) {
            return itemData;
        }

        // Folder item may reference playlist in "playlist" field
        JsonNode playlist = item.path("playlist");
        if (playlist.has("uuid") && playlist.has("title")) {
            return playlist;
        }

        // v2 folder items: check for trn (tidal resource name) like "trn:playlist:xxx"
        String trn = item.path("trn").asText(null);
        if (trn != null && trn.startsWith("trn:playlist:")) {
            String uuid = trn.replace("trn:playlist:", "");
            ObjectNode normalized = objectMapper.createObjectNode();
            normalized.put("uuid", uuid);
            normalized.put("title", item.path("name").asText(item.path("title").asText("Untitled")));
            normalized.put("numberOfTracks", item.path("numberOfTracks").asInt(
                    item.path("totalNumberOfItems").asInt(0)));

            // Try to get image
            String squareImage = item.path("squareImage").asText(null);
            if (squareImage == null) {
                squareImage = item.path("image").asText(null);
            }
            if (squareImage != null) {
                normalized.put("squareImage", squareImage);
            }

            normalized.put("description", item.path("description").asText(""));
            return normalized;
        }

        // If the item has "addedAt" (typical for folder list), it wraps the actual data
        if (item.has("addedAt") || item.has("lastModifiedAt")) {
            // Try nested fields
            for (String field : new String[] { "item", "playlist", "resource" }) {
                JsonNode nested = item.path(field);
                if (nested.has("uuid")) {
                    return nested;
                }
            }
        }

        return null;
    }

    private JsonNode fetchPlaylistInfo(String token, String playlistId, String countryCode) {
        // Try v1 API FIRST - it returns squareImage directly
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Accept", "application/vnd.tidal.v1+json");

            String url = tidalProperties.getApiUrl() + "/playlists/" + playlistId + "?countryCode=" + countryCode;
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();

                // Debug: Log full response for troubleshooting
                log.info("[Tidal] v1 API FULL RESPONSE for {}: {}", playlistId,
                        body.toString().substring(0, Math.min(1000, body.toString().length())));

                String squareImage = body.path("squareImage").asText(null);
                String image = body.path("image").asText(null);
                log.info("[Tidal] v1 API for {} - squareImage={}, image={}", playlistId, squareImage, image);

                // Return body even if squareImage is null - we'll handle in
                // resolvePlaylistImage
                return body;
            }
        } catch (Exception e) {
            log.warn("[Tidal] v1 fetchPlaylistInfo failed, trying v2: {}", e.getMessage());
        }

        // Fallback to v2 API (with coverArt included)
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Accept", "application/vnd.api+json");
            headers.set("Client-Id", tidalProperties.getClientId());

            // Include coverArt relationship to get image data
            String url = "https://openapi.tidal.com/v2/playlists/" + playlistId + "?countryCode=" + countryCode
                    + "&include=coverArt";
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();
                log.info("[Tidal] v2 API response for {}: {}", playlistId,
                        body.toString().substring(0, Math.min(500, body.toString().length())));

                JsonNode data = body.has("data") ? body.path("data") : body;

                // Convert v2 format to v1-like format for compatibility
                ObjectNode result = objectMapper.createObjectNode();
                result.put("uuid", data.path("id").asText(playlistId));

                if (data.has("attributes")) {
                    JsonNode attrs = data.path("attributes");
                    result.put("title", attrs.path("name").asText(attrs.path("title").asText("")));
                    result.put("numberOfTracks", attrs.path("numberOfItems").asInt(
                            attrs.path("numberOfTracks").asInt(0)));
                    result.put("description", attrs.path("description").asText(""));

                    // Extract imageLinks for squareImage
                    JsonNode imageLinks = attrs.path("imageLinks");
                    if (imageLinks.isArray() && imageLinks.size() > 0) {
                        // Try to find 320x320 or square image first
                        for (JsonNode imgLink : imageLinks) {
                            String meta = imgLink.path("meta").asText("");
                            if (meta.contains("320x320") || meta.contains("square")) {
                                result.put("squareImage", imgLink.path("href").asText());
                                break;
                            }
                        }
                        // Fallback to first image if no square found
                        if (!result.has("squareImage") || result.path("squareImage").asText("").isEmpty()) {
                            result.put("squareImage", imageLinks.path(0).path("href").asText());
                        }
                    }
                }

                // Try to get image from included coverArt (various type names)
                if ((!result.has("squareImage") || result.path("squareImage").asText("").isEmpty())
                        && body.has("included") && body.path("included").isArray()) {
                    log.info("[Tidal] v2 API included array: {}", body.path("included").toString());

                    for (JsonNode inc : body.path("included")) {
                        String incType = inc.path("type").asText("");
                        // coverArt can be "images", "image", "artworks", "coverArt"
                        if (incType.contains("image") || incType.contains("artwork") || incType.contains("cover")) {
                            String href = "";

                            // FIRST: Try files array (actual Tidal v2 structure for artworks)
                            JsonNode files = inc.path("attributes").path("files");
                            if (files.isArray() && files.size() > 0) {
                                // Find 320x320 image first
                                for (JsonNode file : files) {
                                    int width = file.path("meta").path("width").asInt(0);
                                    if (width == 320) {
                                        href = file.path("href").asText("");
                                        if (!href.isEmpty()) {
                                            log.info("[Tidal] Found 320x320 coverArt from files[]: {}", href);
                                            break;
                                        }
                                    }
                                }
                                // Fallback to first file if no 320x320 found
                                if (href.isEmpty()) {
                                    href = files.get(0).path("href").asText("");
                                    if (!href.isEmpty()) {
                                        log.info("[Tidal] Found first coverArt from files[]: {}", href);
                                    }
                                }
                            }

                            // SECOND: Try direct href in attributes
                            if (href.isEmpty()) {
                                href = inc.path("attributes").path("href").asText("");
                            }
                            // THIRD: Try url in attributes
                            if (href.isEmpty()) {
                                href = inc.path("attributes").path("url").asText("");
                            }
                            // FOURTH: Try links.self
                            if (href.isEmpty()) {
                                href = inc.path("links").path("self").asText("");
                            }

                            if (!href.isEmpty()) {
                                log.info("[Tidal] Found coverArt URL from included[type={}]: {}", incType, href);
                                result.put("squareImage", href);
                                break;
                            }
                        }
                    }
                }

                return result;
            }
        } catch (Exception e) {
            log.warn("[Tidal] v2 fetchPlaylistInfo also failed: {}", e.getMessage());
        }

        // Fallback to v1 API
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

        // Try v2 API first
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Accept", "application/vnd.api+json");
            headers.set("Client-Id", tidalProperties.getClientId());

            String url = "https://openapi.tidal.com/v2/playlists/" + playlistId + "/relationships/items?countryCode="
                    + countryCode + "&include=items,items.artists,items.albums";
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();

                // v2 returns tracks in "included" array
                // First, build a map of artists by id for cross-referencing
                Map<String, String> artistMap = new HashMap<>();
                Map<String, JsonNode> albumMap = new HashMap<>();
                if (body.has("included") && body.path("included").isArray()) {
                    for (JsonNode inc : body.path("included")) {
                        String type = inc.path("type").asText();
                        String id = inc.path("id").asText();
                        if ("artists".equals(type) && inc.has("attributes")) {
                            artistMap.put(id, inc.path("attributes").path("name").asText("Unknown"));
                        } else if ("albums".equals(type)) {
                            albumMap.put(id, inc);
                        }
                    }
                }

                if (body.has("included") && body.path("included").isArray()) {
                    for (JsonNode inc : body.path("included")) {
                        if ("tracks".equals(inc.path("type").asText())) {
                            ObjectNode track = objectMapper.createObjectNode();
                            track.put("id", inc.path("id").asText());

                            if (inc.has("attributes")) {
                                JsonNode attrs = inc.path("attributes");
                                track.put("title", attrs.path("title").asText(""));
                                // v2 API returns ISO 8601 duration (e.g. "PT2M58S")
                                String durationStr = attrs.path("duration").asText("");
                                int durationSec = 0;
                                if (!durationStr.isEmpty()) {
                                    try {
                                        durationSec = (int) Duration.parse(durationStr).getSeconds();
                                    } catch (Exception e) {
                                        durationSec = attrs.path("duration").asInt(0);
                                    }
                                }
                                track.put("duration", durationSec);
                                track.put("trackNumber", attrs.path("trackNumber").asInt(0));
                                track.put("isrc", attrs.path("isrc").asText(""));
                            }

                            // Extract artist from relationships
                            String artistName = "Unknown";
                            if (inc.has("relationships") && inc.path("relationships").has("artists")) {
                                JsonNode artistsData = inc.path("relationships").path("artists").path("data");
                                if (artistsData.isArray() && artistsData.size() > 0) {
                                    String artistId = artistsData.get(0).path("id").asText();
                                    artistName = artistMap.getOrDefault(artistId, "Unknown");
                                }
                            }
                            ObjectNode artistNode = objectMapper.createObjectNode();
                            artistNode.put("name", artistName);
                            track.set("artist", artistNode);

                            // Extract album from relationships
                            String albumTitle = "Unknown";
                            String albumCover = null;
                            if (inc.has("relationships") && inc.path("relationships").has("albums")) {
                                JsonNode albumsData = inc.path("relationships").path("albums").path("data");
                                if (albumsData.isArray() && albumsData.size() > 0) {
                                    String albumId = albumsData.get(0).path("id").asText();
                                    JsonNode albumNode = albumMap.get(albumId);
                                    if (albumNode != null && albumNode.has("attributes")) {
                                        albumTitle = albumNode.path("attributes").path("title").asText("Unknown");
                                        albumCover = albumNode.path("attributes").path("imageCover").path(0).path("url").asText(null);
                                    }
                                }
                            }
                            ObjectNode albumNode = objectMapper.createObjectNode();
                            albumNode.put("title", albumTitle);
                            if (albumCover != null) {
                                albumNode.put("cover", albumCover);
                            }
                            track.set("album", albumNode);

                            // Wrap in item format for compatibility
                            ObjectNode item = objectMapper.createObjectNode();
                            item.set("item", track);
                            item.put("type", "track");
                            allItems.add(item);
                        }
                    }
                }

                if (!allItems.isEmpty()) {
                    log.info("[Tidal] v2 Total tracks fetched: {}", allItems.size());
                    return allItems;
                }
            }
        } catch (Exception e) {
            log.warn("[Tidal] v2 fetchPlaylistTracks failed, trying v1: {}", e.getMessage());
        }

        // Fallback to v1 API
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

                // DEBUG: Log first track's full structure
                if (allItems.isEmpty() && items.size() > 0) {
                    JsonNode firstItem = items.get(0);
                    log.info("[Tidal] FIRST TRACK RAW DATA: {}", firstItem.toString());

                    // Specifically check album.cover
                    JsonNode itemNode = firstItem.has("item") ? firstItem.path("item") : firstItem;
                    String albumCover = itemNode.path("album").path("cover").asText("MISSING");
                    String albumTitle = itemNode.path("album").path("title").asText("MISSING");
                    log.info("[Tidal] Track album.cover={}, album.title={}", albumCover, albumTitle);
                }

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

    /**
     * artist가 "Unknown"일 때 Tidal v1 API로 개별 트랙 조회하여 artist명 보정
     */
    private String fetchArtistFromTidalV1(String token, String tidalId, String countryCode) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Accept", "application/json");

            String url = tidalProperties.getApiUrl() + "/tracks/" + tidalId
                    + "?countryCode=" + (countryCode != null ? countryCode : "KR");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();
                String artist = body.path("artist").path("name").asText(null);
                if (artist != null && !artist.isEmpty() && !"Unknown".equals(artist)) {
                    return artist;
                }
                // artists 배열 fallback
                if (body.has("artists") && body.path("artists").isArray() && body.path("artists").size() > 0) {
                    artist = body.path("artists").get(0).path("name").asText(null);
                    if (artist != null && !artist.isEmpty() && !"Unknown".equals(artist)) {
                        return artist;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[Tidal] v1 track lookup failed for {}: {}", tidalId, e.getMessage());
        }
        return null;
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
            // v1 API는 표준 JSON Accept 헤더 사용
            headers.set("Accept", "application/json");

            List<TidalPlaylist> playlists = new ArrayList<>();
            List<TidalTrack> tracks = new ArrayList<>();

            // Tidal v1 API: /search/tracks 별도 호출
            String tracksUrl = String.format("%s/search/tracks?query=%s&limit=%d&countryCode=%s",
                    tidalProperties.getApiUrl(),
                    URLEncoder.encode(query, StandardCharsets.UTF_8),
                    limit,
                    countryCode);

            log.info("[Tidal] Search tracks URL: {}", tracksUrl);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> tracksResponse = restTemplate.exchange(tracksUrl, HttpMethod.GET, entity,
                    JsonNode.class);
            JsonNode tracksData = tracksResponse.getBody();

            log.info("[Tidal] Search tracks response: {}",
                    tracksData != null
                            ? tracksData.toString().substring(0, Math.min(1000, tracksData.toString().length()))
                            : "null");

            // Tidal v1 /search/tracks 응답 구조:
            // { "items": [...tracks...], "totalNumberOfItems": N, "limit": L, "offset": O }
            if (tracksData != null && tracksData.has("items")) {
                JsonNode trackItems = tracksData.path("items");
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

            log.info("[Tidal] Parsed {} tracks", tracks.size());

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
