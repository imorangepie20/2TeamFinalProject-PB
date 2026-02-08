package com.springboot.finalprojcet.domain.common.service;

import java.util.List;

public interface ImageService {
    String downloadImage(String imageUrl, String subDir);
    
    /**
     * 여러 이미지를 2x2 그리드로 합성
     * @param imageUrls 이미지 URL 목록 (최대 4개 사용)
     * @param subDir 저장 서브 디렉토리
     * @return 합성된 이미지의 로컬 경로
     */
    String createGridImage(List<String> imageUrls, String subDir);
}
