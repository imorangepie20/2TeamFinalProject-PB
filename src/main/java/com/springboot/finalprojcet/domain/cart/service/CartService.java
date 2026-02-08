package com.springboot.finalprojcet.domain.cart.service;

import com.springboot.finalprojcet.domain.cart.dto.CartAddRequestDto;
import com.springboot.finalprojcet.domain.cart.dto.CartAnalysisRequestDto;
import com.springboot.finalprojcet.domain.cart.dto.CartItemDto;
import com.springboot.finalprojcet.domain.cart.dto.CartResponseDto;

import java.util.Map;

public interface CartService {

    CartResponseDto getCart(Long userId);

    CartItemDto addToCart(Long userId, CartAddRequestDto request);

    void removeFromCart(Long userId, Long cartItemId);

    void clearCart(Long userId);

    boolean isInCart(Long userId, String title, String artist);

    /**
     * 장바구니 분석 요청 - FastAPI에 추천 요청 후 GMS로 전달
     */
    Map<String, Object> analyzeCart(Long userId, CartAnalysisRequestDto request);

    /**
     * 1단계: 장바구니 → 플레이리스트 저장 + 장바구니 비우기 (트랜잭션)
     */
    Map<String, Object> saveCartAsPlaylist(Long userId, CartAnalysisRequestDto request);

    /**
     * 2단계: FastAPI에 분석 요청 (트랜잭션 불필요, commit 후 호출)
     */
    Map<String, Object> requestFastapiEvaluation(Long userId, String model);

    /**
     * 사용자 모델 학습 - 사용자의 플레이리스트를 기반으로 AI 모델 학습 요청
     */
    Map<String, Object> trainModel(Long userId, String model);
}
