package com.springboot.finalprojcet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "music_genres")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MusicGenres {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "genre_id", nullable = false)
    private Integer genreId;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "genre_code", length = 50, nullable = false, unique = true)
    private String genreCode;

    @Column(name = "genre_name_ko", length = 100, nullable = false)
    private String genreNameKo;

    @Column(name = "genre_name_en", length = 100, nullable = false)
    private String genreNameEn;

    @Column(name = "genre_icon", length = 50)
    private String genreIcon;

    @Column(name = "genre_color", length = 100)
    private String genreColor;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
