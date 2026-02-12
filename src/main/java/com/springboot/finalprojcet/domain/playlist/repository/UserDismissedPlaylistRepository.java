package com.springboot.finalprojcet.domain.playlist.repository;

import com.springboot.finalprojcet.entity.UserDismissedPlaylist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDismissedPlaylistRepository extends JpaRepository<UserDismissedPlaylist, Long> {
    boolean existsByUserUserIdAndExternalId(Long userId, String externalId);
}
