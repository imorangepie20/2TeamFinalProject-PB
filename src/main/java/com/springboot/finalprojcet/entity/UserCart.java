package com.springboot.finalprojcet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_cart", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "title", "artist"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class UserCart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "artist", nullable = false, length = 500)
    private String artist;

    @Column(name = "album", length = 500)
    private String album;

    @Column(name = "artwork", length = 1000)
    private String artwork;

    @Column(name = "preview_url", length = 1000)
    private String previewUrl;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
