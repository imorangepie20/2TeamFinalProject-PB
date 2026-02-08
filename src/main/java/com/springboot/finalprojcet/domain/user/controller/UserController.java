package com.springboot.finalprojcet.domain.user.controller;


import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.user.dto.UserPreferencesDto;
import com.springboot.finalprojcet.domain.user.dto.info.InfoResponseDto;
import com.springboot.finalprojcet.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@Tag(name = "User", description = "유저 관련 API")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/info")
    @Operation(summary = "유저 정보 조회", description = "본인의 정보를 조회합니다.")
    public ResponseEntity<InfoResponseDto> info(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        InfoResponseDto infoResponseDto = userService.info(userDetails);
        return ResponseEntity.ok(infoResponseDto);
    }

    @GetMapping("/preferences")
    @Operation(summary = "사용자 환경설정 조회", description = "AI 모델 선택 등 사용자 환경설정을 조회합니다.")
    public ResponseEntity<UserPreferencesDto> getPreferences(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UserPreferencesDto preferences = userService.getPreferences(userDetails.getUser().getUserId());
        return ResponseEntity.ok(preferences);
    }

    @PatchMapping("/preferences")
    @Operation(summary = "사용자 환경설정 수정", description = "AI 모델 선택 및 EMS 곡 수 설정을 수정합니다.")
    public ResponseEntity<UserPreferencesDto> updatePreferences(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body
    ) {
        String aiModel = body.get("aiModel") != null ? body.get("aiModel").toString() : null;
        Integer emsTrackLimit = body.get("emsTrackLimit") != null 
                ? Integer.parseInt(body.get("emsTrackLimit").toString()) 
                : null;
        
        UserPreferencesDto preferences = userService.updatePreferences(
                userDetails.getUser().getUserId(), 
                aiModel, 
                emsTrackLimit
        );
        return ResponseEntity.ok(preferences);
    }
}
