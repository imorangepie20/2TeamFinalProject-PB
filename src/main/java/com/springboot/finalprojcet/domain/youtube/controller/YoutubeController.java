package com.springboot.finalprojcet.domain.youtube.controller;

import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.youtube.service.YoutubeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "YouTube", description = "YouTube 및 YouTube Music 연동 API")
@RequiredArgsConstructor
public class YoutubeController {

    private final YoutubeService youtubeService;

    // --- Public Search (YouTube) ---
    @GetMapping("/youtube/search")
    @Operation(summary = "YouTube 검색", description = "YouTube에서 동영상을 검색합니다.")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam("q") String term,
            @RequestParam(defaultValue = "1") int maxResults) {
        // Frontend sends 'q' instead of 'term'
        return ResponseEntity.ok(youtubeService.searchVideo(term, maxResults));
    }

    // --- YouTube Music OAuth ---

    @GetMapping("/youtube-music/auth/login")
    @Operation(summary = "로그인 URL 생성", description = "Google OAuth 로그인 URL을 생성합니다.")
    public ResponseEntity<Map<String, Object>> getLoginUrl(
            @RequestParam(required = false) String visitorId,
            @RequestParam(required = false) String redirectUri) {
        return ResponseEntity.ok(youtubeService.getLoginUrl(visitorId, redirectUri));
    }

    @PostMapping("/youtube-music/auth/exchange")
    @Operation(summary = "토큰 교환", description = "Authorization Code를 Access Token으로 교환합니다.")
    public ResponseEntity<Map<String, Object>> exchangeToken(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(youtubeService.exchangeToken(
                body.get("code"), body.get("state"), body.get("redirectUri")));
    }

    @GetMapping("/youtube-music/auth/status")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam(required = false) String visitorId) {
        return ResponseEntity.ok(youtubeService.getAuthStatus(visitorId));
    }

    @PostMapping("/youtube-music/auth/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body) {
        youtubeService.logout(body.get("visitorId"));
        return ResponseEntity.ok().build();
    }

    // --- Data ---

    @GetMapping("/youtube-music/playlists")
    public ResponseEntity<Map<String, Object>> getPlaylists(
            @RequestParam(required = false) String visitorId,
            @RequestParam(defaultValue = "50") int maxResults,
            @RequestParam(required = false) String pageToken) {
        return ResponseEntity.ok(youtubeService.getPlaylists(visitorId, maxResults, pageToken));
    }

    @GetMapping("/youtube-music/playlists/{id}/items")
    public ResponseEntity<Map<String, Object>> getPlaylistItems(
            @PathVariable String id,
            @RequestParam(required = false) String visitorId,
            @RequestParam(defaultValue = "50") int maxResults,
            @RequestParam(required = false) String pageToken) {
        return ResponseEntity.ok(youtubeService.getPlaylistItems(id, visitorId, maxResults, pageToken));
    }

    @GetMapping("/youtube-music/liked")
    public ResponseEntity<Map<String, Object>> getLiked(
            @RequestParam(required = false) String visitorId,
            @RequestParam(defaultValue = "50") int maxResults,
            @RequestParam(required = false) String pageToken) {
        return ResponseEntity.ok(youtubeService.getLikedVideos(visitorId, maxResults, pageToken));
    }

    @PostMapping("/youtube-music/import")
    public ResponseEntity<Map<String, Object>> importPlaylist(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = null;
        if (userDetails != null)
            userId = userDetails.getUser().getUserId();
        if (body.get("userId") != null)
            userId = ((Number) body.get("userId")).longValue();

        return ResponseEntity.ok(youtubeService.importPlaylist(
                (String) body.get("visitorId"), (String) body.get("playlistId"), userId));
    }
}
