package com.springboot.finalprojcet.domain.playlist.dto;

import com.springboot.finalprojcet.enums.SourceType;
import com.springboot.finalprojcet.enums.SpaceType;
import com.springboot.finalprojcet.enums.StatusFlag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlaylistResponseDto {
    private Long id;
    private String title;
    private String description;
    private SpaceType spaceType;
    private StatusFlag status;
    private SourceType sourceType;
    private String externalId;
    private String coverImage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer trackCount;
    private Double aiScore;
    private java.util.List<TrackResponseDto> tracks;
}
