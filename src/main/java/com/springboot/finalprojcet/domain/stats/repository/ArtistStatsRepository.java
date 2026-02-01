package com.springboot.finalprojcet.domain.stats.repository;

import com.springboot.finalprojcet.entity.ArtistStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArtistStatsRepository extends JpaRepository<ArtistStats, String> {
}
