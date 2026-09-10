package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.ReadingCollectionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaReadingCollectionRepository
    extends JpaRepository<ReadingCollectionEntity, UUID> {
  List<ReadingCollectionEntity> findByActiveTrueOrderByDisplayOrderAscIdAsc();

  Optional<ReadingCollectionEntity> findByKeyAndActiveTrue(String key);
}
