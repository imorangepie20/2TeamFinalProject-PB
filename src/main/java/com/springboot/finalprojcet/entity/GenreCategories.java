package com.springboot.finalprojcet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "genre_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenreCategories {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "category_code", length = 50, nullable = false, unique = true)
    private String categoryCode;

    @Column(name = "category_name_ko", length = 100, nullable = false)
    private String categoryNameKo;

    @Column(name = "category_name_en", length = 100, nullable = false)
    private String categoryNameEn;

    @Column(name = "category_icon", length = 50)
    private String categoryIcon;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
