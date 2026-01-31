package com.springboot.finalprojcet.domain.gms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendTracksRequestDto {
    private Long userId;
    private List<TrackDto> tracks;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackDto {
        private Long trackId;
        private String title;
        private String artist;
        private Double score;
    }
}
