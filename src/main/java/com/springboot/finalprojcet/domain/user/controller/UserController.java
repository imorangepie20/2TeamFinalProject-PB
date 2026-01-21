package com.springboot.finalprojcet.domain.user.controller;


import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.user.dto.info.InfoResponseDto;
import com.springboot.finalprojcet.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
