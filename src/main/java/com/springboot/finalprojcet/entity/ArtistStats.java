package com.springboot.finalprojcet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "artist_stats")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArtistStats {

    @Id
    @Column(name = "artist_name", length = 255)
    private String artistName;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "play_count")
    private Long playCount;

    @Column(name = "like_count")
    private Long likeCount;
}
