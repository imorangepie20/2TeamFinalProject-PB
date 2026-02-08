package com.springboot.finalprojcet.domain.cart.controller;

import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.cart.dto.CartAddRequestDto;
import com.springboot.finalprojcet.domain.cart.dto.CartItemDto;
import com.springboot.finalprojcet.domain.cart.dto.CartResponseDto;
import com.springboot.finalprojcet.domain.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart", description = "장바구니 관리 API")
@RequiredArgsConstructor
@Slf4j
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "장바구니 조회", description = "사용자의 장바구니 목록을 조회합니다.")
    public ResponseEntity<CartResponseDto> getCart(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails != null ? userDetails.getUser().getUserId() : 1L;
        log.info("[CartController] getCart - userId={}", userId);
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @PostMapping
    @Operation(summary = "장바구니 추가", description = "트랙을 장바구니에 추가합니다.")
    public ResponseEntity<?> addToCart(
            @RequestBody CartAddRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            Long userId = userDetails != null ? userDetails.getUser().getUserId() : 1L;
            log.info("[CartController] addToCart - userId={}, title={}", userId, request.getTitle());
            
            CartItemDto item = cartService.addToCart(userId, request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Track added to cart",
                    "cartItemId", item.getId(),
                    "item", item
            ));
        } catch (IllegalStateException e) {
            log.warn("[CartController] Track already in cart: {}", e.getMessage());
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Track already in cart",
                    "exists", true
            ));
        } catch (Exception e) {
            log.error("[CartController] Error adding to cart: ", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "장바구니 항목 삭제", description = "장바구니에서 트랙을 삭제합니다.")
    public ResponseEntity<?> removeFromCart(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            Long userId = userDetails != null ? userDetails.getUser().getUserId() : 1L;
            log.info("[CartController] removeFromCart - userId={}, cartItemId={}", userId, id);
            
            cartService.removeFromCart(userId, id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Track removed from cart"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Cart item not found"
            ));
        } catch (Exception e) {
            log.error("[CartController] Error removing from cart: ", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @DeleteMapping
    @Operation(summary = "장바구니 비우기", description = "장바구니의 모든 항목을 삭제합니다.")
    public ResponseEntity<?> clearCart(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            Long userId = userDetails != null ? userDetails.getUser().getUserId() : 1L;
            log.info("[CartController] clearCart - userId={}", userId);
            
            cartService.clearCart(userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Cart cleared"
            ));
        } catch (Exception e) {
            log.error("[CartController] Error clearing cart: ", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/analyze")
    @Operation(summary = "장바구니 분석 요청", description = "장바구니의 트랙을 FastAPI에 전달하여 추천받고 GMS로 전달합니다.")
    public ResponseEntity<?> analyzeCart(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            Long userId = userDetails != null ? userDetails.getUser().getUserId() : 1L;
            String model = request.getOrDefault("model", "M1");
            log.info("[CartController] analyzeCart - userId={}, model={}", userId, model);
            
            com.springboot.finalprojcet.domain.cart.dto.CartAnalysisRequestDto req =
                    com.springboot.finalprojcet.domain.cart.dto.CartAnalysisRequestDto.builder()
                            .userId(userId)
                            .model(model)
                            .build();
            
            // 1단계: DB 저장 (트랜잭션 → 여기서 commit)
            Map<String, Object> saveResult = cartService.saveCartAsPlaylist(userId, req);
            if (!(boolean) saveResult.get("success")) {
                return ResponseEntity.internalServerError().body(saveResult);
            }
            log.info("[CartController] Playlist saved - playlistId={}", saveResult.get("playlistId"));

            // 2단계: FastAPI 분석 요청 (commit 이후이므로 FastAPI가 DB 데이터를 볼 수 있음)
            Map<String, Object> evalResult = cartService.requestFastapiEvaluation(userId, model);
            
            // 최종 응답
            saveResult.put("evaluation", evalResult);
            saveResult.put("success", true);
            saveResult.put("message", (boolean) evalResult.get("success") ? "분석 완료" : "분석 요청 완료 (AI 처리 중)");
            
            return ResponseEntity.ok(saveResult);
        } catch (IllegalStateException e) {
            log.warn("[CartController] Cart analysis failed: {}", e.getMessage());
            return ResponseEntity.status(409).body(Map.of(
                    "error", e.getMessage(),
                    "exists", true
            ));
        } catch (Exception e) {
            log.error("[CartController] Error analyzing cart: ", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/train-model")
    @Operation(summary = "사용자 모델 학습", description = "사용자의 플레이리스트를 기반으로 AI 모델 학습을 요청합니다.")
    public ResponseEntity<?> trainModel(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            Long userId = userDetails != null ? userDetails.getUser().getUserId() : 1L;
            String model = request.getOrDefault("model", "M1");
            log.info("[CartController] trainModel - userId={}, model={}", userId, model);
            
            Map<String, Object> result = cartService.trainModel(userId, model);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[CartController] Error training model: ", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}
