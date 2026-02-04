package com.springboot.finalprojcet.domain.youtube.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis-based storage for YouTube OAuth tokens.
 * Persists tokens across server restarts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class YoutubeTokenStore {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOKEN_KEY_PREFIX = "youtube:token:";
    private static final String PKCE_KEY_PREFIX = "youtube:pkce:";
    
    // Token TTL: 30 days (refresh tokens are long-lived)
    private static final long TOKEN_TTL_DAYS = 30;
    // PKCE context TTL: 10 minutes (short-lived for OAuth flow)
    private static final long PKCE_TTL_MINUTES = 10;

    // ===== Token Info =====
    
    public static class TokenInfo {
        public String accessToken;
        public String refreshToken;
        public long expiresAt;

        public TokenInfo() {}

        public TokenInfo(String accessToken, String refreshToken, long expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAt = System.currentTimeMillis() + (expiresIn * 1000);
        }
    }

    public void saveToken(String visitorId, TokenInfo tokenInfo) {
        String key = TOKEN_KEY_PREFIX + visitorId;
        try {
            String json = objectMapper.writeValueAsString(tokenInfo);
            redisTemplate.opsForValue().set(key, json, TOKEN_TTL_DAYS, TimeUnit.DAYS);
            log.debug("Saved YouTube token for visitor: {}", visitorId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize token info", e);
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
            log.error("Failed to deserialize token info", e);
            return null;
        }
    }

    public void removeToken(String visitorId) {
        String key = TOKEN_KEY_PREFIX + visitorId;
        redisTemplate.delete(key);
        log.debug("Removed YouTube token for visitor: {}", visitorId);
    }

    public boolean hasToken(String visitorId) {
        String key = TOKEN_KEY_PREFIX + visitorId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // ===== PKCE Context =====

    public static class PkceContext {
        public String codeVerifier;
        public String visitorId;

        public PkceContext() {}

        public PkceContext(String codeVerifier, String visitorId) {
            this.codeVerifier = codeVerifier;
            this.visitorId = visitorId;
        }
    }

    public void savePkceContext(String state, PkceContext context) {
        String key = PKCE_KEY_PREFIX + state;
        try {
            String json = objectMapper.writeValueAsString(context);
            redisTemplate.opsForValue().set(key, json, PKCE_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Saved PKCE context for state: {}", state);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PKCE context", e);
            throw new RuntimeException("Failed to save PKCE context", e);
        }
    }

    public PkceContext getPkceContext(String state) {
        String key = PKCE_KEY_PREFIX + state;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PkceContext.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize PKCE context", e);
            return null;
        }
    }

    public PkceContext removePkceContext(String state) {
        String key = PKCE_KEY_PREFIX + state;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        redisTemplate.delete(key);
        try {
            return objectMapper.readValue(json, PkceContext.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize PKCE context", e);
            return null;
        }
    }
}
