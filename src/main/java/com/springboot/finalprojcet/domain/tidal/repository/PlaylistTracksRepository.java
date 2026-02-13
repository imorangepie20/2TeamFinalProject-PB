package com.springboot.finalprojcet.domain.tidal.repository;

import com.springboot.finalprojcet.entity.PlaylistTracks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistTracksRepository extends JpaRepository<PlaylistTracks, Long> {

    java.util.List<PlaylistTracks> findAllByPlaylistPlaylistIdOrderByOrderIndex(Long playlistId);

    @Query("SELECT pt FROM PlaylistTracks pt JOIN FETCH pt.track WHERE pt.playlist.playlistId = :playlistId ORDER BY pt.orderIndex")
    java.util.List<PlaylistTracks> findAllWithTrackByPlaylistId(@Param("playlistId") Long playlistId);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(pt.orderIndex) FROM PlaylistTracks pt WHERE pt.playlist.playlistId = :playlistId")
    Integer findMaxOrderIndexByPlaylistId(
            @org.springframework.data.repository.query.Param("playlistId") Long playlistId);

    void deleteByPlaylistPlaylistIdAndTrackTrackId(Long playlistId, Long trackId);

    Integer countByPlaylistPlaylistId(Long playlistId);
}
