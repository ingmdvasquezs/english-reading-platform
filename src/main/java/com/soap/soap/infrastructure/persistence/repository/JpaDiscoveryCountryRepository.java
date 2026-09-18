package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.DiscoveryCountryEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaDiscoveryCountryRepository extends JpaRepository<DiscoveryCountryEntity, UUID> {
  @Query(
      "SELECT DISTINCT c FROM DiscoveryCountryEntity c LEFT JOIN FETCH c.heroImages WHERE"
          + " c.region.id = :regionId AND c.active = true ORDER BY c.displayOrder ASC,"
          + " c.countryCode ASC")
  List<DiscoveryCountryEntity> findByRegionIdAndActiveTrueWithHeroImages(
      @Param("regionId") UUID regionId);

  Optional<DiscoveryCountryEntity> findByCountryCode(String countryCode);
}
