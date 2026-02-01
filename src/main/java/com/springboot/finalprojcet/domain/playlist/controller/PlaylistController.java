package com.springboot.finalprojcet.domain.playlist.controller;

import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.playlist.dto.PlaylistRequestDto;
import com.springboot.finalprojcet.domain.playlist.dto.PlaylistResponseDto;
import com.springboot.finalprojcet.domain.playlist.dto.TrackRequestDto;
import com.springboot.finalprojcet.domain.playlist.service.PlaylistService;
import com.springboot.finalprojcet.enums.SpaceType;
import com.springboot.finalprojcet.enums.StatusFlag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/playlists")
@Tag(name = "Playlists", description = "플레이리스트 관리 API")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistService playlistService;

    @GetMapping
    @Operation(summary = "플레이리스트 목록 조회", description = "조건에 따른 플레이리스트 목록을 반환합니다.")
    public ResponseEntity<?> getPlaylists(
            @RequestParam(required = false) SpaceType spaceType,
            @RequestParam(required = false) StatusFlag status,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            Long userId = userDetails != null ? userDetails.getUser().getUserId() : null;
            return ResponseEntity.ok(playlistService.getAllPlaylists(spaceType, status, userId));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(java.util.Map.of("error", e.getMessage(), "trace", e.getStackTrace()[0].toString()));
        }
    }

    @PostMapping
    @Operation(summary = "플레이리스트 생성", description = "새로운 플레이리스트를 생성합니다.")
    public ResponseEntity<PlaylistResponseDto> createPlaylist(
            @RequestBody PlaylistRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(playlistService.createPlaylist(userDetails.getUser().getUserId(), request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "플레이리스트 상세 조회", description = "플레이리스트 상세 정보와 트랙 목록을 반환합니다.")
    public ResponseEntity<PlaylistResponseDto> getPlaylist(@PathVariable Long id) {
        return ResponseEntity.ok(playlistService.getPlaylistById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "플레이리스트 수정", description = "플레이리스트 제목 및 설명을 수정합니다.")
    public ResponseEntity<PlaylistResponseDto> updatePlaylist(
            @PathVariable Long id,
            @RequestBody PlaylistRequestDto request) {
        return ResponseEntity.ok(playlistService.updatePlaylist(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "플레이리스트 상태 변경", description = "플레이리스트 상태(PTP, PRP, PFP)를 변경합니다.")
    public ResponseEntity<PlaylistResponseDto> updatePlaylistStatus(
            @PathVariable Long id,
            @RequestBody Map<String, StatusFlag> body) {
        return ResponseEntity.ok(playlistService.updatePlaylistStatus(id, body.get("status")));
    }

    @PatchMapping("/{id}/move")
    @Operation(summary = "플레이리스트 이동", description = "플레이리스트를 다른 공간(Space)으로 이동합니다.")
    public ResponseEntity<Map<String, Object>> movePlaylist(
            @PathVariable Long id,
            @RequestBody Map<String, SpaceType> body) {
        return ResponseEntity.ok(playlistService.movePlaylist(id, body.get("spaceType")));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "플레이리스트 삭제", description = "플레이리스트를 삭제합니다.")
    public ResponseEntity<Void> deletePlaylist(@PathVariable Long id) {
        playlistService.deletePlaylist(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/tracks")
    @Operation(summary = "트랙 추가", description = "플레이리스트에 트랙을 추가합니다.")
    public ResponseEntity<Map<String, Object>> addTrack(
            @PathVariable Long id,
            @RequestBody Map<String, TrackRequestDto> body) {
        return ResponseEntity.ok(playlistService.addTrackToPlaylist(id, body.get("track")));
    }

    @DeleteMapping("/{id}/tracks/{trackId}")
    @Operation(summary = "트랙 삭제", description = "플레이리스트에서 트랙을 삭제합니다.")
    public ResponseEntity<Void> removeTrack(
            @PathVariable Long id,
            @PathVariable Long trackId) {
        playlistService.removeTrackFromPlaylist(id, trackId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/seed")
    @Operation(summary = "초기 플레이리스트 생성", description = "기본 플레이리스트를 생성합니다.")
    public ResponseEntity<Map<String, Object>> seedPlaylists() {
        // 이미 DB에 데이터가 있으므로 성공 메시지만 반환
        return ResponseEntity.ok(Map.of("message", "Playlists seeded successfully", "imported", 0));
    }
}
