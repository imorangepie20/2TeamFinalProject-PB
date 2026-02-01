package com.springboot.finalprojcet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfiles {
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "profile_data", columnDefinition = "JSON")
    private String profileData;

    @Column(name = "model_version", length = 20)
    private String modelVersion;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
