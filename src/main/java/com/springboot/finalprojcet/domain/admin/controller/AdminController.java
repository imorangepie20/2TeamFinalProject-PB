package com.springboot.finalprojcet.domain.admin.controller;

import com.springboot.finalprojcet.domain.admin.dto.AdminStatsDto;
import com.springboot.finalprojcet.domain.admin.dto.RoleUpdateRequest;
import com.springboot.finalprojcet.domain.admin.dto.UserAdminDto;
import com.springboot.finalprojcet.domain.admin.service.AdminService;
import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.enums.RoleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "관리자 API")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    @Operation(summary = "전체 사용자 목록", description = "ADMIN/MASTER 권한. 검색·페이징 지원")
    public ResponseEntity<?> getUsers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(403).body(Map.of("error", "권한이 없습니다"));
        }
        Page<UserAdminDto> users = adminService.getAllUsers(search, page, size);
        return ResponseEntity.ok(users);
    }

    @PatchMapping("/users/{userId}/role")
    @Operation(summary = "사용자 역할 변경", description = "MASTER 권한 전용")
    public ResponseEntity<?> updateUserRole(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long userId,
            @RequestBody RoleUpdateRequest request
    ) {
        if (!isMaster(userDetails)) {
            return ResponseEntity.status(403).body(Map.of("error", "MASTER 권한이 필요합니다"));
        }
        adminService.updateUserRole(userId, request.getRoleType());
        return ResponseEntity.ok(Map.of("message", "역할이 변경되었습니다"));
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "사용자 삭제", description = "MASTER 권한 전용. 본인 삭제 불가")
    public ResponseEntity<?> deleteUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long userId
    ) {
        if (!isMaster(userDetails)) {
            return ResponseEntity.status(403).body(Map.of("error", "MASTER 권한이 필요합니다"));
        }
        try {
            adminService.deleteUser(userId, userDetails.getUser().getUserId());
            return ResponseEntity.ok(Map.of("message", "사용자가 삭제되었습니다"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "관리자 대시보드 통계", description = "ADMIN/MASTER 권한")
    public ResponseEntity<?> getStats(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(403).body(Map.of("error", "권한이 없습니다"));
        }
        AdminStatsDto stats = adminService.getStats();
        return ResponseEntity.ok(stats);
    }

    private boolean isAdmin(CustomUserDetails userDetails) {
        if (userDetails == null) return false;
        RoleType role = userDetails.getUser().getRoleType();
        return role == RoleType.ADMIN || role == RoleType.MASTER;
    }

    private boolean isMaster(CustomUserDetails userDetails) {
        if (userDetails == null) return false;
        return userDetails.getUser().getRoleType() == RoleType.MASTER;
    }
}
