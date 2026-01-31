package com.springboot.finalprojcet.domain.gms.service.impl;

import com.springboot.finalprojcet.domain.gms.dto.EmsPlaylistResponseDto;
import com.springboot.finalprojcet.domain.gms.dto.RecommendTracksRequestDto;
import com.springboot.finalprojcet.domain.gms.dto.RecommendTracksResponseDto;
import com.springboot.finalprojcet.domain.gms.repository.EmsPlaylistForRecommendRepository;
import com.springboot.finalprojcet.domain.gms.repository.PlaylistRepository;
import com.springboot.finalprojcet.domain.gms.service.GmsRecommendService;
import com.springboot.finalprojcet.domain.user.repository.UserRepository;
import com.springboot.finalprojcet.entity.EmsPlaylistForRecommend;
import com.springboot.finalprojcet.entity.Playlists;
import com.springboot.finalprojcet.entity.Users;
import com.springboot.finalprojcet.enums.RecommendStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmsRecommendServiceImpl implements GmsRecommendService {

    private final EmsPlaylistForRecommendRepository emsPlaylistForRecommendRepository;
    private final PlaylistRepository playlistRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    @Value("${fastapi.url:http://localhost:8000}")
    private String fastApiUrl;

    @Override
    @Transactional
    public void successFirstModel(Long userId) {
        log.info("AI 모델 학습 완료 콜백 수신 - userId: {}", userId);

        // EMS 플레이리스트 3개 생성 및 평가 요청
        List<EmsPlaylistResponseDto> createdPlaylists = makeEmsPlaylistsForRecommend(userId, 3);

        log.info("EMS 플레이리스트 {} 개 생성 완료 - userId: {}", createdPlaylists.size(), userId);
    }

    @Override
    @Transactional
    public List<EmsPlaylistResponseDto> makeEmsPlaylistsForRecommend(Long userId, int recommendCount) {
        log.info("EMS 플레이리스트 추천용 생성 시작 - userId: {}, count: {}", userId, recommendCount);

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다. userId: " + userId));

        // 사용자의 EMS 플레이리스트 중 랜덤으로 recommendCount개 선택
        List<Playlists> emsPlaylists = playlistRepository.findRandomEmsByUserId(userId, recommendCount);

        if (emsPlaylists.isEmpty()) {
            log.warn("사용자의 EMS 플레이리스트가 없습니다. userId: {}", userId);
            return new ArrayList<>();
        }

        List<EmsPlaylistForRecommend> createdEntities = new ArrayList<>();

        for (Playlists playlist : emsPlaylists) {
            // 이미 추천용으로 등록된 플레이리스트인지 확인
            if (!emsPlaylistForRecommendRepository.existsByPlaylistPlaylistIdAndUserUserId(
                    playlist.getPlaylistId(), userId)) {

                EmsPlaylistForRecommend entity = EmsPlaylistForRecommend.builder()
                        .playlist(playlist)
                        .user(user)
                        .status(RecommendStatus.valid)
                        .build();

                createdEntities.add(emsPlaylistForRecommendRepository.save(entity));
            }
        }

        log.info("EMS 플레이리스트 추천용 {} 개 저장 완료", createdEntities.size());

        // FastAPI 평가 요청
        callFastApiEvaluation(userId);

        return createdEntities.stream()
                .map(EmsPlaylistResponseDto::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RecommendTracksResponseDto sendRecommendTracks(RecommendTracksRequestDto request) {
        log.info("추천 트랙 수신 - userId: {}, trackCount: {}",
                request.getUserId(), request.getTracks().size());

        // TODO: 추천 트랙 저장 로직 구현
        // GMS 공간에 추천 트랙 저장 또는 사용자에게 알림 등

        List<Long> trackIds = request.getTracks().stream()
                .map(RecommendTracksRequestDto.TrackDto::getTrackId)
                .collect(Collectors.toList());

        return RecommendTracksResponseDto.builder()
                .userId(request.getUserId())
                .trackCount(trackIds.size())
                .trackIds(trackIds)
                .message("추천 트랙 " + trackIds.size() + "개 수신 완료")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmsPlaylistResponseDto> getValidRecommendPlaylists(Long userId) {
        return emsPlaylistForRecommendRepository.findValidByUserId(userId)
                .stream()
                .map(EmsPlaylistResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * FastAPI에 EMS 플레이리스트 평가 요청
     */
    private void callFastApiEvaluation(Long userId) {
        try {
            String url = fastApiUrl + "/api/v1/evaluation/start";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("userId", userId);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.info("FastAPI 평가 요청 - url: {}, userId: {}", url, userId);
            restTemplate.postForEntity(url, request, String.class);

        } catch (Exception e) {
            log.error("FastAPI 평가 요청 실패 - userId: {}, error: {}", userId, e.getMessage());
            // 실패해도 예외를 던지지 않고 로그만 기록 (비동기 처리 권장)
        }
    }
}
