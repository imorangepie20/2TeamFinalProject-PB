package com.springboot.finalprojcet.domain.tidal.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis-based storage for Tidal OAuth tokens.
 * Persists tokens across server restarts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TidalTokenStore {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOKEN_KEY_PREFIX = "tidal:token:";
    private static final String PKCE_KEY_PREFIX = "tidal:pkce:";
    
    // Token TTL: 30 days (refresh tokens are long-lived)
    private static final long TOKEN_TTL_DAYS = 30;
    // PKCE context TTL: 10 minutes (short-lived for OAuth flow)
    private static final long PKCE_TTL_MINUTES = 10;

    // ===== Token Info =====
    
    public static class TokenInfo {
        public String accessToken;
        public String refreshToken;
        public long expiresAt;
        public String userId;
        public String countryCode;
        public String username;

        public TokenInfo() {}

        public TokenInfo(String accessToken, String refreshToken, long expiresIn, 
                         String userId, String countryCode, String username) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAt = System.currentTimeMillis() + (expiresIn * 1000);
            this.userId = userId;
            this.countryCode = countryCode;
            this.username = username;
        }
    }

    public void saveToken(String visitorId, TokenInfo tokenInfo) {
        String key = TOKEN_KEY_PREFIX + visitorId;
        try {
            String json = objectMapper.writeValueAsString(tokenInfo);
            redisTemplate.opsForValue().set(key, json, TOKEN_TTL_DAYS, TimeUnit.DAYS);
            log.debug("Saved Tidal token for visitor: {}", visitorId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Tidal token info", e);
            throw new RuntimeException("Failed to save token", e);
        }
    }

    public TokenInfo getToken(String visitorId) {
        String key = TOKEN_KEY_PREFIX + visitorId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, TokenInfo.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize Tidal token info", e);
            return null;
        }
    }

    public void removeToken(String visitorId) {
        String key = TOKEN_KEY_PREFIX + visitorId;
        redisTemplate.delete(key);
        log.debug("Removed Tidal token for visitor: {}", visitorId);
    }

    public boolean hasToken(String visitorId) {
        String key = TOKEN_KEY_PREFIX + visitorId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // ===== PKCE Verifier =====

    public void savePkceVerifier(String visitorId, String codeVerifier) {
        String key = PKCE_KEY_PREFIX + visitorId;
        redisTemplate.opsForValue().set(key, codeVerifier, PKCE_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("Saved Tidal PKCE verifier for visitor: {}", visitorId);
    }

    public String getPkceVerifier(String visitorId) {
        String key = PKCE_KEY_PREFIX + visitorId;
        return redisTemplate.opsForValue().get(key);
    }

    public String removePkceVerifier(String visitorId) {
        String key = PKCE_KEY_PREFIX + visitorId;
        String verifier = redisTemplate.opsForValue().get(key);
        if (verifier != null) {
            redisTemplate.delete(key);
        }
        return verifier;
    }
}
