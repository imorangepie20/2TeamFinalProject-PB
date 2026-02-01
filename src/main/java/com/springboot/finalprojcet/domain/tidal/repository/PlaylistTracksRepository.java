package com.springboot.finalprojcet.domain.tidal.repository;

import com.springboot.finalprojcet.entity.PlaylistTracks;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistTracksRepository extends JpaRepository<PlaylistTracks, Long> {

    java.util.List<PlaylistTracks> findAllByPlaylistPlaylistIdOrderByOrderIndex(Long playlistId);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(pt.orderIndex) FROM PlaylistTracks pt WHERE pt.playlist.playlistId = :playlistId")
    Integer findMaxOrderIndexByPlaylistId(
            @org.springframework.data.repository.query.Param("playlistId") Long playlistId);

    void deleteByPlaylistPlaylistIdAndTrackTrackId(Long playlistId, Long trackId);

    Integer countByPlaylistPlaylistId(Long playlistId);
}
