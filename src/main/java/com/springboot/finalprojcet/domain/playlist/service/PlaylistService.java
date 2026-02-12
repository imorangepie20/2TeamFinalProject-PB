package com.springboot.finalprojcet.domain.playlist.service;

import com.springboot.finalprojcet.domain.playlist.dto.PlaylistRequestDto;
import com.springboot.finalprojcet.domain.playlist.dto.PlaylistResponseDto;
import com.springboot.finalprojcet.domain.playlist.dto.TrackRequestDto;
import com.springboot.finalprojcet.enums.SpaceType;
import com.springboot.finalprojcet.enums.StatusFlag;

import java.util.List;
import java.util.Map;

public interface PlaylistService {
    Map<String, Object> getAllPlaylists(SpaceType spaceType, StatusFlag status, Long userId);

    PlaylistResponseDto getPlaylistById(Long id);

    PlaylistResponseDto createPlaylist(Long userId, PlaylistRequestDto request);

    PlaylistResponseDto updatePlaylist(Long id, PlaylistRequestDto request);

    PlaylistResponseDto updatePlaylistStatus(Long id, StatusFlag status);

    Map<String, Object> movePlaylist(Long id, SpaceType spaceType);

    Map<String, Object> importAlbum(Long userId, Map<String, Object> data);

    void deletePlaylist(Long id, Long userId);

    Map<String, Object> addTrackToPlaylist(Long id, TrackRequestDto track);

    void removeTrackFromPlaylist(Long id, Long trackId);
    
    Map<String, Object> searchTracks(String query, int limit);
}
