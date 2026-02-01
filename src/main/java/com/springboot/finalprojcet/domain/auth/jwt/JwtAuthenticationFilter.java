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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
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
        String bearerToken = request.getHeader("Authorization"); // HTTP헤더에서 Authorization 값(Bearer
                                                                 // eyJhbGciOiJIUzI1NiJ9...) 가져옴
        String token = jwtTokenProvider.resolveToken(bearerToken); // bearer을 제외한 나머지 순수 토큰값을 가져옴

        // 토큰 유효성 검증
        if (token != null && jwtTokenProvider.validateToken(token)) { // validateToken(JwtTokenProvider에서 유효성 검사 메소드) 토큰
                                                                      // 유효성 검사
            // 토큰에서 이메일 추출
            String email = jwtTokenProvider.getEmail(token); // 토큰에서 이메일을 가져옴

            // DB에서 사용자 조회
            UserDetails userDetails = null;
            try {
                userDetails = customUserDetailsService.loadUserByUsername(email);
            } catch (UsernameNotFoundException e) {
                log.warn("User not found for email: {} - Proceeding as Anonymous", email);
            }

            if (userDetails != null) {
                // 인증 객체 생성 및 SerucityContext에 저장
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 다음 필터로 진행
        filterChain.doFilter(request, response); // 다음필터로 넘기는데 실패해도 넘김, 넘긴건 securityConfig에서 막을거임
    }
}
