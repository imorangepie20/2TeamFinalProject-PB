package com.springboot.finalprojcet.domain.gms.dto;

import com.springboot.finalprojcet.entity.EmsPlaylistForRecommend;
import com.springboot.finalprojcet.enums.RecommendStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmsPlaylistResponseDto {
    private Long id;
    private Long playlistId;
    private String playlistTitle;
    private Long userId;
    private RecommendStatus status;
    private LocalDateTime createdAt;

    public static EmsPlaylistResponseDto from(EmsPlaylistForRecommend entity) {
        return EmsPlaylistResponseDto.builder()
                .id(entity.getId())
                .playlistId(entity.getPlaylist().getPlaylistId())
                .playlistTitle(entity.getPlaylist().getTitle())
                .userId(entity.getUser().getUserId())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
