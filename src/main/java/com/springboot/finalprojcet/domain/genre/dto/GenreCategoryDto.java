package com.springboot.finalprojcet.domain.genre.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenreCategoryDto {
    private Integer id;
    private String code;
    private String nameKo;
    private String nameEn;
    private String icon;
    private Integer displayOrder;
    private List<GenreDto> genres;
}
