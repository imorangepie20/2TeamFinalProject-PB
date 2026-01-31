package com.springboot.finalprojcet.domain.gms.controller;

import com.springboot.finalprojcet.domain.gms.dto.EmsPlaylistResponseDto;
import com.springboot.finalprojcet.domain.gms.dto.RecommendTracksRequestDto;
import com.springboot.finalprojcet.domain.gms.dto.RecommendTracksResponseDto;
import com.springboot.finalprojcet.domain.gms.dto.SuccessFirstModelRequestDto;
import com.springboot.finalprojcet.domain.gms.service.GmsRecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gms")
@Tag(name = "GMS Recommend", description = "GMS 추천 관련 API")
@RequiredArgsConstructor
@Slf4j
public class GmsRecommendController {

    private final GmsRecommendService gmsRecommendService;

    /**
     * FastAPI에서 AI 모델 학습 완료 후 호출하는 콜백 API
     */
    @PostMapping("/success-first-model")
    @Operation(summary = "AI 모델 학습 완료 콜백", description = "FastAPI에서 AI 모델 학습 완료 후 호출")
    public ResponseEntity<Map<String, Object>> successFirstModel(
            @RequestBody SuccessFirstModelRequestDto request
    ) {
        log.info("successFirstModel API 호출 - userId: {}", request.getUserId());

        gmsRecommendService.successFirstModel(request.getUserId());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "모델 학습 완료 처리 성공");
        response.put("userId", request.getUserId());

        return ResponseEntity.ok(response);
    }

    /**
     * EMS 플레이리스트 추천용 생성 API
     */
    @PostMapping("/make-ems-playlists/{userId}")
    @Operation(summary = "EMS 플레이리스트 추천용 생성", description = "사용자를 위한 EMS 플레이리스트를 추천용으로 생성")
    public ResponseEntity<List<EmsPlaylistResponseDto>> makeEmsPlaylistsForRecommend(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "3") int recommendCount
    ) {
        log.info("makeEmsPlaylistsForRecommend API 호출 - userId: {}, count: {}", userId, recommendCount);

        List<EmsPlaylistResponseDto> result = gmsRecommendService.makeEmsPlaylistsForRecommend(userId, recommendCount);

        return ResponseEntity.ok(result);
    }

    /**
     * FastAPI에서 추천 트랙을 전달받는 API
     */
    @PostMapping("/send-recommend-tracks")
    @Operation(summary = "추천 트랙 수신", description = "FastAPI에서 추천 트랙을 전달받아 처리")
    public ResponseEntity<RecommendTracksResponseDto> sendRecommendTracks(
            @RequestBody RecommendTracksRequestDto request
    ) {
        log.info("sendRecommendTracks API 호출 - userId: {}", request.getUserId());

        RecommendTracksResponseDto result = gmsRecommendService.sendRecommendTracks(request);

        return ResponseEntity.ok(result);
    }

    /**
     * 사용자의 유효한 추천 플레이리스트 조회 API
     */
    @GetMapping("/recommend-playlists/{userId}")
    @Operation(summary = "추천 플레이리스트 조회", description = "사용자의 valid 상태인 추천 플레이리스트 조회")
    public ResponseEntity<List<EmsPlaylistResponseDto>> getValidRecommendPlaylists(
            @PathVariable Long userId
    ) {
        log.info("getValidRecommendPlaylists API 호출 - userId: {}", userId);

        List<EmsPlaylistResponseDto> result = gmsRecommendService.getValidRecommendPlaylists(userId);

        return ResponseEntity.ok(result);
    }
}
