package com.springboot.finalprojcet.domain.tidal.repository;

import com.springboot.finalprojcet.entity.Tracks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TracksRepository extends JpaRepository<Tracks, Long> {
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
}
