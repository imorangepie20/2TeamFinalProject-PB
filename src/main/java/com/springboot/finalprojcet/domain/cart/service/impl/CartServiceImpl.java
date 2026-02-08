package com.springboot.finalprojcet.domain.cart.service.impl;

import com.springboot.finalprojcet.domain.cart.dto.CartAddRequestDto;
import com.springboot.finalprojcet.domain.cart.dto.CartAnalysisRequestDto;
import com.springboot.finalprojcet.domain.cart.dto.CartItemDto;
import com.springboot.finalprojcet.domain.cart.dto.CartResponseDto;
import com.springboot.finalprojcet.domain.cart.repository.UserCartRepository;
import com.springboot.finalprojcet.domain.cart.service.CartService;
import com.springboot.finalprojcet.domain.gms.repository.PlaylistRepository;
import com.springboot.finalprojcet.domain.tidal.repository.TracksRepository;
import com.springboot.finalprojcet.domain.tidal.repository.PlaylistTracksRepository;
import com.springboot.finalprojcet.domain.user.repository.UserRepository;
import com.springboot.finalprojcet.entity.PlaylistTracks;
import com.springboot.finalprojcet.entity.Tracks;
import com.springboot.finalprojcet.entity.UserCart;
import com.springboot.finalprojcet.entity.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CartServiceImpl implements CartService {

    private final UserCartRepository cartRepository;
    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;
    private final TracksRepository tracksRepository;
    private final PlaylistTracksRepository playlistTracksRepository;
    private final RestTemplate restTemplate;

    @Value("${fastapi.url:http://fastapi:8000}")
    private String fastApiUrl;

    @Override
    @Transactional(readOnly = true)
    public CartResponseDto getCart(Long userId) {
        log.info("[CartService] getCart - userId={}", userId);
        
        List<UserCart> cartItems = cartRepository.findByUserUserIdOrderByCreatedAtDesc(userId);
        List<CartItemDto> dtos = cartItems.stream()
                .map(CartItemDto::fromEntity)
                .collect(Collectors.toList());
        
        return CartResponseDto.builder()
                .success(true)
                .cart(dtos)
                .count(dtos.size())
                .build();
    }

    @Override
    public CartItemDto addToCart(Long userId, CartAddRequestDto request) {
        log.info("[CartService] addToCart - userId={}, title={}, artist={}", 
                userId, request.getTitle(), request.getArtist());
        
        // 중복 체크
        if (cartRepository.existsByUserUserIdAndTitleAndArtist(
                userId, request.getTitle(), request.getArtist())) {
            throw new IllegalStateException("Track already in cart");
        }
        
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        
        UserCart cartItem = UserCart.builder()
                .user(user)
                .trackId(request.getTrackId())
                .title(request.getTitle())
                .artist(request.getArtist())
                .album(request.getAlbum())
                .artwork(request.getArtwork())
                .previewUrl(request.getPreviewUrl())
                .externalId(request.getExternalId())
                .build();
        
        UserCart saved = cartRepository.save(cartItem);
        log.info("[CartService] Cart item saved - id={}", saved.getId());
        
        return CartItemDto.fromEntity(saved);
    }

    @Override
    public void removeFromCart(Long userId, Long cartItemId) {
        log.info("[CartService] removeFromCart - userId={}, cartItemId={}", userId, cartItemId);
        
        UserCart cartItem = cartRepository.findByIdAndUserUserId(cartItemId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found"));
        
        cartRepository.delete(cartItem);
        log.info("[CartService] Cart item deleted - id={}", cartItemId);
    }

    @Override
    public void clearCart(Long userId) {
        log.info("[CartService] clearCart - userId={}", userId);
        cartRepository.deleteByUserUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isInCart(Long userId, String title, String artist) {
        return cartRepository.existsByUserUserIdAndTitleAndArtist(userId, title, artist);
    }

    @Override
    public Map<String, Object> analyzeCart(Long userId, CartAnalysisRequestDto request) {
        // 하위 호환용 - Controller에서 직접 saveCartAsPlaylist + requestFastapiEvaluation 사용
        Map<String, Object> saveResult = saveCartAsPlaylist(userId, request);
        if (!(boolean) saveResult.get("success")) {
            return saveResult;
        }
        String model = request.getModel() != null ? request.getModel() : "M1";
        Map<String, Object> evalResult = requestFastapiEvaluation(userId, model);
        saveResult.put("evaluation", evalResult);
        return saveResult;
    }

    @Override
    public Map<String, Object> saveCartAsPlaylist(Long userId, CartAnalysisRequestDto request) {
        log.info("[CartService] saveCartAsPlaylist - userId={}, model={}", userId, request.getModel());

        List<UserCart> cartItems = cartRepository.findByUserUserIdOrderByCreatedAtDesc(userId);
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        // 1. 플레이리스트 생성
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        String title = "분석 요청 (" + today + ")";

        com.springboot.finalprojcet.entity.Playlists playlist = com.springboot.finalprojcet.entity.Playlists.builder()
                .user(userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId)))
                .title(title)
                .description("AI 분석 요청 (" + cartItems.size() + " tracks)")
                .sourceType(com.springboot.finalprojcet.enums.SourceType.Upload)
                .spaceType(com.springboot.finalprojcet.enums.SpaceType.EMS)
                .statusFlag(com.springboot.finalprojcet.enums.StatusFlag.PTP)
                .coverImage(cartItems.get(0).getArtwork())
                .build();

        playlist = playlistRepository.save(playlist);
        log.info("[CartService] Playlist created - id={}", playlist.getPlaylistId());

        // 1-2. 장바구니 트랙들을 tracks 테이블에 저장하고 playlist_tracks에 연결
        int orderIdx = 0;
        for (UserCart cartItem : cartItems) {
            Tracks track = null;
            if (cartItem.getTrackId() != null) {
                track = tracksRepository.findById(cartItem.getTrackId()).orElse(null);
            }
            
            if (track == null) {
                track = Tracks.builder()
                        .title(cartItem.getTitle())
                        .artist(cartItem.getArtist())
                        .album(cartItem.getAlbum() != null ? cartItem.getAlbum() : "")
                        .artwork(cartItem.getArtwork())
                        .build();
                
                if (cartItem.getPreviewUrl() != null && !cartItem.getPreviewUrl().isEmpty()) {
                    track.setExternalMetadata("{\"previewUrl\":\"" + cartItem.getPreviewUrl().replace("\"", "\\\"") + "\"}");
                }
                
                track = tracksRepository.save(track);
                log.info("[CartService] Track created - id={}, title={}", track.getTrackId(), track.getTitle());
            }
            
            PlaylistTracks pt = PlaylistTracks.builder()
                    .playlist(playlist)
                    .track(track)
                    .orderIndex(orderIdx++)
                    .build();
            playlistTracksRepository.save(pt);
        }
        log.info("[CartService] {} tracks linked to playlist {}", orderIdx, playlist.getPlaylistId());

        // 2. 장바구니 비우기
        cartRepository.deleteByUserUserId(userId);
        log.info("[CartService] Cart cleared - userId={}", userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("playlistId", playlist.getPlaylistId());
        result.put("trackCount", cartItems.size());
        result.put("message", "플레이리스트 저장 완료");

        return result;
    }

    @Override
    public Map<String, Object> requestFastapiEvaluation(Long userId, String model) {
        log.info("[CartService] requestFastapiEvaluation - userId={}, model={}", userId, model);

        try {
            String url = fastApiUrl + "/api/v1/evaluation/start";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("userId", userId);
            body.put("model", model != null ? model : "M1");

            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(body, headers);

            log.info("[CartService] Requesting FastAPI evaluation - url: {}, userId: {}", url, userId);
            ResponseEntity<String> response = restTemplate.postForEntity(url, httpRequest, String.class);
            log.info("[CartService] FastAPI evaluation completed - status: {}", response.getStatusCode());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "AI 분석 완료");
            result.put("response", response.getBody());
            return result;

        } catch (Exception e) {
            log.error("[CartService] FastAPI evaluation failed - userId: {}, error: {}", userId, e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "AI 분석 실패: " + e.getMessage());
            return result;
        }
    }

    @Override
    public Map<String, Object> trainModel(Long userId, String model) {
        log.info("[CartService] trainModel - userId={}, model={}", userId, model);

        try {
            String url = fastApiUrl + "/api/v1/training/start";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("userId", userId);
            body.put("model", model != null ? model : "M1");

            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(body, headers);

            log.info("[CartService] Requesting FastAPI training - url: {}, userId: {}", url, userId);
            ResponseEntity<String> response = restTemplate.postForEntity(url, httpRequest, String.class);

            log.info("[CartService] FastAPI training requested - status: {}", response.getStatusCode());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "모델 학습 요청 완료");
            return result;

        } catch (Exception e) {
            log.error("[CartService] FastAPI training request failed - userId: {}, error: {}", userId, e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "모델 학습 요청 실패: " + e.getMessage());
            return result;
        }
    }
}
