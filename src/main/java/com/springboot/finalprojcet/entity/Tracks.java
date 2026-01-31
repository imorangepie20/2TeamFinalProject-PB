package com.springboot.finalprojcet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tracks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Tracks {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "track_id", nullable = false)
    private Long trackId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "artist", nullable = false)
    private String artist;

    @Column(name = "album")
    private String album;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "isrc", length = 50)
    private String isrc;

    @Column(name = "external_metadata", columnDefinition = "JSON")
    private String externalMetadata;

    @Column(name = "genre", length = 500)
    private String genre;

    @Column(name = "audio_features", columnDefinition = "JSON")
    private String audioFeatures;

    @Column(name = "popularity")
    private Short popularity;

    @Column(name = "explicit")
    private Boolean explicit;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "track_number")
    private Short trackNumber;

    @Column(name = "playcount")
    private Long playcount;

    @Column(name = "listeners")
    private Integer listeners;

    @Column(name = "mbid", length = 36)
    private String mbid;

    @Column(name = "spotify_id", length = 22)
    private String spotifyId;

    @Column(name = "tempo", precision = 6, scale = 3)
    private BigDecimal tempo;

    @Column(name = "music_key")
    private Byte musicKey;

    @Column(name = "mode")
    private Boolean mode;

    @Column(name = "time_signature")
    private Byte timeSignature;

    @Column(name = "danceability", precision = 4, scale = 3)
    private BigDecimal danceability;

    @Column(name = "energy", precision = 4, scale = 3)
    private BigDecimal energy;

    @Column(name = "valence", precision = 4, scale = 3)
    private BigDecimal valence;

    @Column(name = "acousticness", precision = 4, scale = 3)
    private BigDecimal acousticness;

    @Column(name = "instrumentalness", precision = 4, scale = 3)
    private BigDecimal instrumentalness;

    @Column(name = "liveness", precision = 4, scale = 3)
    private BigDecimal liveness;

    @Column(name = "speechiness", precision = 4, scale = 3)
    private BigDecimal speechiness;

    @Column(name = "loudness", precision = 5, scale = 2)
    private BigDecimal loudness;

    @Column(name = "artwork", length = 500)
    private String artwork;

    @CreatedDate
    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
