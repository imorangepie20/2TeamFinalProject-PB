package com.springboot.finalprojcet.domain.user.service.impl;

import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.user.dto.UserPreferencesDto;
import com.springboot.finalprojcet.domain.user.dto.info.InfoResponseDto;
import com.springboot.finalprojcet.domain.user.repository.UserPreferencesRepository;
import com.springboot.finalprojcet.domain.user.repository.UserRepository;
import com.springboot.finalprojcet.domain.user.service.UserService;

import com.springboot.finalprojcet.entity.UserPreferences;
import com.springboot.finalprojcet.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    
    private static final List<String> VALID_AI_MODELS = Arrays.asList("M1", "M2", "M3");

    @Override
    public InfoResponseDto info(CustomUserDetails userDetails) {
        Users user = userDetails.getUser();

        return new InfoResponseDto(
                user.getUserId(),
                user.getNickname(),
                user.getEmail()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserPreferencesDto getPreferences(Long userId) {
        return userPreferencesRepository.findByUserUserId(userId)
                .map(UserPreferencesDto::from)
                .orElse(UserPreferencesDto.builder()
                        .userId(userId)
                        .aiModel("M1")  // Default
                        .emsTrackLimit(100)  // Default
                        .build());
    }

    @Override
    @Transactional
    public UserPreferencesDto updatePreferences(Long userId, String aiModel, Integer emsTrackLimit) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        UserPreferences preferences = userPreferencesRepository.findByUserUserId(userId)
                .orElseGet(() -> UserPreferences.builder()
                        .user(user)
                        .build());
        
        // Update AI model if provided
        if (aiModel != null && VALID_AI_MODELS.contains(aiModel.toUpperCase())) {
            preferences.setAiModel(aiModel.toUpperCase());
        }
        
        // Update EMS track limit if provided (min 10, max 500)
        if (emsTrackLimit != null) {
            int limit = Math.max(10, Math.min(500, emsTrackLimit));
            preferences.setEmsTrackLimit(limit);
        }
        
        userPreferencesRepository.save(preferences);
        
        return UserPreferencesDto.from(preferences);
    }
}
