package com.springboot.finalprojcet.domain.cart.dto;

import com.springboot.finalprojcet.entity.UserCart;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemDto {
    private Long id;
    private Long userId;
    private Long trackId;
    private String title;
    private String artist;
    private String album;
    private String artwork;
    private String previewUrl;
    private String externalId;
    private LocalDateTime createdAt;

    public static CartItemDto fromEntity(UserCart entity) {
        return CartItemDto.builder()
                .id(entity.getId())
                .userId(entity.getUser().getUserId())
                .trackId(entity.getTrackId())
                .title(entity.getTitle())
                .artist(entity.getArtist())
                .album(entity.getAlbum())
                .artwork(entity.getArtwork())
                .previewUrl(entity.getPreviewUrl())
                .externalId(entity.getExternalId())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
