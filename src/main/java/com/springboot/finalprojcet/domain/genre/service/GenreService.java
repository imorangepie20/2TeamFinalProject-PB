package com.springboot.finalprojcet.domain.genre.service;

import com.springboot.finalprojcet.domain.genre.dto.GenreCategoryDto;
import com.springboot.finalprojcet.domain.genre.dto.GenreDto;
import com.springboot.finalprojcet.domain.genre.dto.UserGenreDto;

import java.util.List;
import java.util.Map;

public interface GenreService {
    Map<String, Object> getGenresGrouped();

    List<GenreDto> getFlatGenres();

    List<UserGenreDto> getUserGenres(Long userId);
}
