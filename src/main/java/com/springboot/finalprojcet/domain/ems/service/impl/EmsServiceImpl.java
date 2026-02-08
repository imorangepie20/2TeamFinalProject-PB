package com.springboot.finalprojcet.domain.ems.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.finalprojcet.domain.gms.repository.PlaylistRepository;
import com.springboot.finalprojcet.domain.ems.service.EmsService;
import com.springboot.finalprojcet.domain.tidal.config.TidalProperties;
import com.springboot.finalprojcet.domain.tidal.repository.PlaylistTracksRepository;
import com.springboot.finalprojcet.domain.tidal.repository.TracksRepository;
import com.springboot.finalprojcet.entity.Playlists;
import com.springboot.finalprojcet.entity.Tracks;
import com.springboot.finalprojcet.enums.SpaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmsServiceImpl implements EmsService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistTracksRepository playlistTracksRepository;
    private final TracksRepository tracksRepository;
    private final ObjectMapper objectMapper;
    private final TidalProperties tidalProperties;
    private final RestTemplate restTemplate;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getEmsStats(Long userId) {
        List<Playlists> playlists = playlistRepository.findByUserUserId(userId).stream()
                .filter(p -> p.getSpaceType() == SpaceType.EMS)
                .collect(Collectors.toList());

        long playlistCount = playlists.size();
        long trackCount = 0;
        long totalDuration = 0;
        Map<String, Integer> artistCounts = new HashMap<>();
        Map<String, Integer> genreCounts = new HashMap<>();

        // This iteration can be slow for large data. Consider custom JPQL.
        for (Playlists p : playlists) {
            var tracks = playlistTracksRepository.findAllByPlaylistPlaylistIdOrderByOrderIndex(p.getPlaylistId());
            trackCount += tracks.size();
            for (var pt : tracks) {
                Tracks t = pt.getTrack();
                totalDuration += t.getDuration();

                if (t.getArtist() != null)
                    artistCounts.merge(t.getArtist(), 1, Integer::sum);
                if (t.getGenre() != null)
                    genreCounts.merge(t.getGenre(), 1, Integer::sum);
            }
        }

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
                "spaceType", "EMS",
                "stats", stats,
                "topArtists", topArtists,
                "genreDistribution", genreDistribution);
    }

    @Override
    @Transactional
    public Map<String, Object> getRecommendations(Long userId, int limit) {
        // Get random tracks for recommendations
        List<Tracks> allTracks = tracksRepository.findAll();
        Collections.shuffle(allTracks);

        List<Tracks> selectedTracks = allTracks.stream().limit(limit).collect(Collectors.toList());

        // Fill missing duration for selected tracks (lazy loading)
        List<Tracks> tracksToUpdate = new ArrayList<>();
        for (Tracks t : selectedTracks) {
            if (t.getDuration() == null || t.getDuration() == 0) {
                Map<String, Object> itunesData = fetchTrackFromItunes(t.getTitle(), t.getArtist());
                if (itunesData != null && itunesData.containsKey("duration")) {
                    int duration = ((Number) itunesData.get("duration")).intValue();
                    if (duration > 0) {
                        t.setDuration(duration);
                        tracksToUpdate.add(t);
                        log.debug("[EMS] Filled duration for '{}' - {}s", t.getTitle(), duration);
                    }
                }
            }
        }

        // Save updated tracks
        if (!tracksToUpdate.isEmpty()) {
            tracksRepository.saveAll(tracksToUpdate);
            log.info("[EMS] Updated {} tracks with duration info", tracksToUpdate.size());
        }

        List<Map<String, Object>> recommendations = selectedTracks.stream()
                .map(t -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("trackId", t.getTrackId());
                    m.put("title", t.getTitle());
                    m.put("artist", t.getArtist());
                    m.put("album", t.getAlbum());
                    m.put("duration", t.getDuration());
                    m.put("genre", t.getGenre());
                    m.put("artwork", t.getArtwork());
                    m.put("recommendReason", "random_discovery");
                    return m;
                }).collect(Collectors.toList());

        return Map.of(
                "userId", userId,
                "recommendations", recommendations,
                "total", recommendations.size());
    }

    @Override
    @Transactional
    public Map<String, Object> getSpotifySpecial() {
        // Filter playlists by description "SPOTIFY_SPECIAL"
        List<Playlists> allPlaylists = playlistRepository.findAll();
        List<Playlists> specialPlaylists = allPlaylists.stream()
                .filter(p -> p.getDescription() != null && p.getDescription().contains("SPOTIFY_SPECIAL"))
                .collect(Collectors.toList());

        log.info("EmsService: Found {} total playlists, {} special playlists", allPlaylists.size(),
                specialPlaylists.size());

        // Categorization logic
        Map<String, List<Map<String, Object>>> categories = new HashMap<>();

        for (Playlists p : specialPlaylists) {
            String cat = "Other";
            String title = p.getTitle();
            if (title == null)
                title = ""; // safety

            if (matches(title, "KPOP", "kpop", "K-Pop"))
                cat = "K-POP";
            else if (matches(title, "R&B", "감성"))
                cat = "R&B";
            else if (matches(title, "hip", "Hip", "외힙"))
                cat = "Hip-Hop";
            else if (matches(title, "Party", "파티"))
                cat = "Party";
            else if (matches(title, "WORKOUT", "운동"))
                cat = "Workout";
            else if (matches(title, "Study", "공부"))
                cat = "Study";
            else if (matches(title, "Acoustic", "어쿠스틱"))
                cat = "Acoustic";
            else if (matches(title, "Starbucks", "Cafe", "카페"))
                cat = "Cafe";
            else if (matches(title, "Latino", "Latin"))
                cat = "Latin";
            else if (matches(title, "EDM", "Electronic"))
                cat = "EDM";
            else if (matches(title, "Classical", "클래식"))
                cat = "Classical";

            int count = playlistTracksRepository.countByPlaylistPlaylistId(p.getPlaylistId());

            Map<String, Object> pMap = new HashMap<>();
            pMap.put("playlistId", p.getPlaylistId());
            pMap.put("title", p.getTitle());
            pMap.put("description", p.getDescription());
            pMap.put("coverImage", p.getCoverImage()); // Should process Tial image if needed
            pMap.put("trackCount", count);
            pMap.put("category", cat);

            categories.computeIfAbsent(cat, k -> new ArrayList<>()).add(pMap);
        }

        long totalTracks = specialPlaylists.stream()
                .mapToLong(p -> playlistTracksRepository.countByPlaylistPlaylistId(p.getPlaylistId()))
                .sum();

        // Get Top 10 Tracks from these playlists
        List<Map<String, Object>> hotTracks = new ArrayList<>();
        Set<Long> addedTrackIds = new HashSet<>();
        List<Tracks> tracksToUpdate = new ArrayList<>();

        for (Playlists p : specialPlaylists) {
            var tracks = playlistTracksRepository.findAllByPlaylistPlaylistIdOrderByOrderIndex(p.getPlaylistId());
            for (var pt : tracks) {
                if (hotTracks.size() >= 10)
                    break;
                Tracks t = pt.getTrack();
                if (addedTrackIds.contains(t.getTrackId()))
                    continue;

                // Fill missing duration (lazy loading)
                if (t.getDuration() == null || t.getDuration() == 0) {
                    Map<String, Object> itunesData = fetchTrackFromItunes(t.getTitle(), t.getArtist());
                    if (itunesData != null && itunesData.containsKey("duration")) {
                        int duration = ((Number) itunesData.get("duration")).intValue();
                        if (duration > 0) {
                            t.setDuration(duration);
                            tracksToUpdate.add(t);
                        }
                    }
                }

                Map<String, Object> tMap = new HashMap<>();
                tMap.put("trackId", t.getTrackId());
                tMap.put("title", t.getTitle());
                tMap.put("artist", t.getArtist());
                tMap.put("album", t.getAlbum());
                tMap.put("duration", t.getDuration());
                tMap.put("artwork", t.getArtwork() != null ? t.getArtwork() : p.getCoverImage());
                tMap.put("popularity", 50 + (int) (Math.random() * 50));

                hotTracks.add(tMap);
                addedTrackIds.add(t.getTrackId());
            }
            if (hotTracks.size() >= 10)
                break;
        }

        // Save updated tracks
        if (!tracksToUpdate.isEmpty()) {
            tracksRepository.saveAll(tracksToUpdate);
            log.info("[EMS] Updated {} hot tracks with duration info", tracksToUpdate.size());
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPlaylists", specialPlaylists.size());
        stats.put("totalTracks", totalTracks);
        stats.put("hotTracks", hotTracks.size());

        return Map.of(
                "event", Map.of(
                        "title", "🎧 Spotify 특별전",
                        "subtitle", "2026 New Year Special Collection",
                        "description", "Spotify의 엄선된 플레이리스트를 MusicSpace에서 만나보세요!"),
                "stats", stats,
                "hotTracks", hotTracks,
                "categories", categories,
                "playlists", specialPlaylists.stream().map(p -> p.getPlaylistId()).collect(Collectors.toList()));
    }

    private boolean matches(String target, String... keywords) {
        for (String k : keywords) {
            if (target.contains(k))
                return true;
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public Object exportEmsData(Long userId, String format) {
        List<Map<String, Object>> flatData = new ArrayList<>();

        List<Playlists> playlists = playlistRepository.findByUserUserId(userId).stream()
                .filter(p -> p.getSpaceType() == SpaceType.EMS)
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
                flatData.add(row);
            }
        }

        if ("csv".equalsIgnoreCase(format)) {
            return generateCsv(flatData);
        }

        return Map.of(
                "userId", userId,
                "spaceType", "EMS",
                "totalRecords", flatData.size(),
                "exportedAt", java.time.LocalDateTime.now().toString(),
                "data", flatData);
    }

    @Override
    @Transactional(readOnly = true)
    public Object exportPlaylist(Long playlistId, String format) {
        // Same logic as PMS, can refactor to shared util later
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

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getPlaylistLinks(Long userId) {
        List<Playlists> playlists = playlistRepository.findByUserUserId(userId).stream()
                .filter(p -> p.getSpaceType() == SpaceType.EMS)
                .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                .collect(Collectors.toList());

        List<Map<String, Object>> links = playlists.stream().map(p -> {
            int count = playlistTracksRepository.countByPlaylistPlaylistId(p.getPlaylistId());
            Map<String, Object> map = new HashMap<>();
            map.put("playlistId", p.getPlaylistId());
            map.put("title", p.getTitle());
            map.put("trackCount", count);
            map.put("csvUrl", "/api/ems/playlist/" + p.getPlaylistId() + "/export?format=csv");
            map.put("jsonUrl", "/api/ems/playlist/" + p.getPlaylistId() + "/export?format=json");
            return map;
        }).collect(Collectors.toList());

        return Map.of(
                "userId", userId,
                "total", links.size(),
                "playlists", links);
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
        return "\uFEFF" + csv.toString();
    }

    @Override
    @Transactional
    public Map<String, Object> migrateEmsTracks() {
        log.info("[EMS Migration] Starting track metadata migration...");

        // Find all EMS tracks without duration
        List<Tracks> tracksToMigrate = tracksRepository.findEmsTracksWithoutDuration();
        log.info("[EMS Migration] Found {} tracks without duration", tracksToMigrate.size());

        int updated = 0;
        int failed = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (Tracks track : tracksToMigrate) {
            try {
                // First try Tidal API if tidalId exists
                String tidalId = extractTidalId(track);
                Map<String, Object> trackData = null;

                if (tidalId != null && !tidalId.isEmpty()) {
                    trackData = fetchTrackFromTidal(tidalId);
                }

                // If no tidalId or Tidal failed, try iTunes search by title+artist
                if (trackData == null && track.getTitle() != null && track.getArtist() != null) {
                    trackData = fetchTrackFromItunes(track.getTitle(), track.getArtist());
                }

                if (trackData != null) {
                    // Update track metadata
                    if (trackData.containsKey("duration") && trackData.get("duration") != null) {
                        int duration = ((Number) trackData.get("duration")).intValue();
                        if (duration > 0) {
                            track.setDuration(duration);
                        }
                    }
                    if (trackData.containsKey("artist") && (track.getArtist() == null || "Unknown".equals(track.getArtist()))) {
                        track.setArtist((String) trackData.get("artist"));
                    }
                    if (trackData.containsKey("album") && track.getAlbum() == null) {
                        track.setAlbum((String) trackData.get("album"));
                    }

                    if (track.getDuration() != null && track.getDuration() > 0) {
                        tracksRepository.save(track);
                        updated++;

                        if (updated % 50 == 0) {
                            log.info("[EMS Migration] Progress: {} updated, {} failed, {} skipped", updated, failed, skipped);
                        }
                    } else {
                        skipped++;
                    }
                } else {
                    skipped++;
                }

                // Rate limiting - 500ms between requests (iTunes rate limit is strict)
                Thread.sleep(500);

            } catch (Exception e) {
                failed++;
                if (errors.size() < 10) {
                    errors.add("Track " + track.getTrackId() + ": " + e.getMessage());
                }
            }
        }

        log.info("[EMS Migration] Complete! Updated: {}, Failed: {}, Skipped: {}", updated, failed, skipped);

        return Map.of(
                "status", "complete",
                "total", tracksToMigrate.size(),
                "updated", updated,
                "failed", failed,
                "skipped", skipped,
                "errors", errors
        );
    }

    private String extractTidalId(Tracks track) {
        try {
            String metadata = track.getExternalMetadata();
            if (metadata == null || metadata.isEmpty()) {
                return null;
            }

            JsonNode node = objectMapper.readTree(metadata);
            if (node.has("tidalId")) {
                return node.get("tidalId").asText();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> fetchTrackFromTidal(String tidalId) {
        try {
            // Use Tidal v1 API (doesn't require user auth for basic track info)
            String url = tidalProperties.getApiUrl() + "/tracks/" + tidalId + "?countryCode=KR";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("x-tidal-token", tidalProperties.getClientId());

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();

                Map<String, Object> result = new HashMap<>();
                result.put("duration", body.path("duration").asInt(0));
                result.put("isrc", body.path("isrc").asText(null));

                // Artist from nested object
                if (body.has("artist") && body.path("artist").has("name")) {
                    result.put("artist", body.path("artist").path("name").asText("Unknown"));
                } else if (body.has("artists") && body.path("artists").isArray() && body.path("artists").size() > 0) {
                    result.put("artist", body.path("artists").get(0).path("name").asText("Unknown"));
                }

                // Album from nested object
                if (body.has("album") && body.path("album").has("title")) {
                    result.put("album", body.path("album").path("title").asText(null));
                }

                return result;
            }
        } catch (Exception e) {
            log.warn("[EMS Migration] Failed to fetch track {}: {}", tidalId, e.getMessage());
        }
        return null;
    }

    private Map<String, Object> fetchTrackFromItunes(String title, String artist) {
        // Retry with exponential backoff
        int maxRetries = 3;
        int delay = 500; // Start with 500ms

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                // Clean up search terms
                String searchTerm = (title + " " + artist)
                        .replaceAll("[^a-zA-Z0-9가-힣\\s]", " ")
                        .replaceAll("\\s+", " ")
                        .trim();

                if (searchTerm.length() < 3) {
                    return null;
                }

                String encodedTerm = java.net.URLEncoder.encode(searchTerm, java.nio.charset.StandardCharsets.UTF_8);
                String url = "https://itunes.apple.com/search?term=" + encodedTerm
                        + "&media=music&entity=song&limit=3&country=KR";

                // Use String response and parse manually (iTunes returns text/javascript)
                HttpHeaders headers = new HttpHeaders();
                headers.set("Accept", "*/*");

                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode body = objectMapper.readTree(response.getBody());
                    JsonNode results = body.path("results");

                    if (results.isArray() && results.size() > 0) {
                        // Find best match - prefer exact title match
                        JsonNode bestMatch = null;
                        for (JsonNode track : results) {
                            String trackName = track.path("trackName").asText("").toLowerCase();
                            if (trackName.equals(title.toLowerCase())) {
                                bestMatch = track;
                                break;
                            }
                            if (bestMatch == null) {
                                bestMatch = track;
                            }
                        }

                        if (bestMatch != null) {
                            Map<String, Object> result = new HashMap<>();
                            // iTunes returns duration in milliseconds
                            int durationMs = bestMatch.path("trackTimeMillis").asInt(0);
                            result.put("duration", durationMs / 1000); // Convert to seconds
                            result.put("artist", bestMatch.path("artistName").asText(null));
                            result.put("album", bestMatch.path("collectionName").asText(null));
                            return result;
                        }
                    }
                    return null; // Success but no results
                }
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("429") || msg.contains("Too Many Requests"))) {
                    // Rate limited - wait and retry
                    try {
                        Thread.sleep(delay);
                        delay *= 2; // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    // Other error, don't retry
                    if (attempt == 0) {
                        log.debug("[EMS Migration] iTunes search failed for '{}' by '{}': {}", title, artist, msg);
                    }
                    return null;
                }
            }
        }
        return null;
    }
}
