package com.springboot.finalprojcet.domain.analysis.controller;

import com.springboot.finalprojcet.domain.analysis.dto.AnalysisProfileDto;
import com.springboot.finalprojcet.domain.analysis.dto.EvaluationResponseDto;
import com.springboot.finalprojcet.domain.analysis.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
@Tag(name = "Analysis", description = "AI 분석 API")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping("/train")
    @Operation(summary = "모델 학습", description = "사용자의 PMS 데이터를 기반으로 AI 모델을 학습합니다.")
    public ResponseEntity<Map<String, Object>> trainModel(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null ? Long.parseLong(body.get("userId").toString()) : 3L; // Default user 3
                                                                                                       // for parity
        return ResponseEntity.ok(analysisService.trainModel(userId));
    }

    @GetMapping("/profile/{userId}")
    @Operation(summary = "프로필 조회", description = "사용자의 학습된 AI 프로필을 조회합니다.")
    public ResponseEntity<Map<String, Object>> getProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(analysisService.getProfileSummary(userId));
    }

    @PostMapping("/evaluate/{id}")
    @Operation(summary = "플레이리스트 평가", description = "특정 플레이리스트를 AI 모델로 평가합니다.")
    public ResponseEntity<EvaluationResponseDto> evaluatePlaylist(@PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null ? Long.parseLong(body.get("userId").toString()) : 3L;
        return ResponseEntity.ok(analysisService.evaluatePlaylist(userId, id));
    }

    @PostMapping("/batch-evaluate")
    @Operation(summary = "일괄 평가", description = "여러 플레이리스트를 일괄 평가합니다.")
    public ResponseEntity<Map<String, Object>> batchEvaluate(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null ? Long.parseLong(body.get("userId").toString()) : 3L;
        List<String> playlistIds = (List<String>) body.get("playlistIds");
        if (playlistIds == null) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "playlistIds array required"));
        }
        return ResponseEntity.ok(analysisService.batchEvaluate(userId, playlistIds));
    }

    @GetMapping("/recommendations/{userId}")
    @Operation(summary = "추천", description = "AI 추천 플레이리스트를 조회합니다.")
    public ResponseEntity<Map<String, Object>> getRecommendations(@PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analysisService.getRecommendations(userId, limit));
    }
}
