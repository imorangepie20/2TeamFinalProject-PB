package com.springboot.finalprojcet.domain.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j // 로그 메소드 사용하는 어노테이션
@Component // spring Bean으로 등록
           // 다른 클래스에서 @Autowired 나 생성자 주입으로 가져다 쓸 수 있음
@RequiredArgsConstructor // final 필드를 파라미터로 받는 생성자를 자동 생성
                         // JwtProperties를 자동으로 주입받음
public class JwtTokenProvider {
    private final JwtProperties jwtProperties;

    private SecretKey getSecretKey() {
        String secret = jwtProperties.getSecret();
        // Ensure key is at least 256 bits (32 bytes) for HS256
        while (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            secret = secret + secret;
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length > 32) {
            keyBytes = java.util.Arrays.copyOf(keyBytes, 32);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
    /*
        Jwt 서명할 때 문자열 그대로 사용하지 못해서 'SecretKey' 객체로 변환해야 함
        동작 과정
         -> jwtProperties.getSercet() : application-dev.properties에서 설정한 jwt.secret 값 가져옴
         -> BizConnect-Secret-Key-For-JWT-Authentication-2024-Very-Long-Key-Here(문자열)
         -> .getBytes(StandarCharsets.UTF_8) : 문자열을 바이트 배열로 변환. UTF_8은 한글도 꺠지지 않게 인코딩하는 방식
         -> [66, 105, 122, 67, ...](바이트 배열)
         -> keys.hmacShaKeyFor() -> SecretKey객체 : jjwt 라이브러리가 제공하는 메소드, 바이트 배열을 HMAC-SHA 알고리즘용 SecretKey로 변환
     */

// access 토큰 생성
    public String createAccessToken(String email, Long userId, String role) {
        return createToken(email, userId, role, jwtProperties.getAccessTokenExpiration());
    }

    // refresh 토큰 생성
    public String createRefreshToken(String email, Long userId, String role) {
        return createToken(email, userId, role, jwtProperties.getRefreshTokenExpiration());
    }

    private String createToken(String email, Long userId, String role, long expiration) {
        Date now = new Date();
        Date expriedate = new Date(now.getTime() + expiration); // 현재 시간에 expiration을 더한 값을 저장

        return Jwts.builder()
                .subject(email) // 토큰 주인 이메일로 저장
                .claim("uid", userId) // userId 저장
                .claim("role", role) // 권한 저장
                .issuedAt(now) // 현재 시간 저장
                .expiration(expriedate) // 토큰 만료 시간 저장
                .signWith(getSecretKey()) // 비밀키로 서명. 이게 있어야 위조 여부 확인 가능
                .compact(); // 최종 토큰 문자열 생성
    }

    // 토큰 유효성 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parser() // 토큰 파싱(분석) 시작
                    .verifyWith(getSecretKey()) // 이 비밀키로 서명 검증
                    .build() // 설정 끝이라는 끝(객체 생성 완료)
                    .parseSignedClaims(token); // 토큰 과싱 실행. 여기서 검증함
            return true;
            /*
                서명이 유효한가?(위조 여부)
                만료되지 않았는가?
                토큰 형식이 올바른가
             */
        }catch (Exception e) {
            log.error("토큰 검증 실패 : {}", e.getMessage());
            return false;
        }
    }

    // 토큰에서 role 추출
    public String getRole(String token) {
        // Claims는 Json 덩어리
        Claims claims = Jwts.parser() // 분석시작
                .verifyWith(getSecretKey()) // 이 비밀키로 서명 검증
                .build() // 설정 끝
                .parseSignedClaims(token) // 토큰 파싱(여기서 검증 시작)
                .getPayload(); // 토큰 내용물(Claims) 꺼내기 ex) Claims claims = { sub: "hong@company.com", role: "일반유저", ... }
        return claims.get("role", String.class);
    }

// 토큰에서 이메일 추출
    public String getEmail(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    // 토큰에서 userId 추출
    public Long getUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("uid", Long.class);
    }

    /*
        role과 email 추출방법이 return 에서 다른이유
        : email은 Jwt표준 필드라 가능하지만 role은 내가 커스텀해서 만든 값이라 get으로 요청하는거임
     */


    // Authorization 헤더에서 토큰 추출
    public String resolveToken(String bearerToken) { // 클라이언트가 api 요청할 때 코큰을 헤더에 Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...이런식으로 보냄 그래서 토큰을 추출
        if(bearerToken != null && bearerToken.startsWith("Bearer ")) { // bearerToken에 값이 존재하고 안에 Bearer로 시작하면 앞에 7글자를 잘라내고 반환 0에서 6까지 즉 bearer 제거 아니면 null 리턴
            return bearerToken.substring(7);
        }
        return null;
    }
}
