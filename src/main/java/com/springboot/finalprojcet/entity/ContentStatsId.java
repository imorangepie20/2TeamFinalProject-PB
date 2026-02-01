package com.springboot.finalprojcet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ContentStatsId implements Serializable {
    @Column(name = "content_type", length = 20)
    private String contentType;

    @Column(name = "content_id")
    private Long contentId;
}
