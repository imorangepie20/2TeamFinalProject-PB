-- MariaDB 11.0 SQL Script
-- 데이터베이스 생성 (없는 경우)
CREATE DATABASE IF NOT EXISTS music_space_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE music_space_db;

-- 1. 회원정보 테이블 (Users)
CREATE TABLE IF NOT EXISTS users (
                                     user_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 고유 ID',
                                     email VARCHAR(255) NOT NULL UNIQUE COMMENT '이메일 (로그인 ID)',
    password_hash VARCHAR(255) NOT NULL COMMENT '비밀번호 해시',
    nickname VARCHAR(100) NOT NULL COMMENT '사용자 닉네임',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '가입일시',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시'
    ) ENGINE=InnoDB COMMENT='사용자 회원가입 및 로그인 정보';

-- 2. 외부 스트리밍 플랫폼 연결 정보 (Connected Platforms)
CREATE TABLE IF NOT EXISTS user_platforms (
                                              platform_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '플랫폼 연결 고유 ID',
                                              user_id BIGINT NOT NULL COMMENT '사용자 ID',
                                              platform_name ENUM('Tidal', 'YouTube Music', 'Apple Music') NOT NULL COMMENT '플랫폼 명',
    access_token TEXT COMMENT '액세스 토큰',
    refresh_token TEXT COMMENT '리프레시 토큰',
    connected_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '연동일시',
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_platform (user_id, platform_name)
    ) ENGINE=InnoDB COMMENT='사용자별 연동된 외부 스트리밍 플랫폼 정보';

-- 3. 플레이리스트 테이블 (Playlists)
-- PTP: Personal Temporary Playlist (개인 임시)
-- PRP: Personal Regular Playlist (개인 정규)
-- PFP: Personal Filtered Playlist (필터링됨/검증 후보)
CREATE TABLE IF NOT EXISTS playlists (
                                         playlist_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '플레이리스트 고유 ID',
                                         user_id BIGINT NOT NULL COMMENT '사용자 ID',
                                         title VARCHAR(200) NOT NULL COMMENT '플레이리스트 제목',
    description TEXT COMMENT '플레이리스트 설명',
    cover_image VARCHAR(500)  '커버 이미지 URL',

    -- 공간 타입: PMS(나의 공간), EMS(외부 공간), GMS(검증/Gateway 공간)
    space_type ENUM('PMS', 'EMS', 'GMS') NOT NULL DEFAULT 'EMS' COMMENT '공간 타입 (PMS, EMS, GMS)',

    -- 상태 플래그: PTP(임시), PRP(정규), PFP(필터링/검증완료)
    status_flag ENUM('PTP', 'PRP', 'PFP') NOT NULL DEFAULT 'PTP' COMMENT '상태 플래그 (PTP:임시, PRP:정규, PFP:필터링됨)',

    source_type ENUM('Platform', 'Upload', 'System') NOT NULL DEFAULT 'Platform' COMMENT '출처 타입 (Platform:스트리밍, Upload:파일업로드, System:시스템생성)',
    external_id VARCHAR(255) COMMENT '외부 플랫폼에서의 플레이리스트 ID',

--    ai_score DECIMAL(5, 2) DEFAULT 0.00 COMMENT 'AI 추천/검증 점수',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    ) ENGINE=InnoDB COMMENT='플레이리스트 정보 (PMS, EMS, GMS 통합 관리)';

-- 4. 트랙(음원) 정보 테이블 (Tracks)
-- 트랙은 여러 플레이리스트에 포함될 수 있으므로 별도 관리
CREATE TABLE IF NOT EXISTS tracks (
                                      track_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '트랙 고유 ID',
                                      title VARCHAR(255) NOT NULL COMMENT '곡 제목',
    artist VARCHAR(255) NOT NULL COMMENT '아티스트',
    album VARCHAR(255) COMMENT '앨범명',
    duration INT COMMENT '재생 시간(초)',
    isrc VARCHAR(50) COMMENT '국제 표준 녹음 코드',

    -- 메타데이터는 JSON으로 유연하게 저장 (플랫폼별 ID 등)
    external_metadata JSON COMMENT '외부 플랫폼별 상세 ID 및 메타데이터 (JSON)',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시'
    ) ENGINE=InnoDB COMMENT='전체 트랙 메타데이터 저장소';

-- 5. 플레이리스트-트랙 매핑 테이블 (Playlist Tracks)
CREATE TABLE IF NOT EXISTS playlist_tracks (
                                               map_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '매핑 ID',
                                               playlist_id BIGINT NOT NULL COMMENT '플레이리스트 ID',
                                               track_id BIGINT NOT NULL COMMENT '트랙 ID',
                                               order_index INT NOT NULL DEFAULT 0 COMMENT '플레이리스트 내 정렬 순서',
                                               added_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '추가된 일시',

                                               FOREIGN KEY (playlist_id) REFERENCES playlists(playlist_id) ON DELETE CASCADE,
    FOREIGN KEY (track_id) REFERENCES tracks(track_id) ON DELETE CASCADE,
    INDEX idx_playlist_order (playlist_id, order_index)
    ) ENGINE=InnoDB COMMENT='플레이리스트와 트랙 간의 관계 정의';

-- 6. AI 분석/학습 로그 (Optional but recommended based on text)
CREATE TABLE IF NOT EXISTS ai_analysis_logs (
                                                log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                user_id BIGINT NOT NULL,
                                                target_type ENUM('Playlist', 'Track') NOT NULL,
    target_id BIGINT NOT NULL,
    score DECIMAL(5, 2),
    analysis_result JSON COMMENT '상세 분석 결과',
    analyzed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    ) ENGINE=InnoDB COMMENT='AI 취향 분석 및 검증 로그';


CREATE TABLE IF NOT EXISTS playlist_scored_id (
                                                  playlist_id BIGINT NOT NULL COMMENT '플레이리스트 ID',
                                                  user_id  BIGINT NOT NULL COMMENT '사용자 ID',
                                                  ai_score DECIMAL(5, 2) DEFAULT 0.00 COMMENT 'AI 추천/검증 점수',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    -- PK 설정: 한 사용자가 한 플레이리스트에 대해 중복 점수를 매기지 못하게 함
    PRIMARY KEY (playlist_id, user_id),

    -- 외래키 설정
    FOREIGN KEY (playlist_id) REFERENCES playlists(playlist_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    ) ENGINE=InnoDB COMMENT='사용자별 플레이리스트 평가 점수';

CREATE TABLE IF NOT EXISTS track_scored_id (
                                               track_id  BIGINT   NOT NULL COMMENT '트랙 ID',
                                               user_id  BIGINT NOT NULL COMMENT '사용자 ID',
                                               ai_score DECIMAL (5, 2) DEFAULT 0.00 COMMENT 'AI 추천/검증 점수',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    -- PK 설정: 한 사용자가 한 트랙에 대해 중복 점수를 매기지 못하게 함
    PRIMARY KEY (track_id, user_id),

    -- 외래키 설정
    FOREIGN KEY (track_id) REFERENCES tracks(track_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    ) ENGINE=InnoDB COMMENT='사용자별 트랙 평가 점수';

ALTER TABLE playlists ADD COLUMN cover_image VARCHAR(500) COMMENT '커버 이미지 URL';