package com.springboot.finalprojcet.domain.training.service;

import java.util.Map;

public interface TrainingService {
    Map<String, Object> getUserTrainingData(Long userId, boolean includeMetadata);

    Object exportTrainingData(Long userId, String format); // Returns Map or byte[]/String for CSV

    Map<String, Object> getFeatures(Long userId);

    Map<String, Object> saveScores(Long userId, java.util.List<Map<String, Object>> scores);

    Map<String, Object> getInteractions(Long userId, int limit);

    Map<String, Object> collectFeatures(java.util.List<Long> trackIds, int limit); // Spotify Features

    Map<String, Object> collectGenres(java.util.List<Long> trackIds, int limit); // MusicBrainz/LastFM

    Map<String, Object> getFeaturesStatus();

    Map<String, Object> submitRating(Long userId, Long trackId, int rating);

    Map<String, Object> getRatings(Long userId, int limit);
}
