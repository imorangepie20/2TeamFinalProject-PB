package com.springboot.finalprojcet.domain.tidal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TidalPlaylist {
    private String uuid;
    private String title;
    private Integer numberOfTracks;
    private Integer trackCount;
    private String image;
    private String squareImage; // For internal use
    private String description;
}
