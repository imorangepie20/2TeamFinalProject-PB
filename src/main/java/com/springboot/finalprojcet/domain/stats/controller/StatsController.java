package com.springboot.finalprojcet.domain.stats.controller;

import com.springboot.finalprojcet.domain.stats.dto.HomeStatsResponseDto;
import com.springboot.finalprojcet.domain.stats.dto.StatsRequestDto;
import com.springboot.finalprojcet.domain.stats.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "Stats", description = "통계 및 분석 API")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @PostMapping("/view")
    @Operation(summary = "조회수 기록", description = "콘텐츠의 조회수를 증가시킵니다.")
    public ResponseEntity<Map<String, Boolean>> recordView(@RequestBody StatsRequestDto request) {
        statsService.recordView(request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/play")
    @Operation(summary = "재생수 기록", description = "콘텐츠의 재생수를 증가시킵니다.")
    public ResponseEntity<Map<String, Boolean>> recordPlay(@RequestBody StatsRequestDto request) {
        statsService.recordPlay(request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/like")
    @Operation(summary = "좋아요 토글", description = "콘텐츠의 좋아요 상태를 토글합니다.")
    public ResponseEntity<Map<String, Boolean>> toggleLike(@RequestBody StatsRequestDto request) {
        statsService.toggleLike(request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/home")
    @Operation(summary = "홈 통계", description = "홈 화면에 표시할 전체 통계 데이터를 반환합니다.")
    public ResponseEntity<HomeStatsResponseDto> getHomeStats() {
        return ResponseEntity.ok(statsService.getHomeStats());
    }

    @GetMapping("/best/playlists")
    public ResponseEntity<Map<String, Object>> getBestPlaylists(
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "play_count") String sortBy) {
        return ResponseEntity.ok(statsService.getBestPlaylists(limit, sortBy));
    }

    @GetMapping("/best/tracks")
    public ResponseEntity<Map<String, Object>> getBestTracks(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "play_count") String sortBy) {
        return ResponseEntity.ok(statsService.getBestTracks(limit, sortBy));
    }

    @GetMapping("/best/artists")
    public ResponseEntity<Map<String, Object>> getBestArtists(
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "play_count") String sortBy) {
        return ResponseEntity.ok(statsService.getBestArtists(limit, sortBy));
    }

    @GetMapping("/best/albums")
    public ResponseEntity<Map<String, Object>> getBestAlbums(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(statsService.getBestAlbums(limit));
    }
}
