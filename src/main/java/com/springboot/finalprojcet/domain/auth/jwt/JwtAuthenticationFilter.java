package com.springboot.finalprojcet.domain.auth.jwt;


import com.springboot.finalprojcet.domain.auth.service.CostomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter { // OncePerRequestFilter : 요청당 한 번만 실행되는 필터

    private final JwtTokenProvider jwtTokenProvider;
    private final CostomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 헤더에서 토큰 추출
        String bearerToken = request.getHeader("Authorization"); // HTTP헤더에서 Authorization 값(Bearer eyJhbGciOiJIUzI1NiJ9...) 가져옴
        String token = jwtTokenProvider.resolveToken(bearerToken); // bearer을 제외한 나머지 순수 토큰값을 가져옴

        // 토큰 유효성 검증
        if(token != null && jwtTokenProvider.validateToken(token)) { // validateToken(JwtTokenProvider에서 유효성 검사 메소드) 토큰 유효성 검사
            // 토큰에서 이메일 추출
            String email = jwtTokenProvider.getEmail(token); // 토큰에서 이메일을 가져옴

            // DB에서 사용자 조회
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(email); // 이메일로 db에서 조

            // 인증 객체 생성 및 SerucityContext에 저장
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()); // 시큐리티 인증 객체 생성
            /*
                UsernamePasswordAuthenticationToken에는 사용자 정보, 비밀번호, 권한, 인증 유무의 값이 저장됨
                인증된 사용자 정보, null(원래 비밀번호가 들어가지만 이미 토큰 검증을 했으니 필요 없음), 사용자 권한
             */
            SecurityContextHolder.getContext().setAuthentication(authentication); // 인증된 사용자라고 저장
            /*
                SecurityContextHolder : 시큐리티의 금고역할(현재 요청의 인증 정보를 보관하는 곳)
                getContext() : 이건 금고를 여는 함수
                setAuthentication(authentication) : 금고에 인증서를 넣음
                금고 관리자 -> 금고 열기 -> 인증서 넣기
             */
        }

        // 다음 필터로 진행
        filterChain.doFilter(request, response); // 다음필터로 넘기는데 실패해도 넘김, 넘긴건 securityConfig에서 막을거임
    }
}
