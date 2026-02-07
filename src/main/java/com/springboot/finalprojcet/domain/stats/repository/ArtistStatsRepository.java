package com.springboot.finalprojcet.domain.stats.repository;

import com.springboot.finalprojcet.entity.ArtistStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArtistStatsRepository extends JpaRepository<ArtistStats, String> {

    // Get random top artists from database
    @Query(value = "SELECT * FROM artist_stats WHERE play_count > 0 ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<ArtistStats> findRandomArtists(@Param("limit") int limit);

    // Get top artists by play count (sorted)
    @Query(value = "SELECT * FROM artist_stats WHERE play_count > 0 ORDER BY play_count DESC LIMIT :limit", nativeQuery = true)
    List<ArtistStats> findTopArtistsByPlayCount(@Param("limit") int limit);

    // Get top artists by view count (sorted)
    @Query(value = "SELECT * FROM artist_stats WHERE view_count > 0 ORDER BY view_count DESC LIMIT :limit", nativeQuery = true)
    List<ArtistStats> findTopArtistsByViewCount(@Param("limit") int limit);

    // Get top artists by like count (sorted)
    @Query(value = "SELECT * FROM artist_stats WHERE like_count > 0 ORDER BY like_count DESC LIMIT :limit", nativeQuery = true)
    List<ArtistStats> findTopArtistsByLikeCount(@Param("limit") int limit);
}
