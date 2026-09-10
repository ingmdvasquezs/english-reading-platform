package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.OnboardingReadingEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaOnboardingReadingRepository
    extends JpaRepository<OnboardingReadingEntity, Long> {

  Optional<OnboardingReadingEntity> findFirstByActiveTrueOrderByVersionDescCreatedAtDescIdDesc();
}
