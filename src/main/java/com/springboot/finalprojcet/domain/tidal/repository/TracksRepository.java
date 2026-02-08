package com.springboot.finalprojcet.domain.tidal.repository;

import com.springboot.finalprojcet.entity.Tracks;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TracksRepository extends JpaRepository<Tracks, Long> {
        
        // Search by artist or title
        List<Tracks> findByArtistContainingIgnoreCaseOrTitleContainingIgnoreCase(
                String artist, String title, Pageable pageable);
        @Query("SELECT t FROM Tracks t WHERE JSON_EXTRACT(t.externalMetadata, '$.tidalId') = :tidalId")
        Optional<Tracks> findByTidalId(@Param("tidalId") String tidalId);

        @Query("SELECT DISTINCT pt.track FROM PlaylistTracks pt " +
                        "WHERE pt.playlist.user.userId = :userId AND pt.playlist.spaceType = :spaceType")
        java.util.List<Tracks> findTracksByUserIdAndSpaceType(@Param("userId") Long userId,
                        @Param("spaceType") com.springboot.finalprojcet.enums.SpaceType spaceType);

        @Query("SELECT DISTINCT pt.track FROM PlaylistTracks pt " +
                        "WHERE pt.playlist.user.userId = :userId AND pt.playlist.sourceType = :sourceType")
        java.util.List<Tracks> findTracksByUserIdAndSourceType(@Param("userId") Long userId,
                        @Param("sourceType") com.springboot.finalprojcet.enums.SourceType sourceType);

        // Find EMS tracks without duration
        @Query("SELECT DISTINCT pt.track FROM PlaylistTracks pt " +
                        "WHERE pt.playlist.spaceType = 'EMS' AND (pt.track.duration IS NULL OR pt.track.duration = 0)")
        List<Tracks> findEmsTracksWithoutDuration();

        // Find tracks by ISRC
        Optional<Tracks> findByIsrc(String isrc);
}
