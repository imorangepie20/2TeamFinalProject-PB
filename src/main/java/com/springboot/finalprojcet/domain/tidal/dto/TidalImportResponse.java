package com.springboot.finalprojcet.domain.tidal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TidalImportResponse {
    private boolean success;
    private String message;
    private Long playlistId;
    private String title;
    private Integer importedTracks;
    private Integer totalTracks;
    private String error;
}
