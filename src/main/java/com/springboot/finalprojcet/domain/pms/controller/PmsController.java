package com.springboot.finalprojcet.domain.pms.controller;

import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.pms.service.PmsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/pms")
@Tag(name = "PMS", description = "PMS 전용 기능 (통계, 내보내기)")
@RequiredArgsConstructor
public class PmsController {

    private final PmsService pmsService;

    @GetMapping("/stats")
    @Operation(summary = "PMS 통계", description = "PMS 공간의 종합 통계를 조회합니다.")
    public ResponseEntity<Map<String, Object>> getStats(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(pmsService.getPmsStats(userDetails.getUser().getUserId()));
    }

    @GetMapping("/playlists/links")
    @Operation(summary = "플레이리스트 링크 목록", description = "내보내기 가능한 플레이리스트 링크들을 조회합니다.")
    public ResponseEntity<Map<String, Object>> getPlaylistLinks(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(pmsService.getPlaylistLinks(userDetails.getUser().getUserId()));
    }

    @GetMapping("/export")
    @Operation(summary = "PMS 전체 데이터 내보내기", description = "PMS의 모든 데이터를 JSON 또는 CSV로 내보냅니다.")
    public ResponseEntity<?> exportData(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "json") String format) {

        Object result = pmsService.exportPmsData(userDetails.getUser().getUserId(), format);

        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pms_data.csv\"")
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

        Object result = pmsService.exportPlaylist(playlistId, format);

        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"playlist_" + playlistId + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(result);
        }
        return ResponseEntity.ok(result);
    }
}
