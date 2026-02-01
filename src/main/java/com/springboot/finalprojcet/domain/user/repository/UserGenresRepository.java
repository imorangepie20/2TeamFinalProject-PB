package com.springboot.finalprojcet.domain.user.repository;

import com.springboot.finalprojcet.entity.UserGenres;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGenresRepository extends JpaRepository<UserGenres, Long> {
    java.util.List<UserGenres> findByUser_UserIdOrderByPreferenceLevelDescGenre_DisplayOrderAsc(Long userId);
}
