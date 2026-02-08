package com.springboot.finalprojcet.domain.cart.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartAnalysisRequestDto {
    private Long userId;
    private String model;
}
