package com.springboot.finalprojcet.domain.analysis.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisProfileDto {
    private Long userId;
    private String trainedAt;
    private DataStats dataStats;
    private Preferences preferences;
    private Weights weights;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DataStats {
        private int totalTracks;
        private int uniqueArtists;
        private int uniqueAlbums;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Preferences {
        private List<ItemWeight> topArtists;
        private List<ItemCount> topAlbums;
        private List<ItemCount> inferredGenres;
        private StatProfile popularityProfile;
        private StatProfile durationProfile;
        private double explicitTolerance;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Weights {
        private double artistMatch;
        private double genreMatch;
        private double popularityMatch;
        private double durationMatch;
        private double albumMatch;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemWeight {
        private String name;
        private int count;
        private double weight;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemCount {
        private String name;
        private int count;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatProfile {
        private int avg;
    }
}
