package com.springboot.finalprojcet.domain.itunes.controller;

import com.springboot.finalprojcet.domain.itunes.service.ItunesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/itunes")
@Tag(name = "iTunes", description = "iTunes 음악 검색 및 추천 API")
@RequiredArgsConstructor
public class ItunesController {

    private final ItunesService itunesService;

    @GetMapping("/search")
    @Operation(summary = "음악 검색", description = "iTunes에서 음악을 검색합니다.")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String term,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "US") String country,
            @RequestParam(defaultValue = "song") String entity) {
        return ResponseEntity.ok(itunesService.searchMusic(term, limit, country, entity));
    }

    @GetMapping("/recommendations")
    @Operation(summary = "추천 앨범/음악", description = "iTunes 기반 추천 데이터를 조회합니다.")
    public ResponseEntity<Map<String, Object>> getRecommendations(
            @RequestParam(defaultValue = "US") String country,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String genre) {
        return ResponseEntity.ok(itunesService.getRecommendations(country, limit, genre));
    }

    @GetMapping("/album/{id}")
    @Operation(summary = "앨범 상세 조회", description = "앨범의 트랙 목록을 조회합니다.")
    public ResponseEntity<Map<String, Object>> getAlbum(
            @PathVariable String id,
            @RequestParam(defaultValue = "US") String country) {
        try {
            return ResponseEntity.ok(itunesService.getAlbumDetails(id, country));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
