package com.springboot.finalprojcet.domain.admin.service.impl;

import com.springboot.finalprojcet.domain.admin.dto.AdminStatsDto;
import com.springboot.finalprojcet.domain.admin.dto.UserAdminDto;
import com.springboot.finalprojcet.domain.admin.service.AdminService;
import com.springboot.finalprojcet.domain.gms.repository.PlaylistRepository;
import com.springboot.finalprojcet.domain.tidal.repository.TracksRepository;
import com.springboot.finalprojcet.domain.user.repository.UserRepository;
import com.springboot.finalprojcet.entity.Users;
import com.springboot.finalprojcet.enums.RoleType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final TracksRepository tracksRepository;
    private final PlaylistRepository playlistRepository;

    @Override
    public Page<UserAdminDto> getAllUsers(String search, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Users> usersPage;
        if (search == null || search.isBlank()) {
            usersPage = userRepository.findAll(pageRequest);
        } else {
            usersPage = userRepository.findByNicknameContainingOrEmailContaining(search, search, pageRequest);
        }

        return usersPage.map(UserAdminDto::from);
    }

    @Override
    @Transactional
    public void updateUserRole(Long userId, RoleType roleType) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
        user.setRoleType(roleType);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId, Long requesterId) {
        if (userId.equals(requesterId)) {
            throw new IllegalArgumentException("본인 계정은 삭제할 수 없습니다");
        }
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
        userRepository.delete(user);
    }

    @Override
    public AdminStatsDto getStats() {
        return AdminStatsDto.builder()
                .totalUsers(userRepository.count())
                .totalTracks(tracksRepository.count())
                .totalPlaylists(playlistRepository.count())
                .build();
    }
}
