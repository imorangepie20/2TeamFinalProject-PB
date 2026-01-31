package com.springboot.finalprojcet.entity;

import com.springboot.finalprojcet.enums.RecommendStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ems_playlist_for_recommend")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmsPlaylistForRecommend extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlists playlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RecommendStatus status = RecommendStatus.valid;
}
