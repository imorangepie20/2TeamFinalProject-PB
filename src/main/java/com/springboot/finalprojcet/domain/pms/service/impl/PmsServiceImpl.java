package com.springboot.finalprojcet.domain.pms.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.finalprojcet.domain.gms.repository.PlaylistRepository;
import com.springboot.finalprojcet.domain.pms.service.PmsService;
import com.springboot.finalprojcet.domain.tidal.repository.PlaylistTracksRepository;
import com.springboot.finalprojcet.domain.tidal.repository.TracksRepository;
import com.springboot.finalprojcet.domain.user.repository.UserRepository;
import com.springboot.finalprojcet.entity.Playlists;
import com.springboot.finalprojcet.entity.Tracks;
import com.springboot.finalprojcet.enums.SpaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PmsServiceImpl implements PmsService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistTracksRepository playlistTracksRepository;
    private final TracksRepository tracksRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getPmsStats(Long userId) {
        // Fetch all PMS playlists for user
        List<Playlists> playlists = playlistRepository.findByUserUserId(userId).stream()
                .filter(p -> p.getSpaceType() == SpaceType.PMS)
                .collect(Collectors.toList());

        long playlistCount = playlists.size();

        // This is inefficient loop, real impl should use custom JPQL queries in
        // Repository for stats
        // But for MVP migration, we can aggregate in memory if data size is small-ish,
        // OR add custom queries to Repository.
        // Given existing repositories allow finding by playlist, we can iterate.
        // Better approach: Add custom queries to StatsRepository or here.
        // For now, I'll assume small data or iterate.

        long trackCount = 0;
        long totalDuration = 0;
        Map<String, Integer> artistCounts = new HashMap<>(); // Rough count
        Map<String, Integer> genreCounts = new HashMap<>();

        for (Playlists p : playlists) {
            var tracks = playlistTracksRepository.findAllByPlaylistPlaylistIdOrderByOrderIndex(p.getPlaylistId());
            trackCount += tracks.size();
            for (var pt : tracks) {
                Tracks t = pt.getTrack();
                totalDuration += t.getDuration();

                String artist = t.getArtist();
                if (artist != null)
                    artistCounts.merge(artist, 1, Integer::sum);

                String genre = t.getGenre();
                if (genre != null)
                    genreCounts.merge(genre, 1, Integer::sum);
            }
        }

        // Top Artists
        List<Map<String, Object>> topArtists = artistCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("artist", e.getKey());
                    m.put("trackCount", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        // Genre Distribution
        List<Map<String, Object>> genreDistribution = genreCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("genre", e.getKey());
                    m.put("trackCount", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> stats = new HashMap<>();
        stats.put("playlists", playlistCount);
        stats.put("tracks", trackCount);
        stats.put("artists", artistCounts.size());
        stats.put("totalDurationSeconds", totalDuration);
        stats.put("totalDurationFormatted", formatDuration(totalDuration));

        return Map.of(
                "userId", userId,
                "spaceType", "PMS",
                "stats", stats,
                "topArtists", topArtists,
                "genreDistribution", genreDistribution);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getPlaylistLinks(Long userId) {
        List<Playlists> playlists = playlistRepository.findByUserUserId(userId).stream()
                .filter(p -> p.getSpaceType() == SpaceType.PMS)
                .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                .collect(Collectors.toList());

        List<Map<String, Object>> links = playlists.stream().map(p -> {
            int count = playlistTracksRepository.countByPlaylistPlaylistId(p.getPlaylistId());
            // baseUrl should be handled by controller ideally, but here we can return
            // relative paths
            Map<String, Object> map = new HashMap<>();
            map.put("playlistId", p.getPlaylistId());
            map.put("title", p.getTitle());
            map.put("trackCount", count);
            map.put("csvUrl", "/api/pms/playlist/" + p.getPlaylistId() + "/export?format=csv");
            map.put("jsonUrl", "/api/pms/playlist/" + p.getPlaylistId() + "/export?format=json");
            return map;
        }).collect(Collectors.toList());

        return Map.of(
                "userId", userId,
                "total", links.size(),
                "playlists", links);
    }

    @Override
    @Transactional(readOnly = true)
    public Object exportPmsData(Long userId, String format) {
        // Fetch all data for export
        // Similar to getPmsStats but detailed list.
        // For CSV/JSON export.

        List<Map<String, Object>> flatData = new ArrayList<>();

        List<Playlists> playlists = playlistRepository.findByUserUserId(userId).stream()
                .filter(p -> p.getSpaceType() == SpaceType.PMS)
                .collect(Collectors.toList());

        for (Playlists p : playlists) {
            var tracks = playlistTracksRepository.findAllByPlaylistPlaylistIdOrderByOrderIndex(p.getPlaylistId());
            for (var pt : tracks) {
                Tracks t = pt.getTrack();
                Map<String, Object> row = new HashMap<>();
                row.put("userId", userId);
                row.put("playlistId", p.getPlaylistId());
                row.put("playlistTitle", p.getTitle());
                row.put("sourceType", p.getSourceType());
                row.put("trackId", t.getTrackId());
                row.put("trackTitle", t.getTitle());
                row.put("artist", t.getArtist());
                row.put("album", t.getAlbum());
                row.put("duration", t.getDuration());
                row.put("isrc", t.getIsrc());
                row.put("genre", t.getGenre());
                row.put("orderIndex", pt.getOrderIndex());
                // rating, scores are missing in basic entities need joins or extra fetches
                // Skipping rating/scores for now or need to add Repository lookups

                flatData.add(row);
            }
        }

        if ("csv".equalsIgnoreCase(format)) {
            return generateCsv(flatData);
        }

        return Map.of(
                "userId", userId,
                "spaceType", "PMS",
                "totalRecords", flatData.size(),
                "exportedAt", java.time.LocalDateTime.now().toString(),
                "data", flatData);
    }

    @Override
    @Transactional(readOnly = true)
    public Object exportPlaylist(Long playlistId, String format) {
        Playlists p = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new RuntimeException("Playlist not found"));

        List<Map<String, Object>> flatData = new ArrayList<>();
        var tracks = playlistTracksRepository.findAllByPlaylistPlaylistIdOrderByOrderIndex(playlistId);

        for (var pt : tracks) {
            Tracks t = pt.getTrack();
            Map<String, Object> row = new HashMap<>();
            row.put("trackId", t.getTrackId());
            row.put("title", t.getTitle());
            row.put("artist", t.getArtist());
            row.put("album", t.getAlbum());
            row.put("duration", t.getDuration());
            row.put("isrc", t.getIsrc());
            row.put("genre", t.getGenre());
            row.put("orderIndex", pt.getOrderIndex());
            flatData.add(row);
        }

        if ("csv".equalsIgnoreCase(format)) {
            return generateCsv(flatData);
        }

        return Map.of(
                "playlistId", playlistId,
                "playlistTitle", p.getTitle(),
                "totalTracks", flatData.size(),
                "exportedAt", java.time.LocalDateTime.now().toString(),
                "data", flatData);
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%d:%02d", minutes, secs);
    }

    private String generateCsv(List<Map<String, Object>> data) {
        if (data.isEmpty())
            return "";

        // Headers
        List<String> headers = new ArrayList<>(data.get(0).keySet());
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", headers)).append("\n");

        for (Map<String, Object> row : data) {
            List<String> values = headers.stream().map(h -> {
                Object val = row.get(h);
                if (val == null)
                    return "";
                String s = val.toString();
                if (s.contains(",") || s.contains("\n") || s.contains("\"")) {
                    return "\"" + s.replace("\"", "\"\"") + "\"";
                }
                return s;
            }).collect(Collectors.toList());
            csv.append(String.join(",", values)).append("\n");
        }
        return "\uFEFF" + csv.toString(); // BOM
    }
}
