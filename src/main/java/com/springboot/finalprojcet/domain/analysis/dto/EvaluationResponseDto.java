package com.springboot.finalprojcet.domain.analysis.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationResponseDto {
    private int score;
    private String grade;
    private String reason;
    private MatchDetails matchDetails;
    private boolean needsTraining;
    private boolean promoted;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchDetails {
        private int artistMatches;
        private int albumMatches;
        private int genreMatches;
        private java.util.List<String> matchedArtists;
    }
}
