package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.DiscoveryRegionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDiscoveryRegionRepository extends JpaRepository<DiscoveryRegionEntity, UUID> {
  Optional<DiscoveryRegionEntity> findByKeyAndActiveTrue(String key);

  Optional<DiscoveryRegionEntity> findByKey(String key);
}
