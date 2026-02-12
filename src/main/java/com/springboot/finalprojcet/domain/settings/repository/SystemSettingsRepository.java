package com.springboot.finalprojcet.domain.settings.repository;

import com.springboot.finalprojcet.entity.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingsRepository extends JpaRepository<SystemSettings, String> {
}
