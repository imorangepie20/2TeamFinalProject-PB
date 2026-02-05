package com.springboot.finalprojcet.domain.tidal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TidalStreamUrlResponse {
    private boolean success;
    private String streamUrl;
    private String quality; // LOW, NORMAL, HIGH, LOSSLESS, HI_RES
    private String codec; // AAC, FLAC, MQA
    private Integer bitRate;
    private Integer sampleRate;
    private Integer bitDepth;
    private String error;
    private String fallbackSource; // YOUTUBE, ITUNES_PREVIEW, etc.
    private String fallbackUrl;
}
