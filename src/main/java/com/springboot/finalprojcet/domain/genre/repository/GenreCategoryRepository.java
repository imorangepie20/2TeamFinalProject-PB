package com.springboot.finalprojcet.domain.genre.repository;

import com.springboot.finalprojcet.entity.GenreCategories;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GenreCategoryRepository extends JpaRepository<GenreCategories, Integer> {
    List<GenreCategories> findByIsActiveTrueOrderByDisplayOrderAsc();
}
