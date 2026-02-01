package com.springboot.finalprojcet.domain.genre.service.impl;

import com.springboot.finalprojcet.domain.genre.dto.GenreCategoryDto;
import com.springboot.finalprojcet.domain.genre.dto.GenreDto;
import com.springboot.finalprojcet.domain.genre.dto.UserGenreDto;
import com.springboot.finalprojcet.domain.genre.repository.GenreCategoryRepository;
import com.springboot.finalprojcet.domain.genre.service.GenreService;
import com.springboot.finalprojcet.domain.user.repository.MusicGenresRepository;
import com.springboot.finalprojcet.domain.user.repository.UserGenresRepository;
import com.springboot.finalprojcet.entity.GenreCategories;
import com.springboot.finalprojcet.entity.MusicGenres;
import com.springboot.finalprojcet.entity.UserGenres;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GenreServiceImpl implements GenreService {

    private final GenreCategoryRepository genreCategoryRepository;
    private final MusicGenresRepository musicGenresRepository;
    private final UserGenresRepository userGenresRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getGenresGrouped() {
        List<GenreCategories> categories = genreCategoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        List<MusicGenres> genres = musicGenresRepository.findByIsActiveTrueOrderByDisplayOrderAsc();

        List<GenreCategoryDto> categoryDtos = categories.stream().map(cat -> {
            List<GenreDto> catGenres = genres.stream()
                    .filter(g -> g.getCategoryId() != null && g.getCategoryId().equals(cat.getCategoryId()))
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            return GenreCategoryDto.builder()
                    .id(cat.getCategoryId())
                    .code(cat.getCategoryCode())
                    .nameKo(cat.getCategoryNameKo())
                    .nameEn(cat.getCategoryNameEn())
                    .icon(cat.getCategoryIcon())
                    .displayOrder(cat.getDisplayOrder())
                    .genres(catGenres)
                    .build();
        }).collect(Collectors.toList());

        List<GenreDto> allGenres = genres.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("categories", categoryDtos);
        result.put("genres", allGenres);

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GenreDto> getFlatGenres() {
        List<MusicGenres> genres = musicGenresRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        List<GenreCategories> categories = genreCategoryRepository.findAll();
        Map<Integer, String> categoryNames = categories.stream()
                .collect(Collectors.toMap(GenreCategories::getCategoryId, GenreCategories::getCategoryNameKo));

        return genres.stream().map(g -> {
            GenreDto dto = convertToDto(g);
            if (g.getCategoryId() != null) {
                dto.setCategoryNameKo(categoryNames.get(g.getCategoryId()));
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserGenreDto> getUserGenres(Long userId) {
        List<UserGenres> userGenres = userGenresRepository
                .findByUser_UserIdOrderByPreferenceLevelDescGenre_DisplayOrderAsc(userId);

        return userGenres.stream().map(ug -> {
            MusicGenres g = ug.getGenre();
            return UserGenreDto.builder()
                    .id(g.getGenreId())
                    .categoryId(g.getCategoryId())
                    .code(g.getGenreCode())
                    .nameKo(g.getGenreNameKo())
                    .nameEn(g.getGenreNameEn())
                    .icon(g.getGenreIcon())
                    .color(g.getGenreColor())
                    .displayOrder(g.getDisplayOrder())
                    .preferenceLevel(ug.getPreferenceLevel())
                    .build();
        }).collect(Collectors.toList());
    }

    private GenreDto convertToDto(MusicGenres g) {
        return GenreDto.builder()
                .id(g.getGenreId())
                .categoryId(g.getCategoryId())
                .code(g.getGenreCode())
                .nameKo(g.getGenreNameKo())
                .nameEn(g.getGenreNameEn())
                .icon(g.getGenreIcon())
                .color(g.getGenreColor())
                .displayOrder(g.getDisplayOrder())
                .build();
    }
}
