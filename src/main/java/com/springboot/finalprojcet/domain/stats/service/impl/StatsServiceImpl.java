package com.springboot.finalprojcet.domain.stats.service.impl;

import com.springboot.finalprojcet.domain.gms.repository.PlaylistRepository;
import com.springboot.finalprojcet.domain.stats.dto.HomeStatsResponseDto;
import com.springboot.finalprojcet.domain.stats.dto.StatsRequestDto;
import com.springboot.finalprojcet.domain.stats.repository.ArtistStatsRepository;
import com.springboot.finalprojcet.domain.stats.repository.ContentStatsRepository;
import com.springboot.finalprojcet.domain.stats.service.StatsService;
import com.springboot.finalprojcet.domain.tidal.repository.TracksRepository;
import com.springboot.finalprojcet.entity.ArtistStats;
import com.springboot.finalprojcet.entity.ContentStats;
import com.springboot.finalprojcet.entity.ContentStatsId;
import com.springboot.finalprojcet.entity.Tracks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsServiceImpl implements StatsService {

    private final ContentStatsRepository contentStatsRepository;
    private final ArtistStatsRepository artistStatsRepository;
    private final PlaylistRepository playlistRepository;
    private final TracksRepository tracksRepository;

    @Override
    @Transactional
    public void recordView(StatsRequestDto request) {
        if ("artist".equals(request.getContentType()) && request.getArtistName() != null) {
            ArtistStats stats = artistStatsRepository.findById(request.getArtistName())
                    .orElse(ArtistStats.builder().artistName(request.getArtistName()).viewCount(0L).playCount(0L)
                            .likeCount(0L).build());
            stats.setViewCount(stats.getViewCount() + 1);
            artistStatsRepository.save(stats);
        } else if (request.getContentId() != null) {
            ContentStatsId id = new ContentStatsId(request.getContentType(), request.getContentId());
            ContentStats stats = contentStatsRepository.findById(id)
                    .orElse(ContentStats.builder().id(id).viewCount(0L).playCount(0L).likeCount(0L).build());
            stats.setViewCount(stats.getViewCount() + 1);
            contentStatsRepository.save(stats);
        }
    }

    @Override
    @Transactional
    public void recordPlay(StatsRequestDto request) {
        if ("artist".equals(request.getContentType()) && request.getArtistName() != null) {
            updateArtistPlay(request.getArtistName());
        } else if (request.getContentId() != null) {
            ContentStatsId id = new ContentStatsId(request.getContentType(), request.getContentId());
            ContentStats stats = contentStatsRepository.findById(id)
                    .orElse(ContentStats.builder().id(id).viewCount(0L).playCount(0L).likeCount(0L).build());
            stats.setPlayCount(stats.getPlayCount() + 1);
            contentStatsRepository.save(stats);

            // If track, also update artist stats
            if ("track".equals(request.getContentType())) {
                tracksRepository.findById(request.getContentId()).ifPresent(track -> {
                    if (track.getArtist() != null) {
                        updateArtistPlay(track.getArtist());
                    }
                });
            }
        }
    }

    private void updateArtistPlay(String artistName) {
        ArtistStats stats = artistStatsRepository.findById(artistName)
                .orElse(ArtistStats.builder().artistName(artistName).viewCount(0L).playCount(0L).likeCount(0L).build());
        stats.setPlayCount(stats.getPlayCount() + 1);
        artistStatsRepository.save(stats);
    }

    @Override
    @Transactional
    public void toggleLike(StatsRequestDto request) {
        long increment = Boolean.TRUE.equals(request.getIsLiked()) ? 1 : -1;

        if ("artist".equals(request.getContentType()) && request.getArtistName() != null) {
            ArtistStats stats = artistStatsRepository.findById(request.getArtistName())
                    .orElse(ArtistStats.builder().artistName(request.getArtistName()).viewCount(0L).playCount(0L)
                            .likeCount(0L).build());
            long newCount = Math.max(0, stats.getLikeCount() + increment);
            stats.setLikeCount(newCount);
            artistStatsRepository.save(stats);
        } else if (request.getContentId() != null) {
            ContentStatsId id = new ContentStatsId(request.getContentType(), request.getContentId());
            ContentStats stats = contentStatsRepository.findById(id)
                    .orElse(ContentStats.builder().id(id).viewCount(0L).playCount(0L).likeCount(0L).build());
            long newCount = Math.max(0, stats.getLikeCount() + increment);
            stats.setLikeCount(newCount);
            contentStatsRepository.save(stats);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HomeStatsResponseDto getHomeStats() {
        long totalPlaylists = playlistRepository.count();
        long totalTracks = tracksRepository.count();
        // In real app, AI pending count and total likes would need specific queries.
        // Simplified for MVP:
        long aiPending = 0; // Implement query if needed
        long likes = 0; // Implement query if needed

        return HomeStatsResponseDto.builder()
                .totalPlaylists(totalPlaylists)
                .totalTracks(totalTracks)
                .aiPending(aiPending)
                .likes(likes)
                .build();
    }

    @Override
    public Map<String, Object> getBestPlaylists(int limit, String sortBy) {
        // Requires complex join query. Returning empty for MVP structure.
        return Map.of("playlists", java.util.Collections.emptyList());
    }

    @Override
    public Map<String, Object> getBestTracks(int limit, String sortBy) {
        // Requires complex join query. Returning empty for MVP structure.
        return Map.of("tracks", java.util.Collections.emptyList());
    }

    @Override
    public Map<String, Object> getBestArtists(int limit, String sortBy) {
        java.util.List<ArtistStats> artistStats;

        // Use ORDER BY RAND() for random selection from database
        if ("random".equals(sortBy)) {
            artistStats = artistStatsRepository.findRandomArtists(limit);
        } else if ("view_count".equals(sortBy)) {
            artistStats = artistStatsRepository.findTopArtistsByViewCount(limit);
        } else if ("like_count".equals(sortBy)) {
            artistStats = artistStatsRepository.findTopArtistsByLikeCount(limit);
        } else {
            // Default: play_count
            artistStats = artistStatsRepository.findTopArtistsByPlayCount(limit);
        }

        java.util.List<Map<String, Object>> artists = artistStats.stream()
                .map(stats -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", stats.getArtistName());
                    map.put("playCount", stats.getPlayCount());
                    map.put("viewCount", stats.getViewCount());
                    map.put("likeCount", stats.getLikeCount());
                    return map;
                })
                .collect(java.util.stream.Collectors.toList());

        return Map.of("artists", artists);
    }

    @Override
    public Map<String, Object> getBestAlbums(int limit) {
        // Requires complex join query. Returning empty for MVP structure.
        return Map.of("albums", java.util.Collections.emptyList());
    }
}
