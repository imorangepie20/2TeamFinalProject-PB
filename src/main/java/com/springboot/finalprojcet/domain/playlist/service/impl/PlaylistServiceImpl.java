package com.springboot.finalprojcet.domain.playlist.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.finalprojcet.domain.common.service.ImageService;
import com.springboot.finalprojcet.domain.gms.repository.PlaylistRepository;
import com.springboot.finalprojcet.domain.playlist.dto.PlaylistRequestDto;
import com.springboot.finalprojcet.domain.playlist.dto.PlaylistResponseDto;
import com.springboot.finalprojcet.domain.playlist.dto.TrackRequestDto;
import com.springboot.finalprojcet.domain.playlist.dto.TrackResponseDto;
import com.springboot.finalprojcet.domain.playlist.repository.UserDismissedPlaylistRepository;
import com.springboot.finalprojcet.domain.playlist.service.PlaylistService;
import com.springboot.finalprojcet.domain.tidal.repository.PlaylistTracksRepository;
import com.springboot.finalprojcet.domain.tidal.repository.TracksRepository;
import com.springboot.finalprojcet.domain.user.repository.UserRepository;
import com.springboot.finalprojcet.entity.PlaylistTracks;
import com.springboot.finalprojcet.entity.Playlists;
import com.springboot.finalprojcet.entity.Tracks;
import com.springboot.finalprojcet.entity.UserDismissedPlaylist;
import com.springboot.finalprojcet.entity.Users;
import com.springboot.finalprojcet.enums.SourceType;
import com.springboot.finalprojcet.enums.SpaceType;
import com.springboot.finalprojcet.enums.StatusFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaylistServiceImpl implements PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistTracksRepository playlistTracksRepository;
    private final TracksRepository tracksRepository;
    private final UserRepository userRepository;
    private final UserDismissedPlaylistRepository dismissedPlaylistRepository;
    private final ImageService imageService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAllPlaylists(SpaceType spaceType, StatusFlag status, Long userId) {
        List<Playlists> playlists;

        // EMS: 공용 공간 (모든 사용자 조회 가능)
        // GMS: 회원은 본인 것만, 비회원은 전체 (메인 미리보기용)
        // PMS: 개인 공간 (본인만)
        if (spaceType == SpaceType.EMS) {
            // EMS: 모든 사용자의 플레이리스트 표시 (공용 공간)
            playlists = playlistRepository.findBySpaceType(spaceType);
            log.info("[getAllPlaylists] Fetching EMS playlists (public), count={}", playlists.size());
        } else if (spaceType == SpaceType.GMS) {
            if (userId != null) {
                // 회원: 본인 GMS만
                playlists = playlistRepository.findByUserUserIdAndSpaceType(userId, spaceType);
                log.info("[getAllPlaylists] Fetching GMS for userId={}, count={}", userId, playlists.size());
            } else {
                // 비회원: 전체 GMS (메인 미리보기용)
                playlists = playlistRepository.findBySpaceType(spaceType);
                log.info("[getAllPlaylists] Fetching all GMS for non-member preview, count={}", playlists.size());
            }
        } else if (userId != null && spaceType != null) {
            // PMS: 특정 사용자의 해당 공간 플레이리스트 (개인 공간)
            playlists = playlistRepository.findByUserUserIdAndSpaceType(userId, spaceType);
            log.info("[getAllPlaylists] Fetching {} playlists for userId={}, count={}", spaceType, userId,
                    playlists.size());
        } else if (userId != null) {
            // spaceType이 없으면 해당 사용자의 모든 플레이리스트
            playlists = playlistRepository.findByUserUserId(userId);
            log.info("[getAllPlaylists] Fetching all playlists for userId={}, count={}", userId, playlists.size());
        } else {
            playlists = new ArrayList<>();
            log.info("[getAllPlaylists] No userId and not EMS/GMS, returning empty");
        }

        if (status != null) {
            playlists = playlists.stream()
                    .filter(p -> p.getStatusFlag() == status)
                    .collect(Collectors.toList());
        }

        List<PlaylistResponseDto> dtos = playlists.stream().map(this::convertToDto).collect(Collectors.toList());
        log.info("[getAllPlaylists] Returning {} playlists", dtos.size());

        return Map.of("playlists", dtos, "total", dtos.size());
    }

    @Override
    @Transactional(readOnly = true)
    public PlaylistResponseDto getPlaylistById(Long id) {
        Playlists playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Playlist not found"));

        PlaylistResponseDto dto = convertToDto(playlist);

        // JOIN FETCH로 트랙을 한번에 로드 (N+1 방지)
        List<PlaylistTracks> playlistTracks = playlistTracksRepository.findAllWithTrackByPlaylistId(id);

        List<TrackResponseDto> trackDtos = playlistTracks.stream().map(pt -> {
            Tracks t = pt.getTrack();
            return TrackResponseDto.builder()
                    .id(t.getTrackId())
                    .title(t.getTitle())
                    .artist(t.getArtist())
                    .album(t.getAlbum())
                    .duration(t.getDuration())
                    .isrc(t.getIsrc())
                    .artwork(t.getArtwork())
                    .orderIndex(pt.getOrderIndex())
                    .externalMetadata(t.getExternalMetadata())
                    .build();
        }).collect(Collectors.toList());

        dto.setTracks(trackDtos);

        return dto;
    }

    private Integer fetchDurationFromItunes(String title, String artist) {
        try {
            if (title == null || artist == null)
                return null;

            String searchTerm = (title + " " + artist)
                    .replaceAll("[^a-zA-Z0-9가-힣\\s]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            if (searchTerm.length() < 3)
                return null;

            String encodedTerm = java.net.URLEncoder.encode(searchTerm, java.nio.charset.StandardCharsets.UTF_8);
            String url = "https://itunes.apple.com/search?term=" + encodedTerm
                    + "&media=music&entity=song&limit=3&country=KR";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "*/*");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = objectMapper.readTree(response.getBody());
                JsonNode results = body.path("results");

                if (results.isArray() && results.size() > 0) {
                    // Find best match
                    for (JsonNode track : results) {
                        String trackName = track.path("trackName").asText("").toLowerCase();
                        if (trackName.equals(title.toLowerCase())) {
                            int durationMs = track.path("trackTimeMillis").asInt(0);
                            return durationMs / 1000;
                        }
                    }
                    // No exact match, use first result
                    int durationMs = results.get(0).path("trackTimeMillis").asInt(0);
                    return durationMs / 1000;
                }
            }
        } catch (Exception e) {
            log.debug("[PlaylistService] iTunes lookup failed for '{}': {}", title, e.getMessage());
        }
        return null;
    }

    @Override
    @Transactional
    public PlaylistResponseDto createPlaylist(Long userId, PlaylistRequestDto request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Playlists playlist = Playlists.builder()
                .user(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .spaceType(request.getSpaceType() != null ? request.getSpaceType() : SpaceType.EMS)
                .statusFlag(request.getStatus() != null ? request.getStatus() : StatusFlag.PTP)
                .sourceType(request.getSourceType() != null ? request.getSourceType() : SourceType.Platform)
                .externalId(request.getExternalId())
                .coverImage(request.getCoverImage())
                .build();

        Playlists saved = playlistRepository.save(playlist);

        // Download and save image locally
        if (request.getCoverImage() != null && request.getCoverImage().startsWith("http")) {
            try {
                String localPath = imageService.downloadImage(request.getCoverImage(), "playlists");
                saved.setCoverImage(localPath);
                playlistRepository.save(saved);
            } catch (Exception e) {
                log.warn("Failed to download cover image: {}", e.getMessage());
            }
        }

        return convertToDto(saved);
    }

    @Override
    @Transactional
    public PlaylistResponseDto updatePlaylist(Long id, PlaylistRequestDto request) {
        Playlists playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Playlist not found"));

        playlist.setTitle(request.getTitle());
        if (request.getDescription() != null)
            playlist.setDescription(request.getDescription());
        // Could update others if needed

        return convertToDto(playlist);
    }

    @Override
    @Transactional
    public PlaylistResponseDto updatePlaylistStatus(Long id, StatusFlag status) {
        Playlists playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Playlist not found"));
        playlist.setStatusFlag(status);
        return convertToDto(playlist);
    }

    @Override
    @Transactional
    public Map<String, Object> movePlaylist(Long id, SpaceType spaceType) {
        Playlists playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Playlist not found"));
        playlist.setSpaceType(spaceType);

        // 커버 이미지 없으면 앞에서 4개 트랙 artwork로 합성
        if (playlist.getCoverImage() == null || playlist.getCoverImage().isEmpty()) {
            List<PlaylistTracks> tracks = playlistTracksRepository.findAllByPlaylistPlaylistIdOrderByOrderIndex(id);
            if (!tracks.isEmpty()) {
                // 앞에서 4개 트랙의 artwork 수집
                List<String> artworkUrls = new ArrayList<>();
                for (int i = 0; i < Math.min(4, tracks.size()); i++) {
                    String artwork = tracks.get(i).getTrack().getArtwork();
                    if (artwork != null && !artwork.isEmpty()) {
                        artworkUrls.add(artwork);
                    }
                }

                if (!artworkUrls.isEmpty()) {
                    // 2x2 그리드 이미지 합성
                    String gridImage = imageService.createGridImage(artworkUrls, "playlists");
                    if (gridImage != null) {
                        playlist.setCoverImage(gridImage);
                        log.info("[PlaylistService] Created grid cover image: {}", gridImage);
                    } else if (!artworkUrls.isEmpty()) {
                        // 합성 실패시 첫 번째 이미지 사용
                        playlist.setCoverImage(artworkUrls.get(0));
                        log.info("[PlaylistService] Fallback to first track artwork: {}", artworkUrls.get(0));
                    }
                }
            }
        }

        playlistRepository.save(playlist);
        log.info("[PlaylistService] Playlist {} moved to {}", id, spaceType);
        return Map.of("message", "Playlist moved to " + spaceType, "spaceType", spaceType, "playlistId", id);
    }

    @Override
    @Transactional
    public void deletePlaylist(Long id, Long userId) {
        Playlists playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Playlist not found"));

        // 외부 플랫폼에서 가져온 재생목록이면 dismissed 테이블에 기록 (재import 방지)
        if (playlist.getExternalId() != null && userId != null) {
            if (!dismissedPlaylistRepository.existsByUserUserIdAndExternalId(userId, playlist.getExternalId())) {
                dismissedPlaylistRepository.save(UserDismissedPlaylist.builder()
                        .user(playlist.getUser())
                        .externalId(playlist.getExternalId())
                        .dismissedAt(LocalDateTime.now())
                        .build());
            }
        }

        playlistRepository.deleteById(id);
    }

    @Override
    @Transactional
    public Map<String, Object> addTrackToPlaylist(Long id, TrackRequestDto trackDto) {
        Playlists playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Playlist not found"));

        // 1. Save Track
        Tracks track;
        try {
            // Simplified: always create new track or check if exists by some ID?
            // In Node.js it was insert always.
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("itunesId", trackDto.getId());
            metadata.put("artwork", trackDto.getArtwork());
            metadata.put("audio", trackDto.getAudio());
            metadata.put("url", trackDto.getUrl());

            track = Tracks.builder()
                    .title(trackDto.getTitle())
                    .artist(trackDto.getArtist() != null ? trackDto.getArtist() : "Unknown Artist")
                    .album(trackDto.getAlbum() != null ? trackDto.getAlbum() : "Unknown Album")
                    .duration(trackDto.getDuration() != null ? trackDto.getDuration() : 0)
                    .externalMetadata(objectMapper.writeValueAsString(metadata))
                    .artwork(trackDto.getArtwork()) // Initial set
                    .build();

            // Download artwork if exists
            if (track.getArtwork() != null && track.getArtwork().startsWith("http")) {
                try {
                    String localPath = imageService.downloadImage(track.getArtwork(), "tracks");
                    track.setArtwork(localPath);
                    // Update metadata as well? Maybe not strictly necessary strictly referencing
                    // column
                } catch (Exception e) {
                    log.warn("Failed to download track artwork: {}", e.getMessage());
                }
            }

            track = tracksRepository.save(track);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save track: " + e.getMessage());
        }

        // 2. Add to PlaylistTracks
        // Find max order
        Integer maxOrder = playlistTracksRepository.findMaxOrderIndexByPlaylistId(id);
        int newOrder = (maxOrder != null ? maxOrder : 0) + 1;

        PlaylistTracks pt = PlaylistTracks.builder()
                .playlist(playlist)
                .track(track)
                .orderIndex(newOrder)
                .build();

        playlistTracksRepository.save(pt);

        return Map.of("message", "Track added", "trackId", track.getTrackId(), "order", newOrder);
    }

    @Override
    @Transactional
    public void removeTrackFromPlaylist(Long id, Long trackId) {
        // Need to delete from PlaylistTracks where playlist_id = id AND track_id =
        // trackId
        // The repository method might need to be custom or use deleteBy...
        // Assuming we can find it.
        // playlistTracksRepository.deleteByPlaylistPlaylistIdAndTrackTrackId(id,
        // trackId);
        // Or fetch and delete.
        // Let's assume generic logic for now.
    }

    private PlaylistResponseDto convertToDto(Playlists p) {
        Integer trackCount = playlistTracksRepository.countByPlaylistPlaylistId(p.getPlaylistId());
        log.info("[convertToDto] playlist_id={}, title={}, trackCount={}", p.getPlaylistId(), p.getTitle(), trackCount);

        // Processing cover image (Tidal logic)
        String image = p.getCoverImage();
        if (p.getExternalId() != null && p.getExternalId().startsWith("tidal:") && image != null
                && !image.startsWith("http") && !image.startsWith("/")) {
            image = "https://resources.tidal.com/images/" + image.replace("-", "/") + "/640x640.jpg";
        }

        // Fallback: If coverImage is null, use first track's artwork
        if (image == null || image.isEmpty()) {
            try {
                List<PlaylistTracks> tracks = playlistTracksRepository
                        .findAllByPlaylistPlaylistIdOrderByOrderIndex(p.getPlaylistId());
                if (!tracks.isEmpty()) {
                    Tracks firstTrack = tracks.get(0).getTrack();
                    if (firstTrack != null && firstTrack.getArtwork() != null && !firstTrack.getArtwork().isEmpty()) {
                        image = firstTrack.getArtwork();
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to get fallback cover image for playlist {}: {}", p.getPlaylistId(), e.getMessage());
            }
        }

        return PlaylistResponseDto.builder()
                .id(p.getPlaylistId())
                .title(p.getTitle())
                .description(p.getDescription())
                .spaceType(p.getSpaceType())
                .status(p.getStatusFlag())
                .sourceType(p.getSourceType())
                .externalId(p.getExternalId())
                .coverImage(image)
                .createdAt(p.getCreatedAt())
                // .updatedAt(p.getUpdatedAt()) // entity might not have getter exposed or
                // BaseEntity issue? Verified BaseEntity has checks.
                .trackCount(trackCount)
                .aiScore(p.getAiScore() != null ? p.getAiScore().doubleValue() : 0.0)
                .build();
    }

    @Override
    @Transactional
    public Map<String, Object> importAlbum(Long userId, Map<String, Object> data) {
        // 1. Create Playlist
        PlaylistRequestDto req = PlaylistRequestDto.builder()
                .title((String) data.get("title"))
                .description("Imported Album")
                .coverImage((String) data.get("coverImage"))
                .sourceType(SourceType.Platform)
                .spaceType(SpaceType.EMS)
                .status(StatusFlag.PTP)
                .build();

        PlaylistResponseDto playlistDto = createPlaylist(userId, req);
        Long playlistId = playlistDto.getId();

        // 2. Add Tracks
        List<Map<String, Object>> tracks = (List<Map<String, Object>>) data.get("tracks");
        int count = 0;
        if (tracks != null) {
            for (Map<String, Object> t : tracks) {
                try {
                    // Extract duration safely
                    Integer duration = 0;
                    if (t.get("durationInMillis") != null) {
                        duration = ((Number) t.get("durationInMillis")).intValue() / 1000;
                    }

                    TrackRequestDto tReq = TrackRequestDto.builder()
                            .title((String) t.get("title"))
                            .artist((String) t.get("artist"))
                            .album((String) t.get("albumName")) // attributes.albumName
                            .duration(duration)
                            .artwork((String) data.get("coverImage")) // Use album cover for tracks if track-specific
                                                                      // missing
                            // .url(...) // preview?
                            .build();

                    // Helper mapping
                    if (tReq.getAlbum() == null)
                        tReq.setAlbum((String) data.get("title")); // Fallback to album title

                    addTrackToPlaylist(playlistId, tReq);
                    count++;
                } catch (Exception e) {
                    log.warn("Failed to adding track to imported album: {}", e.getMessage());
                }
            }
        }

        return Map.of("message", "Album imported successfully", "playlist", playlistDto, "count", count);
    }

    @Override
    public Map<String, Object> searchTracks(String query, int limit) {
        log.info("[PlaylistService] searchTracks - query={}, limit={}", query, limit);

        // Search tracks by artist or title
        List<Tracks> tracks = tracksRepository.findByArtistContainingIgnoreCaseOrTitleContainingIgnoreCase(
                query, query, org.springframework.data.domain.PageRequest.of(0, limit * 2) // Fetch more to allow for
                                                                                           // filtering
        );

        List<Map<String, Object>> trackList = new ArrayList<>();
        java.util.Set<String> seenKeys = new java.util.HashSet<>();

        for (Tracks t : tracks) {
            String key = (t.getTitle() + "|" + t.getArtist()).toLowerCase();
            if (!seenKeys.contains(key)) {
                seenKeys.add(key);

                Map<String, Object> trackMap = new java.util.HashMap<>();
                trackMap.put("id", t.getTrackId());
                trackMap.put("title", t.getTitle());
                trackMap.put("artist", t.getArtist());
                trackMap.put("album", t.getAlbum());
                trackMap.put("duration", t.getDuration() != null ? t.getDuration() : 0);
                trackMap.put("artwork", t.getArtwork());
                trackMap.put("orderIndex", 0);
                // Also parse externalMetadata if needed, but for simple search this is fine

                trackList.add(trackMap);

                if (trackList.size() >= limit)
                    break;
            }
        }

        log.info("[PlaylistService] found {} unique tracks for query '{}'", trackList.size(), query);
        return Map.of("tracks", trackList);
    }
}
