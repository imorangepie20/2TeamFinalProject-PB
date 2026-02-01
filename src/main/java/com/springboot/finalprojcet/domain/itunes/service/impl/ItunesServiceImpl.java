package com.springboot.finalprojcet.domain.itunes.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.finalprojcet.domain.itunes.service.ItunesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItunesServiceImpl implements ItunesService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String ITUNES_API_URL = "https://itunes.apple.com";

    @Override
    public Map<String, Object> searchMusic(String term, int limit, String country, String entity) {
        if (term == null || term.trim().isEmpty()) {
            throw new IllegalArgumentException("Search term is required");
        }

        String url = UriComponentsBuilder.fromHttpUrl(ITUNES_API_URL + "/search")
                .queryParam("term", term)
                .queryParam("limit", limit)
                .queryParam("country", country)
                .queryParam("media", "music")
                .queryParam("entity", entity)
                .toUriString();

        try {
            String response = restTemplate.getForObject(url, String.class);
            Map<String, Object> data = objectMapper.readValue(response, new TypeReference<>() {
            });
            List<Map<String, Object>> resultsList = (List<Map<String, Object>>) data.get("results");

            if (resultsList == null)
                resultsList = Collections.emptyList();

            List<Map<String, Object>> transformed = resultsList.stream().map(item -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", item.get("trackId"));
                map.put("title", item.get("trackName"));
                map.put("artist", item.get("artistName"));
                map.put("album", item.get("collectionName"));
                String artwork = (String) item.get("artworkUrl100");
                if (artwork != null)
                    artwork = artwork.replace("100x100", "600x600");
                map.put("artwork", artwork);
                map.put("audio", item.get("previewUrl"));
                map.put("url", item.get("trackViewUrl"));
                map.put("date", item.get("releaseDate"));
                return map;
            }).collect(Collectors.toList());

            return Map.of("results", transformed);
        } catch (Exception e) {
            log.error("iTunes Search Error", e);
            throw new RuntimeException("iTunes API Error: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getRecommendations(String country, int limit, String genre) {
        List<String> queries = (genre != null && !genre.isEmpty())
                ? Collections.singletonList(genre)
                : Arrays.asList("K-Pop", "Top Hits", "Pop", "New Music", "Jazz");

        List<CompletableFuture<List<Map<String, Object>>>> futures = queries.stream()
                .map(query -> CompletableFuture.supplyAsync(() -> fetchRecommendationsForQuery(query, country)))
                .collect(Collectors.toList());

        List<Map<String, Object>> validResults = new ArrayList<>();
        Set<Object> seenIds = new HashSet<>();

        futures.forEach(future -> {
            try {
                List<Map<String, Object>> results = future.get(); // Blocking for simplicity in this step, or use join
                for (Map<String, Object> item : results) {
                    Object collectionId = item.get("collectionId");
                    if (collectionId != null && seenIds.add(collectionId)) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", collectionId);
                        map.put("title", item.get("collectionName"));
                        map.put("artist", item.get("artistName"));
                        String artwork = (String) item.get("artworkUrl100");
                        if (artwork != null)
                            artwork = artwork.replace("100x100", "600x600");
                        map.put("artwork", artwork);
                        map.put("count", item.get("trackCount"));
                        map.put("genre", item.get("primaryGenreName"));
                        map.put("date", item.get("releaseDate"));
                        map.put("link", item.get("collectionViewUrl"));
                        validResults.add(map);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch recs", e);
            }
        });

        Collections.shuffle(validResults);
        List<Map<String, Object>> limited = validResults.stream().limit(limit * 2L).collect(Collectors.toList());

        return Map.of("recommendations", limited);
    }

    private List<Map<String, Object>> fetchRecommendationsForQuery(String query, String country) {
        String url = UriComponentsBuilder.fromHttpUrl(ITUNES_API_URL + "/search")
                .queryParam("term", query)
                .queryParam("limit", 5)
                .queryParam("country", country)
                .queryParam("media", "music")
                .queryParam("entity", "album")
                .queryParam("attribute", "albumTerm")
                .toUriString();
        try {
            String response = restTemplate.getForObject(url, String.class);
            Map<String, Object> data = objectMapper.readValue(response, new TypeReference<>() {
            });
            return (List<Map<String, Object>>) data.get("results");
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getAlbumDetails(String id, String country) {
        Map<String, Object> result = fetchAlbumData(id, country);

        // Fallback logic for KR -> US
        if ((result == null || ((List) result.get("tracks")).isEmpty()) && "KR".equalsIgnoreCase(country)) {
            log.info("No tracks in KR for album {}, trying US...", id);
            result = fetchAlbumData(id, "US");
        }

        if (result == null) {
            throw new RuntimeException("Album not found");
        }
        return result;
    }

    private Map<String, Object> fetchAlbumData(String id, String country) {
        String url = UriComponentsBuilder.fromHttpUrl(ITUNES_API_URL + "/lookup")
                .queryParam("id", id)
                .queryParam("entity", "song")
                .queryParam("country", country)
                .toUriString();
        try {
            String response = restTemplate.getForObject(url, String.class);
            Map<String, Object> data = objectMapper.readValue(response, new TypeReference<>() {
            });
            List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");

            if (results == null || results.isEmpty())
                return null;

            Map<String, Object> collection = results.get(0);
            List<Map<String, Object>> tracks = results.stream().skip(1).map(item -> {
                Map<String, Object> t = new HashMap<>();
                t.put("id", item.get("trackId"));
                t.put("title", item.get("trackName"));
                t.put("artist", item.get("artistName"));
                t.put("album", item.get("collectionName"));
                String artwork = (String) item.get("artworkUrl100");
                if (artwork != null)
                    artwork = artwork.replace("100x100", "600x600");
                t.put("artwork", artwork);
                t.put("audio", item.get("previewUrl"));
                t.put("url", item.get("trackViewUrl"));
                t.put("duration", ((Number) item.get("trackTimeMillis")).longValue() / 1000);
                t.put("trackNumber", item.get("trackNumber"));
                return t;
            }).collect(Collectors.toList());

            Map<String, Object> album = new HashMap<>();
            album.put("id", collection.get("collectionId"));
            album.put("title", collection.get("collectionName"));
            album.put("artist", collection.get("artistName"));
            album.put("trackCount", collection.get("trackCount"));
            album.put("tracks", tracks);

            return album;
        } catch (Exception e) {
            return null;
        }
    }
}
