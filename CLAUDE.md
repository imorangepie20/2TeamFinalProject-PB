# Spring Boot 프로젝트 중요 사항

## DB 설정
- **Spring Boot는 자체 DB를 사용하지 않음**
- **Node.js 프로젝트(humamAppleTeamPreject001)의 기존 DB를 공유해서 사용**
- application.yml의 DB 설정은 Node.js와 동일한 MariaDB를 가리켜야 함

## 마이그레이션 현황
- Node.js → Spring Boot 회원가입/인증 이전 중
- Tidal 연동 API 이전 완료

## 주의사항
- Entity, Enum 등은 기존 DB 스키마에 맞춰야 함
- StatusFlag에 `active` 값 추가됨 (Node.js에서 사용)

## 환경변수 (docker-compose.yml)
- TIDAL_CLIENT_ID, TIDAL_CLIENT_SECRET, TIDAL_REDIRECT_URI 필요
- Node.js와 동일한 환경변수 사용
