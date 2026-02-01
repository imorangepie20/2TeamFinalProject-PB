package com.springboot.finalprojcet.domain.spotify.controller;

import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.spotify.service.SpotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/spotify")
@Tag(name = "Spotify", description = "Spotify 연동 및 데이터 가져오기")
@RequiredArgsConstructor
public class SpotifyController {

    private final SpotifyService spotifyService;

    @GetMapping("/auth/login")
    @Operation(summary = "로그인 URL 생성", description = "Spotify OAuth 로그인 URL을 생성합니다.")
    public ResponseEntity<Map<String, Object>> getLoginUrl(@RequestParam(required = false) String visitorId) {
        return ResponseEntity.ok(spotifyService.getLoginUrl(visitorId));
    }

    @PostMapping("/auth/exchange")
    @Operation(summary = "토큰 교환", description = "Authorization Code를 Access Token으로 교환합니다.")
    public ResponseEntity<Map<String, Object>> exchangeToken(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(spotifyService.exchangeToken(
                body.get("code"), body.get("state"), body.get("redirectUri")));
    }

    @PostMapping("/token/connect")
    @Operation(summary = "토큰 직접 연결", description = "Access Token을 직접 입력하여 연결합니다.")
    public ResponseEntity<Map<String, Object>> connectWithToken(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(spotifyService.connectWithToken(
                body.get("visitorId"), body.get("accessToken")));
    }

    @GetMapping("/auth/status")
    @Operation(summary = "연결 상태 확인", description = "Spotify 연결 여부를 확인합니다.")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam(required = false) String visitorId) {
        return ResponseEntity.ok(spotifyService.getTokenStatus(visitorId));
    }

    @GetMapping("/token/status")
    public ResponseEntity<Map<String, Object>> getTokenStatus(@RequestParam(required = false) String visitorId) {
        return ResponseEntity.ok(spotifyService.getTokenStatus(visitorId));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body) {
        spotifyService.disconnectToken(body.get("visitorId"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/token/disconnect")
    public ResponseEntity<Void> disconnect(@RequestBody Map<String, String> body) {
        spotifyService.disconnectToken(body.get("visitorId"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/playlists")
    @Operation(summary = "플레이리스트 조회", description = "사용자의 Spotify 플레이리스트 목록을 조회합니다.")
    public ResponseEntity<Map<String, Object>> getPlaylists(
            @RequestParam(required = false) String visitorId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(spotifyService.getPlaylists(visitorId, limit, offset));
    }

    @GetMapping("/token/playlists")
    public ResponseEntity<Map<String, Object>> getTokenPlaylists(
            @RequestParam(required = false) String visitorId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(spotifyService.getPlaylists(visitorId, limit, offset));
    }

    @GetMapping("/playlists/{id}/tracks")
    public ResponseEntity<Map<String, Object>> getPlaylistTracks(
            @PathVariable String id,
            @RequestParam(required = false) String visitorId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(spotifyService.getPlaylistTracks(id, visitorId, limit, offset));
    }

    @PostMapping("/import")
    @Operation(summary = "플레이리스트 가져오기", description = "Spotify 플레이리스트를 PMS로 가져옵니다.")
    public ResponseEntity<Map<String, Object>> importPlaylist(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) { // Optional based on Auth config
        Long userId = null;
        if (userDetails != null)
            userId = userDetails.getUser().getUserId();
        // Fallback or explicit param
        if (body.get("userId") != null)
            userId = ((Number) body.get("userId")).longValue();

        return ResponseEntity.ok(spotifyService.importPlaylist(
                (String) body.get("visitorId"), (String) body.get("playlistId"), userId));
    }

    @PostMapping("/token/import")
    public ResponseEntity<Map<String, Object>> importTokenPlaylist(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = null;
        if (userDetails != null)
            userId = userDetails.getUser().getUserId();
        if (body.get("userId") != null)
            userId = ((Number) body.get("userId")).longValue();

        return ResponseEntity.ok(spotifyService.importPlaylist(
                (String) body.get("visitorId"), (String) body.get("playlistId"), userId));
    }
}
