package com.springboot.finalprojcet.domain.gms.repository;

import com.springboot.finalprojcet.entity.Playlists;
import com.springboot.finalprojcet.enums.SpaceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaylistRepository extends JpaRepository<Playlists, Long> {

    List<Playlists> findByUserUserId(Long userId);

    List<Playlists> findByUserUserIdAndSpaceType(Long userId, SpaceType spaceType);

    @Query("SELECT p FROM Playlists p WHERE p.user.userId = :userId AND p.spaceType = 'EMS'")
    List<Playlists> findEmsByUserId(@Param("userId") Long userId);

    @Query("SELECT p FROM Playlists p WHERE p.user.userId = :userId AND p.spaceType = 'PMS'")
    List<Playlists> findPmsByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT * FROM playlists WHERE user_id = :userId AND space_type = 'EMS' ORDER BY RAND() LIMIT :count", nativeQuery = true)
    List<Playlists> findRandomEmsByUserId(@Param("userId") Long userId, @Param("count") int count);

    List<Playlists> findBySpaceType(SpaceType spaceType);

    // 중복 체크용: externalId와 userId로 검색
    boolean existsByExternalIdAndUserUserId(String externalId, Long userId);
}
