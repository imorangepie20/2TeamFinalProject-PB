package com.springboot.finalprojcet.config;

import com.springboot.finalprojcet.domain.auth.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // 인증 없이 접근 가능 (URL 매핑 중복 대응)
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        // GMS API (FastAPI 콜백용)
                        .requestMatchers("/gms/**").permitAll()
                        .requestMatchers("/api/gms/**").permitAll()
                        // Tidal API (전체 허용 - Tidal 자체 토큰으로 인증)
                        .requestMatchers("/api/tidal/**").permitAll()
                        .requestMatchers("/api/tidal/auth/device").permitAll()
                        .requestMatchers("/api/tidal/auth/token").permitAll()
                        .requestMatchers("/api/tidal/auth/token").permitAll()
                        // Settings (테마 등 전역 설정 - GET만 공개)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/settings/theme").permitAll()
                        // Playlists
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/playlists").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/playlists/**").permitAll()
                        // Stats
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/stats/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/stats/**").permitAll()
                        // Genres & Analysis
                        .requestMatchers("/api/genres/**").permitAll()
                        .requestMatchers("/api/analysis/**").permitAll()
                        .requestMatchers("/api/analysis/**").permitAll()
                        // PMS & EMS
                        // PMS & EMS
                        .requestMatchers("/api/pms/**").permitAll()
                        .requestMatchers("/api/ems/**").permitAll()
                        // LLM (Deep Dive)
                        .requestMatchers("/api/llm/**").permitAll()
                        // External (iTunes, Spotify)
                        .requestMatchers("/api/itunes/**").permitAll()
                        .requestMatchers("/api/spotify/**").permitAll()
                        .requestMatchers("/api/youtube/**").permitAll()
                        .requestMatchers("/api/youtube-music/**").permitAll()
                        .requestMatchers("/api/training/**").permitAll()
                        // Cart API (장바구니 - 분석 요청 포함)
                        .requestMatchers("/api/cart/**").permitAll()
                        // Playlists POST (플레이리스트 생성/수정)
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/playlists").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/playlists/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/playlists/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/playlists/**").permitAll()
                        // auth.
                        // auth.
                        // Node.js
                        // required
                        // auth?
                        // No,
                        // typically
                        // stats
                        // are
                        // public
                        // or
                        // implicit.
                        // Swagger UI
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/swagger-ui.html").permitAll()
                        .requestMatchers("/api/swagger-ui/**").permitAll()
                        .requestMatchers("/api/swagger-ui.html").permitAll()
                        .requestMatchers("/swagger-resources/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        // Actuator
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/actuator/**").permitAll()
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/error").permitAll()
                        // 나머지는 인증 필요
                        .anyRequest().authenticated());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
