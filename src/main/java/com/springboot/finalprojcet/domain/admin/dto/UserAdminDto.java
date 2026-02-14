package com.springboot.finalprojcet.domain.admin.dto;

import com.springboot.finalprojcet.entity.Users;
import com.springboot.finalprojcet.enums.RoleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class UserAdminDto {
    private Long userId;
    private String nickname;
    private String email;
    private RoleType roleType;
    private String grade;
    private LocalDateTime createdAt;

    public static UserAdminDto from(Users user) {
        return UserAdminDto.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .roleType(user.getRoleType())
                .grade(user.getGrade())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
