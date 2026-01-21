package com.springboot.finalprojcet.domain.user.service.impl;

import com.springboot.finalprojcet.domain.auth.service.CustomUserDetails;
import com.springboot.finalprojcet.domain.user.dto.info.InfoResponseDto;
import com.springboot.finalprojcet.domain.user.repository.UserRepository;
import com.springboot.finalprojcet.domain.user.service.UserService;

import com.springboot.finalprojcet.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    @Override
    public InfoResponseDto info(CustomUserDetails userDetails) {
        Users user = userDetails.getUser();

        return new InfoResponseDto(
                user.getUserId(),
                user.getNickname(),
                user.getEmail()
        );
    }
}
