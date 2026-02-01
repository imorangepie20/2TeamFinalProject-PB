package com.springboot.finalprojcet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "content_stats")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContentStats {

    @EmbeddedId
    private ContentStatsId id;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "play_count")
    private Long playCount;

    @Column(name = "like_count")
    private Long likeCount;
}
