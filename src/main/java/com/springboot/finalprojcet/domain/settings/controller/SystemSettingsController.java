package com.springboot.finalprojcet.domain.settings.controller;

import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.settings.repository.SystemSettingsRepository;
import com.springboot.finalprojcet.entity.SystemSettings;
import com.springboot.finalprojcet.enums.RoleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings", description = "시스템 전역 설정 API")
@RequiredArgsConstructor
public class SystemSettingsController {

    private final SystemSettingsRepository systemSettingsRepository;

    @GetMapping("/theme")
    @Operation(summary = "현재 테마 조회", description = "전체 공개 - 인증 불필요")
    public ResponseEntity<Map<String, String>> getTheme() {
        String theme = systemSettingsRepository.findById("theme")
                .map(SystemSettings::getSettingValue)
                .orElse("default");
        return ResponseEntity.ok(Map.of("theme", theme));
    }

    @PutMapping("/theme")
    @Operation(summary = "테마 변경", description = "MASTER 권한 전용")
    public ResponseEntity<?> setTheme(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> body
    ) {
        if (userDetails == null || userDetails.getUser().getRoleType() != RoleType.MASTER) {
            return ResponseEntity.status(403).body(Map.of("error", "권한이 없습니다"));
        }

        String newTheme = body.get("theme");
        if (!List.of("default", "jazz", "soul").contains(newTheme)) {
            return ResponseEntity.badRequest().body(Map.of("error", "유효하지 않은 테마입니다"));
        }

        systemSettingsRepository.save(SystemSettings.builder()
                .settingKey("theme")
                .settingValue(newTheme)
                .updatedAt(LocalDateTime.now())
                .build());

        return ResponseEntity.ok(Map.of("theme", newTheme));
    }
}
