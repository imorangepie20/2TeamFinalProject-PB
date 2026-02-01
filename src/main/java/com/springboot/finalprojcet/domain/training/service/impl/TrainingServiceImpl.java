package com.springboot.finalprojcet.domain.training.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.finalprojcet.domain.spotify.service.SpotifyService;
import com.springboot.finalprojcet.domain.training.service.TrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrainingServiceImpl implements TrainingService {

    private final JdbcTemplate jdbcTemplate;
    private final SpotifyService spotifyService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${lastfm.api-key:}")
    private String lastFmApiKey;

    @Override
    public Map<String, Object> getUserTrainingData(Long userId, boolean includeMetadata) {
        String sql = "SELECT p.playlist_id, p.title, p.description, p.space_type, p.status_flag, p.source_type, p.created_at "
                +
                "FROM playlists p WHERE p.user_id = ? ORDER BY p.created_at DESC";

        List<Map<String, Object>> playlists = jdbcTemplate.queryForList(sql, userId);

        List<Map<String, Object>> trainingData = new ArrayList<>();

        for (Map<String, Object> p : playlists) {
            Long playlistId = ((Number) p.get("playlist_id")).longValue();

            String trackSql = "SELECT t.track_id, t.title, t.artist, t.album, t.duration, t.isrc, t.external_metadata, pt.order_index, pt.added_at "
                    +
                    "FROM playlist_tracks pt JOIN tracks t ON pt.track_id = t.track_id " +
                    "WHERE pt.playlist_id = ? ORDER BY pt.order_index";

            List<Map<String, Object>> tracks = jdbcTemplate.queryForList(trackSql, playlistId);

            List<Map<String, Object>> parsedTracks = tracks.stream().map(t -> {
                Map<String, Object> map = new HashMap<>(t);
                if (includeMetadata) {
                    try {
                        String meta = (String) t.get("external_metadata");
                        if (meta != null)
                            map.put("external_metadata", objectMapper.readValue(meta, Map.class));
                    } catch (Exception e) {
                        map.put("external_metadata", Map.of());
                    }
                } else {
                    map.remove("external_metadata");
                }
                return map;
            }).collect(Collectors.toList());

            Map<String, Object> pMap = new HashMap<>(p);
            pMap.put("trackCount", tracks.size());
            pMap.put("tracks", parsedTracks);
            trainingData.add(pMap);
        }

        int totalTracks = trainingData.stream().mapToInt(p -> (int) p.get("trackCount")).sum();

        return Map.of(
                "userId", userId,
                "totalPlaylists", playlists.size(),
                "totalTracks", totalTracks,
                "data", trainingData);
    }

    @Override
    public Object exportTrainingData(Long userId, String format) {
        StringBuilder sql = new StringBuilder(
                "SELECT p.user_id, p.playlist_id, p.title as playlist_title, p.space_type, p.status_flag, p.source_type, "
                        +
                        "t.track_id, t.title as track_title, t.artist, t.album, t.duration, t.isrc, t.genre, t.audio_features, t.external_metadata, "
                        +
                        "pt.order_index, " +
                        "COALESCE(r.rating, 0) as user_rating, " +
                        "COALESCE(tsi.ai_score, 0) as track_score, " +
                        "COALESCE(psi.ai_score, 0) as playlist_score " +
                        "FROM playlists p " +
                        "JOIN playlist_tracks pt ON p.playlist_id = pt.playlist_id " +
                        "JOIN tracks t ON pt.track_id = t.track_id " +
                        "LEFT JOIN user_track_ratings r ON t.track_id = r.track_id AND p.user_id = r.user_id " +
                        "LEFT JOIN track_scored_id tsi ON t.track_id = tsi.track_id AND p.user_id = tsi.user_id " +
                        "LEFT JOIN playlist_scored_id psi ON p.playlist_id = psi.playlist_id AND p.user_id = psi.user_id ");

        List<Object> params = new ArrayList<>();
        if (userId != null) {
            sql.append("WHERE p.user_id = ? ");
            params.add(userId);
        }
        sql.append("ORDER BY p.user_id, p.playlist_id, pt.order_index");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        // Parse JSON fields
        List<Map<String, Object>> parsedData = rows.stream().map(row -> {
            Map<String, Object> newRow = new HashMap<>(row);
            try {
                String meta = (String) row.get("external_metadata");
                if (meta != null)
                    newRow.put("external_metadata", objectMapper.readValue(meta, Map.class));
            } catch (Exception e) {
            }

            try {
                String audio = (String) row.get("audio_features");
                if (audio != null) {
                    Map<String, Object> af = objectMapper.readValue(audio, Map.class);
                    // Check if MusicBrainz tags
                    if (af.containsKey("tags")) {
                        newRow.put("tags", ((List) af.get("tags")).stream().collect(Collectors.joining(", ")));
                        newRow.put("audio_features", null);
                    } else {
                        newRow.put("audio_features", af);
                    }
                }
            } catch (Exception e) {
            }
            return newRow;
        }).collect(Collectors.toList());

        if ("csv".equalsIgnoreCase(format)) {
            String[] headers = {
                    "user_id", "playlist_id", "playlist_title", "space_type", "status_flag", "source_type",
                    "track_id", "track_title", "artist", "album", "duration", "isrc", "genre", "tags",
                    "order_index", "user_rating", "track_score", "playlist_score"
            };

            StringBuilder csv = new StringBuilder();
            csv.append(String.join(",", headers)).append("\n");

            for (Map<String, Object> row : parsedData) {
                List<String> values = new ArrayList<>();
                for (String h : headers) {
                    Object val = row.get(h);
                    if (val == null)
                        val = "";
                    String sVal = val.toString();
                    if (sVal.contains(",") || sVal.contains("\"")) {
                        sVal = "\"" + sVal.replace("\"", "\"\"") + "\"";
                    }
                    values.add(sVal);
                }
                csv.append(String.join(",", values)).append("\n");
            }
            return csv.toString();
        }

        return Map.of(
                "totalRecords", parsedData.size(),
                "exportedAt", new Date(),
                "data", parsedData);
    }

    @Override
    public Map<String, Object> getFeatures(Long userId) {
        String userFilter = userId != null ? " AND p.user_id = " + userId : "";

        // Top Artists
        String artistSql = "SELECT t.artist, COUNT(*) as frequency " +
                "FROM tracks t JOIN playlist_tracks pt ON t.track_id = pt.track_id " +
                "JOIN playlists p ON pt.playlist_id = p.playlist_id " +
                "WHERE 1=1 " + userFilter +
                " GROUP BY t.artist ORDER BY frequency DESC LIMIT 50";
        List<Map<String, Object>> artistStats = jdbcTemplate.queryForList(artistSql);

        // Stats
        String durationSql = "SELECT AVG(t.duration) as avg, MIN(t.duration) as min, MAX(t.duration) as max, SUM(t.duration) as total "
                +
                "FROM tracks t JOIN playlist_tracks pt ON t.track_id = pt.track_id " +
                "JOIN playlists p ON pt.playlist_id = p.playlist_id " +
                "WHERE 1=1 " + userFilter;
        Map<String, Object> durationStats = jdbcTemplate.queryForMap(durationSql);

        return Map.of(
                "features", Map.of(
                        "topArtists", artistStats,
                        "durationStats", durationStats));
    }

    @Override
    @Transactional
    public Map<String, Object> saveScores(Long userId, List<Map<String, Object>> scores) {
        int pUpdated = 0;
        int tUpdated = 0;

        for (Map<String, Object> score : scores) {
            String type = (String) score.get("type");
            Number valNum = (Number) score.get("score");
            Double val = valNum != null ? valNum.doubleValue() : 0.0;

            if ("playlist".equals(type)) {
                Long pid = ((Number) score.get("playlistId")).longValue();
                jdbcTemplate.update(
                        "INSERT INTO playlist_scored_id (playlist_id, user_id, ai_score) VALUES (?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE ai_score = ?, updated_at = CURRENT_TIMESTAMP",
                        pid, userId, val, val);
                pUpdated++;
            } else if ("track".equals(type)) {
                Long tid = ((Number) score.get("trackId")).longValue();
                jdbcTemplate.update("INSERT INTO track_scored_id (track_id, user_id, ai_score) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE ai_score = ?, updated_at = CURRENT_TIMESTAMP",
                        tid, userId, val, val);
                tUpdated++;
            }
        }
        return Map.of("message", "Scores saved", "playlistUpdated", pUpdated, "trackUpdated", tUpdated);
    }

    @Override
    public Map<String, Object> getInteractions(Long userId, int limit) {
        String sql = "SELECT p.user_id, t.track_id, t.artist, t.album, 1 as interaction, pt.added_at as timestamp " +
                "FROM playlist_tracks pt JOIN playlists p ON pt.playlist_id = p.playlist_id " +
                "JOIN tracks t ON pt.track_id = t.track_id " +
                "WHERE p.user_id = ? ORDER BY pt.added_at DESC LIMIT ?";
        List<Map<String, Object>> data = jdbcTemplate.queryForList(sql, userId, limit);
        return Map.of("totalInteractions", data.size(), "data", data);
    }

    @Override
    public Map<String, Object> collectFeatures(List<Long> trackIds, int limit) {
        List<Map<String, Object>> tracks;
        if (trackIds != null && !trackIds.isEmpty()) {
            StringBuilder qs = new StringBuilder();
            trackIds.forEach(id -> qs.append("?,"));
            String placeholders = qs.substring(0, qs.length() - 1);
            tracks = jdbcTemplate.queryForList(
                    "SELECT track_id, isrc FROM tracks WHERE track_id IN (" + placeholders + ") AND isrc IS NOT NULL",
                    trackIds.toArray());
        } else {
            tracks = jdbcTemplate.queryForList(
                    "SELECT track_id, isrc FROM tracks WHERE audio_features IS NULL AND isrc IS NOT NULL LIMIT ?",
                    limit);
        }

        int success = 0;
        int failed = 0;

        for (Map<String, Object> t : tracks) {
            try {
                Long tid = ((Number) t.get("track_id")).longValue();
                String isrc = (String) t.get("isrc");

                Map<String, Object> features = spotifyService.getAudioFeatures(isrc);
                if (features != null) {
                    List<String> genres = (List<String>) features.get("genres");
                    String genreStr = (genres != null && !genres.isEmpty()) ? String.join(", ", genres) : null;
                    String json = objectMapper.writeValueAsString(features.get("audioFeatures"));

                    jdbcTemplate.update("UPDATE tracks SET genre = ?, audio_features = ? WHERE track_id = ?",
                            genreStr, json, tid);
                    success++;
                } else {
                    failed++;
                }
                Thread.sleep(100); // Rate limit
            } catch (Exception e) {
                failed++;
                log.error("Feature collection failed", e);
            }
        }

        return Map.of("message", "Completed", "processed", tracks.size(), "success", success, "failed", failed);
    }

    // MusicBrainz & LastImpl omitted for brevity in MVP (Using simplified logic)
    @Override
    public Map<String, Object> collectGenres(List<Long> trackIds, int limit) {
        // Placeholder for MusicBrainz logic
        return Map.of("message", "Not implemented in this phase");
    }

    @Override
    public Map<String, Object> getFeaturesStatus() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tracks", Long.class);
        Long features = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tracks WHERE audio_features IS NOT NULL",
                Long.class);
        return Map.of("total", total, "withAudioFeatures", features);
    }

    @Override
    public Map<String, Object> submitRating(Long userId, Long trackId, int rating) {
        jdbcTemplate.update("INSERT INTO user_track_ratings (user_id, track_id, rating) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE rating = ?, updated_at = CURRENT_TIMESTAMP",
                userId, trackId, rating, rating);
        return Map.of("message", "Saved");
    }

    @Override
    public Map<String, Object> getRatings(Long userId, int limit) {
        String sql = "SELECT * FROM user_track_ratings WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        return Map.of("ratings", jdbcTemplate.queryForList(sql, userId, limit));
    }
}
