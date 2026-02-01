package com.springboot.finalprojcet.domain.playlist.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrackResponseDto {
    private Long id;
    private String title;
    private String artist;
    private String album;
    private Integer duration;
    private String isrc;
    private String artwork;
    private int orderIndex;
    private String externalMetadata;
}
