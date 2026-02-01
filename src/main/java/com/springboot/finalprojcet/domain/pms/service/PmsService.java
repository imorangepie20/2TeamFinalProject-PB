package com.springboot.finalprojcet.domain.pms.service;

import java.util.Map;

public interface PmsService {
    Map<String, Object> getPmsStats(Long userId);

    Map<String, Object> getPlaylistLinks(Long userId);

    Object exportPmsData(Long userId, String format);

    Object exportPlaylist(Long playlistId, String format);
}
