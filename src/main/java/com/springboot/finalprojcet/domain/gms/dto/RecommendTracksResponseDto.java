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
public class RecommendTracksResponseDto {
    private Long userId;
    private int trackCount;
    private List<Long> trackIds;
    private String message;
}
