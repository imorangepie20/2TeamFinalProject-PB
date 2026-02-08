package com.springboot.finalprojcet.domain.cart.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponseDto {
    private boolean success;
    private List<CartItemDto> cart;
    private int count;
    private String message;
}
