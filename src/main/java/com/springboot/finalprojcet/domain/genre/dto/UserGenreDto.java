package com.springboot.finalprojcet.domain.genre.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
public class UserGenreDto extends GenreDto {
    private Integer preferenceLevel;

    // Constructor removed to rely on SuperBuilder
}
