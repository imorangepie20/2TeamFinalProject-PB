package com.springboot.finalprojcet.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AdminStatsDto {
    private long totalUsers;
    private long totalTracks;
    private long totalPlaylists;
}
