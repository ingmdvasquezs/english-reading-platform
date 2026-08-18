package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.ReadingEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaReadingRepository extends JpaRepository<ReadingEntity, UUID> {

  @Override
  @EntityGraph(attributePaths = "user")
  Optional<ReadingEntity> findById(UUID id);

  @EntityGraph(attributePaths = "user")
  List<ReadingEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
