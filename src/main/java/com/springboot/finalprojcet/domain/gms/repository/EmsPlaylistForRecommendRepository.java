package com.springboot.finalprojcet.domain.gms.repository;

import com.springboot.finalprojcet.entity.EmsPlaylistForRecommend;
import com.springboot.finalprojcet.enums.RecommendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmsPlaylistForRecommendRepository extends JpaRepository<EmsPlaylistForRecommend, Long> {

    List<EmsPlaylistForRecommend> findByUserUserId(Long userId);

    List<EmsPlaylistForRecommend> findByUserUserIdAndStatus(Long userId, RecommendStatus status);

    @Query("SELECT e FROM EmsPlaylistForRecommend e WHERE e.user.userId = :userId AND e.status = 'valid'")
    List<EmsPlaylistForRecommend> findValidByUserId(@Param("userId") Long userId);

    boolean existsByPlaylistPlaylistIdAndUserUserId(Long playlistId, Long userId);
}
