package com.springboot.finalprojcet.domain.user.service;


import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.user.dto.UserPreferencesDto;
import com.springboot.finalprojcet.domain.user.dto.info.InfoResponseDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

public interface UserService {
    InfoResponseDto info(@AuthenticationPrincipal CustomUserDetails userDetails);
    
    UserPreferencesDto getPreferences(Long userId);
    
    UserPreferencesDto updatePreferences(Long userId, String aiModel, Integer emsTrackLimit);
}
