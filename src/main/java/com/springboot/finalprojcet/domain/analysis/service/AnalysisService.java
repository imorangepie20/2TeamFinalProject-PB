package com.springboot.finalprojcet.domain.analysis.service;

import com.springboot.finalprojcet.domain.analysis.dto.AnalysisProfileDto;
import com.springboot.finalprojcet.domain.analysis.dto.EvaluationResponseDto;
import com.springboot.finalprojcet.domain.tidal.dto.TidalFeaturedResponse;

import java.util.Map;

public interface AnalysisService {
    Map<String, Object> trainModel(Long userId);

    Map<String, Object> getProfileSummary(Long userId);

    EvaluationResponseDto evaluatePlaylist(Long userId, String playlistId);

    Map<String, Object> batchEvaluate(Long userId, java.util.List<String> playlistIds);

    Map<String, Object> getRecommendations(Long userId, int limit);
}
