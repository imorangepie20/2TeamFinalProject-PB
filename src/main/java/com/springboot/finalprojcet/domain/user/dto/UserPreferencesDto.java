package com.springboot.finalprojcet.domain.user.dto;

import com.springboot.finalprojcet.entity.UserPreferences;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesDto {
    
    private Long userId;
    private String aiModel;
    private Integer emsTrackLimit;
    
    public static UserPreferencesDto from(UserPreferences entity) {
        return UserPreferencesDto.builder()
                .userId(entity.getUser().getUserId())
                .aiModel(entity.getAiModel())
                .emsTrackLimit(entity.getEmsTrackLimit())
                .build();
    }
}
