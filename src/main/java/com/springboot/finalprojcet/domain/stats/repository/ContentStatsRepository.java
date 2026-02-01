package com.springboot.finalprojcet.domain.stats.repository;

import com.springboot.finalprojcet.entity.ContentStats;
import com.springboot.finalprojcet.entity.ContentStatsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentStatsRepository extends JpaRepository<ContentStats, ContentStatsId> {

    // For 'best' queries, we might need custom queries or use the naming convention
    // carefully.
    // However, since the stats are joined with other tables (playlists, tracks) in
    // the Node.js implementation,
    // we will likely perform those joins in the Service using EntityManager or
    // complex JPQL.
    // For simple CRUD/updates, JpaRepository is sufficient.
}
