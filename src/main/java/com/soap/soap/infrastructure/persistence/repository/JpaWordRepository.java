package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.WordEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaWordRepository extends JpaRepository<WordEntity, UUID> {

  Optional<WordEntity> findByNormalizedValueAndLanguage(String normalizedValue, String language);

  boolean existsByNormalizedValueAndLanguage(String normalizedValue, String language);
}
