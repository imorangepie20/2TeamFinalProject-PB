package com.springboot.finalprojcet.domain.admin.service;

import com.springboot.finalprojcet.domain.admin.dto.AdminStatsDto;
import com.springboot.finalprojcet.domain.admin.dto.UserAdminDto;
import com.springboot.finalprojcet.enums.RoleType;
import org.springframework.data.domain.Page;

public interface AdminService {
    Page<UserAdminDto> getAllUsers(String search, int page, int size);
    void updateUserRole(Long userId, RoleType roleType);
    void deleteUser(Long userId, Long requesterId);
    AdminStatsDto getStats();
}
