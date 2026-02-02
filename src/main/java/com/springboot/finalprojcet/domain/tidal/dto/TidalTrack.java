package com.springboot.finalprojcet.domain.tidal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TidalTrack {
    private String id;
    private String title;
    private String artist;
    private String album;
    private int duration;
    private String artwork; // 320x320 URL
    private String isrc;
    private boolean allowStreaming;
}
