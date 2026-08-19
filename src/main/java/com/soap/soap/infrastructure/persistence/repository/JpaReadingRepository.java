package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.ReadingEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaReadingRepository extends JpaRepository<ReadingEntity, UUID> {

  @Override
  @EntityGraph(attributePaths = "user")
  Optional<ReadingEntity> findById(UUID id);

  @EntityGraph(attributePaths = "user")
  Page<ReadingEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
