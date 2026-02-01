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
public class TrackRequestDto {
    private String id; // External ID often passed as 'id' in frontend
    private String title;
    private String artist;
    private String album;
    private Integer duration;
    private String artwork;
    private String url;
    private String audio;
}
