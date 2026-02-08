package com.springboot.finalprojcet.domain.user.repository;

import com.springboot.finalprojcet.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
    
    Optional<UserPreferences> findByUserUserId(Long userId);
    
    boolean existsByUserUserId(Long userId);
}
