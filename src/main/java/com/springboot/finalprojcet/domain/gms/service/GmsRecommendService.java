package com.springboot.finalprojcet.domain.gms.service;

import com.springboot.finalprojcet.domain.gms.dto.EmsPlaylistResponseDto;
import com.springboot.finalprojcet.domain.gms.dto.RecommendTracksRequestDto;
import com.springboot.finalprojcet.domain.gms.dto.RecommendTracksResponseDto;

import java.util.List;

public interface GmsRecommendService {

    /**
     * AI 모델 학습 완료 후 호출되는 콜백
     * FastAPI에서 모델 학습 완료 후 호출
     * @param userId 사용자 ID
     */
    void successFirstModel(Long userId);

    /**
     * 사용자를 위한 EMS 플레이리스트를 추천용으로 생성
     * @param userId 사용자 ID
     * @param recommendCount 생성할 추천 플레이리스트 수 (기본값: 3)
     * @return 생성된 EMS 플레이리스트 목록
     */
    List<EmsPlaylistResponseDto> makeEmsPlaylistsForRecommend(Long userId, int recommendCount);

    /**
     * FastAPI로부터 추천 트랙을 전달받아 처리
     * @param request 추천 트랙 요청 DTO
     * @return 처리 결과
     */
    RecommendTracksResponseDto sendRecommendTracks(RecommendTracksRequestDto request);

    /**
     * 사용자의 valid 상태인 추천 플레이리스트 조회
     * @param userId 사용자 ID
     * @return 유효한 추천 플레이리스트 목록
     */
    List<EmsPlaylistResponseDto> getValidRecommendPlaylists(Long userId);
}
