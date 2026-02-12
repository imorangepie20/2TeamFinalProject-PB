# Spring Boot 백엔드 (2TeamFinalProject-BE)

전체 프로젝트 정보는 `humamAppleTeamPreject001/CLAUDE.md` 참조.

## DB 설정
- **Spring Boot는 자체 DB를 사용하지 않음**
- **메인 프로젝트(humamAppleTeamPreject001)의 기존 MariaDB를 공유해서 사용**
- application.yml의 DB 설정은 동일한 MariaDB를 가리켜야 함
- DB명: `music_space_db`
- 스키마 정의: `humamAppleTeamPreject001/docs/dbSchema.sql`

## 도메인 구조
```
domain/
├── auth/       — 인증/회원가입 (CustomUserDetails, JWT)
├── playlist/   — 플레이리스트 CRUD, 트랙 관리, UserDismissedPlaylistRepository
├── youtube/    — YouTube Music OAuth + 플레이리스트 import
├── tidal/      — Tidal OAuth + 플레이리스트 import
├── spotify/    — Spotify OAuth + 플레이리스트 import
├── ems/        — External Music Space (외부 연동 공간) — PlaylistRepository 사용
├── pms/        — Personal Music Space (개인 공간) — PlaylistRepository 사용
├── gms/        — Gateway Music Space (AI 추천 공간) — PlaylistRepository 정의 위치
├── analysis/   — AI 분석 서비스 — PlaylistRepository 사용
├── training/   — AI 학습 서비스
├── genre/      — 장르 관리
├── stats/      — 통계 서비스
├── cart/       — 장바구니
├── settings/   — 시스템 설정 (전역 테마)
├── itunes/     — iTunes 검색
├── user/       — 사용자 관리
└── common/     — 공통 유틸리티
```

## 주의사항
- Entity, Enum 등은 기존 DB 스키마에 맞춰야 함
- StatusFlag에 `active` 값 추가됨 (Node.js에서 사용)
- **PlaylistRepository는 ems, pms, gms, analysis 등 여러 도메인에서 공유 사용**
- **메서드 시그니처 변경 시 반드시 모든 호출처(caller) 확인할 것**
- **기존에 잘 동작하던 코드는 건드리지 말 것**
- **기능 추가 시 다른 도메인 서비스에 영향 주지 않도록 주의**
- **다중 사용자 환경** — 한 사용자의 액션이 다른 사용자에게 영향 주면 안 됨

## 환경변수 (docker-compose.yml)
- TIDAL_CLIENT_ID, TIDAL_CLIENT_SECRET, TIDAL_REDIRECT_URI 필요
- 메인 프로젝트와 동일한 환경변수 사용

## 빌드/배포
- `docker-compose -f docker-compose.fullstack-local.yml up -d --no-deps --build spring-backend`
- 로그: `docker logs musicspace-spring-backend`

## Git
- 리모트: `https://github.com/imorangepie20/2TeamFinalProject-PB.git`
- push 시 3개 서브 프로젝트(FE, BE, FastAPI) 모두 확인
