package com.springboot.finalprojcet.domain.user.repository;

import com.springboot.finalprojcet.entity.MusicGenres;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MusicGenresRepository extends JpaRepository<MusicGenres, Long> {
    Optional<MusicGenres> findByGenreCode(String genreCode);

    java.util.List<MusicGenres> findByIsActiveTrueOrderByDisplayOrderAsc();
}
