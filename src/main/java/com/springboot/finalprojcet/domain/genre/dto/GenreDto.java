package com.springboot.finalprojcet.domain.genre.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@lombok.experimental.SuperBuilder
public class GenreDto {
    private Integer id;
    private Integer categoryId;
    private String code;
    private String nameKo;
    private String nameEn;
    private String icon;
    private String color;
    private Integer displayOrder;
    private String categoryNameKo; // For flat list view
}
