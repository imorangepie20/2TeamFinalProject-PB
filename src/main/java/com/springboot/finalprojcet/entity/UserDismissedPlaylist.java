package com.springboot.finalprojcet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_dismissed_playlists")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDismissedPlaylist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;
}
