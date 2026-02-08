package com.springboot.finalprojcet.config;

import com.springboot.finalprojcet.domain.user.repository.UserRepository;
import com.springboot.finalprojcet.entity.Users;
import com.springboot.finalprojcet.enums.RoleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        String masterEmail = "jowoosung@gmail.com";
        log.info("Checking for MASTER account: {}", masterEmail);

        userRepository.findByEmail(masterEmail).ifPresentOrElse(
                user -> {
                    if (user.getRoleType() != RoleType.MASTER || !"1".equals(user.getGrade())) {
                        user.setRoleType(RoleType.MASTER);
                        user.setGrade("1");
                        userRepository.save(user);
                        log.info("Updated exiting user {} to MASTER role and Grade 1.", masterEmail);
                    } else {
                        log.info("User {} is already MASTER and Grade 1.", masterEmail);
                    }
                },
                () -> {
                    log.warn(
                            "MASTER account {} not found. Creating a placeholder account if needed, but skipping for now to rely on registration.",
                            masterEmail);
                    // Optionally create if not exists, but password handling is tricky.
                    // Keeping it simple: user must register, then this runner (on restart) or
                    // manual update will fix it.
                    // Or I can create it with a dummy password if critical.
                    // Given the user said "This account is master", I assume it might exist or they
                    // will create it.
                    // Better: Create it if missing to ensure access? No, password hash logic is in
                    // Service.
                    // I'll stick to updating existing.
                });
    }
}
