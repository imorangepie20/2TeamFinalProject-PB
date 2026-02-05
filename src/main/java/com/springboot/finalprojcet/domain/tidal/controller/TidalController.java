package com.springboot.finalprojcet.domain.tidal.controller;

import com.springboot.finalprojcet.domain.tidal.dto.*;
import com.springboot.finalprojcet.domain.tidal.service.TidalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tidal")
@Tag(name = "Tidal", description = "Tidal 연동 API")
@RequiredArgsConstructor
public class TidalController {

    private final TidalService tidalService;

    @GetMapping("/auth/login-url")
    @Operation(summary = "Tidal OAuth URL 생성", description = "PKCE를 사용한 Tidal OAuth 인증 URL을 생성합니다.")
    public ResponseEntity<TidalLoginUrlResponse> getLoginUrl(
            @RequestParam(required = false) String visitorId,
            HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) {
            String referer = request.getHeader("Referer");
            if (referer != null) {
                try {
                    java.net.URL url = new java.net.URL(referer);
                    origin = url.getProtocol() + "://" + url.getHost() + (url.getPort() > 0 ? ":" + url.getPort() : "");
                } catch (Exception e) {
                    origin = "http://localhost";
                }
            }
        }
        return ResponseEntity.ok(tidalService.getLoginUrl(visitorId, origin));
    }

    @PostMapping("/auth/exchange")
    @Operation(summary = "OAuth 코드 교환", description = "OAuth 인증 코드를 액세스 토큰으로 교환합니다.")
    public ResponseEntity<TidalExchangeResponse> exchangeCode(
            @RequestBody TidalExchangeRequest request,
            HttpServletRequest httpRequest) {
        String origin = httpRequest.getHeader("Origin");
        return ResponseEntity.ok(tidalService.exchangeCode(request, origin));
    }

    @GetMapping("/auth/status")
    @Operation(summary = "인증 상태 확인", description = "현재 Tidal 인증 상태를 확인합니다.")
    public ResponseEntity<TidalAuthStatusResponse> getAuthStatus(
            @RequestParam(required = false) String visitorId) {
        return ResponseEntity.ok(tidalService.getAuthStatus(visitorId));
    }

    @PostMapping("/auth/logout")
    @Operation(summary = "로그아웃", description = "Tidal 연결을 해제합니다.")
    public ResponseEntity<Map<String, Boolean>> logout(@RequestBody(required = false) Map<String, String> body) {
        String visitorId = body != null ? body.get("visitorId") : null;
        tidalService.logout(visitorId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/auth/device")
    @Operation(summary = "기기 인증 초기화", description = "Tidal 기기 인증(Device Code Flow)을 시작합니다.")
    public ResponseEntity<TidalDeviceAuthResponse> initDeviceAuth() {
        return ResponseEntity.ok(tidalService.initDeviceAuth());
    }

    @PostMapping("/auth/token")
    @Operation(summary = "토큰 폴링", description = "기기 인증 코드로 토큰을 폴링합니다.")
    public ResponseEntity<TidalTokenPollResponse> pollToken(@RequestBody TidalTokenPollRequest request) {
        return ResponseEntity.ok(tidalService.pollToken(request));
    }

    @GetMapping("/user/playlists")
    @Operation(summary = "사용자 플레이리스트 조회", description = "Tidal에서 사용자의 플레이리스트를 조회합니다.")
    public ResponseEntity<TidalPlaylistResponse> getUserPlaylists(
            @RequestParam(required = false) String visitorId) {
        return ResponseEntity.ok(tidalService.getUserPlaylists(visitorId));
    }

    @PostMapping("/import")
    @Operation(summary = "플레이리스트 가져오기", description = "Tidal 플레이리스트를 PMS로 가져옵니다.")
    public ResponseEntity<TidalImportResponse> importPlaylist(@RequestBody TidalImportRequest request) {
        return ResponseEntity.ok(tidalService.importPlaylist(request));
    }

    @PostMapping("/sync")
    @Operation(summary = "동기화", description = "Tidal 동기화")
    public ResponseEntity<TidalSyncResponse> syncTidal(
            @RequestBody TidalSyncRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.springboot.finalprojcet.domain.auth.service.CustomUserDetails userDetails) {
        return ResponseEntity.ok(tidalService.syncTidal(userDetails.getUser().getUserId(), request));
    }

    @GetMapping("/playlists/{id}/items")
    @Operation(summary = "플레이리스트 트랙 조회", description = "특정 플레이리스트의 트랙 목록을 조회합니다.")
    public ResponseEntity<Object> getPlaylistTracks(
            @PathVariable String id,
            @RequestParam(required = false) String countryCode,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(tidalService.getPlaylistTracks(id, countryCode, limit, offset));
    }

    @GetMapping("/featured")
    @Operation(summary = "추천 플레이리스트", description = "Tidal 추천 플레이리스트를 조회합니다.")
    public ResponseEntity<TidalFeaturedResponse> getFeatured() {
        return ResponseEntity.ok(tidalService.getFeatured());
    }

    @GetMapping("/search")
    @Operation(summary = "검색", description = "Tidal에서 트랙이나 플레이리스트를 검색합니다.")
    public ResponseEntity<TidalSearchResponse> search(
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "TRACKS,PLAYLISTS") String type,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String visitorId) {
        // visitorId resolution logic similar to other endpoints if needed, or pass
        // directly
        return ResponseEntity.ok(tidalService.search(query, type, limit, null, visitorId));
    }

    @GetMapping("/tracks/{trackId}/stream")
    @Operation(summary = "트랙 스트리밍 URL", description = "Tidal 트랙의 스트리밍 URL을 가져옵니다.")
    public ResponseEntity<TidalStreamUrlResponse> getStreamUrl(
            @PathVariable String trackId,
            @RequestParam(required = false) String visitorId,
            @RequestParam(required = false, defaultValue = "LOSSLESS") String quality) {
        return ResponseEntity.ok(tidalService.getStreamUrl(trackId, visitorId, quality));
    }
}
