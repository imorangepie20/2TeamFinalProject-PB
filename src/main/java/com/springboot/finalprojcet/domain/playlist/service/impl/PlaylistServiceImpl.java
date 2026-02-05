package com.springboot.finalprojcet.domain.playlist.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.finalprojcet.domain.common.service.ImageService;
import com.springboot.finalprojcet.domain.gms.repository.PlaylistRepository;
import com.springboot.finalprojcet.domain.playlist.dto.PlaylistRequestDto;
import com.springboot.finalprojcet.domain.playlist.dto.PlaylistResponseDto;
import com.springboot.finalprojcet.domain.playlist.dto.TrackRequestDto;
import com.springboot.finalprojcet.domain.playlist.dto.TrackResponseDto;
import com.springboot.finalprojcet.domain.playlist.service.PlaylistService;
import com.springboot.finalprojcet.domain.tidal.repository.PlaylistTracksRepository;
import com.springboot.finalprojcet.domain.tidal.repository.TracksRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ImageService imageService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAllPlaylists(SpaceType spaceType, StatusFlag status, Long userId) {
        List<Playlists> playlists;

        // EMS만 사용자 독립적 (공용 공간)
        // GMS, PMS는 사용자별 필터링
        if (spaceType == SpaceType.EMS) {
            // EMS: 모든 사용자의 EMS 플레이리스트 표시
            playlists = playlistRepository.findBySpaceType(SpaceType.EMS);
            log.info("[getAllPlaylists] Fetching EMS playlists (user-independent), count={}", playlists.size());
        } else if (userId != null && spaceType != null) {
            // GMS/PMS: 특정 사용자의 해당 공간 플레이리스트
            playlists = playlistRepository.findByUserUserIdAndSpaceType(userId, spaceType);
            log.info("[getAllPlaylists] Fetching {} playlists for userId={}, count={}", spaceType, userId, playlists.size());
        } else if (userId != null) {
            // spaceType이 없으면 해당 사용자의 모든 플레이리스트
            playlists = playlistRepository.findByUserUserId(userId);
            log.info("[getAllPlaylists] Fetching all playlists for userId={}, count={}", userId, playlists.size());
        } else {
            playlists = new ArrayList<>();
            log.info("[getAllPlaylists] No userId and not EMS, returning empty");
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

        // Fetch tracks
        List<PlaylistTracks> playlistTracks = playlistTracksRepository.findAllByPlaylistPlaylistIdOrderByOrderIndex(id);
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

        // Populate tracks in DTO
        dto.setTracks(trackDtos);

        return dto;
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
        return Map.of("message", "Playlist moved to " + spaceType, "spaceType", spaceType);
    }

    @Override
    @Transactional
    public void deletePlaylist(Long id) {
        if (!playlistRepository.existsById(id)) {
            throw new RuntimeException("Playlist not found");
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
}
