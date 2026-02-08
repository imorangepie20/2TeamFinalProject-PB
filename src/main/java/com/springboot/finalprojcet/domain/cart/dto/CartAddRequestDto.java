package com.springboot.finalprojcet.domain.cart.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartAddRequestDto {
    private Long trackId;
    private String title;
    private String artist;
    private String album;
    private String artwork;
    private String previewUrl;
    private String externalId;
}
