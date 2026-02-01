package com.springboot.finalprojcet.domain.analysis.repository;

import com.springboot.finalprojcet.entity.UserProfiles;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfilesRepository extends JpaRepository<UserProfiles, Long> {
}
