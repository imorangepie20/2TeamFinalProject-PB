package com.springboot.finalprojcet.domain.playlist.dto;

import com.springboot.finalprojcet.enums.SourceType;
import com.springboot.finalprojcet.enums.SpaceType;
import com.springboot.finalprojcet.enums.StatusFlag;
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
public class PlaylistRequestDto {
    private String title;
    private String description;
    private SpaceType spaceType; // GMS, PMS, EMS
    private StatusFlag status; // PTP, PRP, PFP
    private SourceType sourceType;
    private String externalId;
    private String coverImage;
}
