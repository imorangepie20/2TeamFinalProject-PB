package com.springboot.finalprojcet.domain.training.controller;

import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.training.service.TrainingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/training")
@Tag(name = "Training", description = "ML 학습 데이터 관리 및 내보내기")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingService trainingService;

    @GetMapping("/user/{userId}/data")
    public ResponseEntity<Map<String, Object>> getUserData(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "true") boolean includeMetadata) {
        return ResponseEntity.ok(trainingService.getUserTrainingData(userId, includeMetadata));
    }

    @GetMapping("/export")
    @Operation(summary = "데이터 내보내기", description = "학습 데이터를 CSV 또는 JSON으로 내보냅니다.")
    public ResponseEntity<Object> exportData(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "json") String format) {

        Object result = trainingService.exportTrainingData(userId, format);

        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=training_data.csv")
                    .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                    .body(result);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/features")
    public ResponseEntity<Map<String, Object>> getFeatures(@RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(trainingService.getFeatures(userId));
    }

    @PostMapping("/score")
    public ResponseEntity<Map<String, Object>> saveScores(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = null;
        if (body.get("userId") != null)
            userId = ((Number) body.get("userId")).longValue();
        if (userId == null && userDetails != null)
            userId = userDetails.getUser().getUserId();

        List<Map<String, Object>> scores = (List<Map<String, Object>>) body.get("scores");
        return ResponseEntity.ok(trainingService.saveScores(userId, scores));
    }

    @GetMapping("/interactions")
    public ResponseEntity<Map<String, Object>> getInteractions(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1000") int limit) {
        return ResponseEntity.ok(trainingService.getInteractions(userId, limit));
    }

    @PostMapping("/collect-features")
    public ResponseEntity<Map<String, Object>> collectFeatures(@RequestBody Map<String, Object> body) {
        List<Long> trackIds = null;
        if (body.get("trackIds") != null) {
            // Need custom parsing or rely on simple list
            // Assuming simple List handling via Jackson
            trackIds = (List<Long>) body.get("trackIds");
        }
        int limit = body.get("limit") != null ? (int) body.get("limit") : 50;
        return ResponseEntity.ok(trainingService.collectFeatures(trackIds, limit));
    }

    @GetMapping("/features-status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(trainingService.getFeaturesStatus());
    }

    @PostMapping("/rate")
    public ResponseEntity<Map<String, Object>> rateTrack(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getUser().getUserId();
        Long trackId = ((Number) body.get("trackId")).longValue();
        int rating = ((Number) body.get("rating")).intValue();
        return ResponseEntity.ok(trainingService.submitRating(userId, trackId, rating));
    }

    @GetMapping("/ratings")
    public ResponseEntity<Map<String, Object>> getRatings(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1000") int limit) {
        return ResponseEntity.ok(trainingService.getRatings(userId, limit));
    }
}
