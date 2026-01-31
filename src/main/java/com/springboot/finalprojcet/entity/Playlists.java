package com.springboot.finalprojcet.entity;

import com.springboot.finalprojcet.enums.SourceType;
import com.springboot.finalprojcet.enums.SpaceType;
import com.springboot.finalprojcet.enums.StatusFlag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "playlists")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Playlists extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "playlist_id", nullable = false)
    private Long playlistId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Users user;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "space_type")
    private SpaceType spaceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_flag")
    private StatusFlag statusFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    private SourceType sourceType;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "cover_image", length = 500)
    private String coverImage;

    @Column(name = "ai_score", precision = 5, scale = 2)
    private java.math.BigDecimal aiScore;
}
