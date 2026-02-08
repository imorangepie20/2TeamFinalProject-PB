package com.springboot.finalprojcet.domain.ems.controller;

import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.ems.service.EmsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ems")
@Tag(name = "EMS", description = "EMS 전용 기능 (추천, 특별전, 통계)")
@RequiredArgsConstructor
public class EmsController {

    private final EmsService emsService;

    @GetMapping("/stats")
    @Operation(summary = "EMS 통계", description = "EMS 공간의 종합 통계를 조회합니다.")
    public ResponseEntity<Map<String, Object>> getStats(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(emsService.getEmsStats(userDetails.getUser().getUserId()));
    }

    @GetMapping("/recommendations")
    @Operation(summary = "추천 트랙", description = "사용자 취향 기반 추천 트랙을 조회합니다.")
    public ResponseEntity<Map<String, Object>> getRecommendations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(emsService.getRecommendations(userDetails.getUser().getUserId(), limit));
    }

    @GetMapping("/spotify-special")
    @Operation(summary = "Spotify 특별전", description = "Spotify 특별전 플레이리스트를 카테고리별로 조회합니다.")
    public ResponseEntity<Map<String, Object>> getSpotifySpecial() {
        return ResponseEntity.ok(emsService.getSpotifySpecial());
    }

    @GetMapping("/playlists/links")
    @Operation(summary = "플레이리스트 링크 목록", description = "내보내기 가능한 플레이리스트 링크들을 조회합니다.")
    public ResponseEntity<Map<String, Object>> getPlaylistLinks(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(emsService.getPlaylistLinks(userDetails.getUser().getUserId()));
    }

    @GetMapping("/export")
    @Operation(summary = "EMS 전체 데이터 내보내기", description = "EMS의 모든 데이터를 JSON 또는 CSV로 내보냅니다.")
    public ResponseEntity<?> exportData(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "json") String format) {

        Object result = emsService.exportEmsData(userDetails.getUser().getUserId(), format);

        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ems_data.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/playlist/{playlistId}/export")
    @Operation(summary = "개별 플레이리스트 내보내기", description = "특정 플레이리스트를 JSON 또는 CSV로 내보냅니다.")
    public ResponseEntity<?> exportPlaylist(
            @PathVariable Long playlistId,
            @RequestParam(defaultValue = "json") String format) {

        Object result = emsService.exportPlaylist(playlistId, format);

        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"playlist_" + playlistId + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/migrate-tracks")
    @Operation(summary = "EMS 트랙 메타데이터 마이그레이션", description = "duration, artist, album 정보가 없는 EMS 트랙들을 Tidal API로 업데이트합니다.")
    public ResponseEntity<Map<String, Object>> migrateTracks() {
        return ResponseEntity.ok(emsService.migrateEmsTracks());
    }
}
