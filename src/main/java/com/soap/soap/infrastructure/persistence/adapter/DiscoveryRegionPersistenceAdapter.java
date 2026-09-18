package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.port.out.DiscoveryRegionRepositoryPort;
import com.soap.soap.domain.model.DiscoveryCountry;
import com.soap.soap.domain.model.DiscoveryHeroImage;
import com.soap.soap.domain.model.DiscoveryRegion;
import com.soap.soap.infrastructure.persistence.entity.DiscoveryCountryEntity;
import com.soap.soap.infrastructure.persistence.entity.DiscoveryHeroImageEntity;
import com.soap.soap.infrastructure.persistence.entity.DiscoveryRegionEntity;
import com.soap.soap.infrastructure.persistence.repository.JpaDiscoveryCountryRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaDiscoveryRegionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DiscoveryRegionPersistenceAdapter implements DiscoveryRegionRepositoryPort {

  private final JpaDiscoveryRegionRepository regionRepository;
  private final JpaDiscoveryCountryRepository countryRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<DiscoveryRegion> findActiveRegionByKey(String regionKey) {
    if (regionKey == null || regionKey.isBlank()) {
      return Optional.empty();
    }
    return regionRepository
        .findByKeyAndActiveTrue(regionKey.trim())
        .map(
            entity ->
                new DiscoveryRegion(
                    entity.getId(),
                    entity.getKey(),
                    entity.getDisplayName(),
                    entity.getSubtitle(),
                    entity.getDisplayOrder(),
                    entity.isActive()));
  }

  @Override
  @Transactional(readOnly = true)
  public List<DiscoveryCountry> findActiveCountriesByRegionId(UUID regionId) {
    if (regionId == null) {
      return List.of();
    }
    List<DiscoveryCountryEntity> entities =
        countryRepository.findByRegionIdAndActiveTrueWithHeroImages(regionId);
    return entities.stream()
        .map(
            entity -> {
              List<DiscoveryHeroImage> images =
                  entity.getHeroImages().stream()
                      .map(
                          img ->
                              new DiscoveryHeroImage(
                                  img.getId(),
                                  img.getAssetKey(),
                                  img.getLocation(),
                                  img.getAlt(),
                                  img.getDisplayOrder()))
                      .toList();
              return new DiscoveryCountry(
                  entity.getId(),
                  entity.getCountryCode(),
                  entity.getRegion().getId(),
                  entity.getDisplayName(),
                  entity.getTagline(),
                  entity.getDescription(),
                  entity.getDisplayOrder(),
                  entity.isActive(),
                  images);
            })
        .toList();
  }

  @Override
  @Transactional
  public DiscoveryRegion saveRegion(DiscoveryRegion region) {
    var entity =
        regionRepository
            .findByKey(region.key())
            .orElseGet(
                () -> {
                  var e = new DiscoveryRegionEntity();
                  e.setKey(region.key());
                  return e;
                });
    entity.setDisplayName(region.displayName());
    entity.setSubtitle(region.subtitle());
    entity.setDisplayOrder(region.displayOrder());
    entity.setActive(region.active());

    var saved = regionRepository.save(entity);
    return new DiscoveryRegion(
        saved.getId(),
        saved.getKey(),
        saved.getDisplayName(),
        saved.getSubtitle(),
        saved.getDisplayOrder(),
        saved.isActive());
  }

  @Override
  @Transactional
  public DiscoveryCountry saveCountry(DiscoveryCountry country) {
    var regionEntity =
        regionRepository
            .findById(country.regionId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Region not found with id: " + country.regionId()));

    var entity =
        countryRepository
            .findByCountryCode(country.countryCode())
            .orElseGet(
                () -> {
                  var e = new DiscoveryCountryEntity();
                  e.setCountryCode(country.countryCode());
                  return e;
                });
    entity.setRegion(regionEntity);
    entity.setDisplayName(country.displayName());
    entity.setTagline(country.tagline());
    entity.setDescription(country.description());
    entity.setDisplayOrder(country.displayOrder());
    entity.setActive(country.active());

    entity.getHeroImages().clear();
    if (country.heroImages() != null) {
      for (DiscoveryHeroImage img : country.heroImages()) {
        var imgEntity = new DiscoveryHeroImageEntity();
        imgEntity.setCountry(entity);
        imgEntity.setAssetKey(img.assetKey());
        imgEntity.setLocation(img.location());
        imgEntity.setAlt(img.alt());
        imgEntity.setDisplayOrder(img.displayOrder());
        entity.getHeroImages().add(imgEntity);
      }
    }

    var saved = countryRepository.save(entity);
    List<DiscoveryHeroImage> images =
        saved.getHeroImages().stream()
            .map(
                img ->
                    new DiscoveryHeroImage(
                        img.getId(),
                        img.getAssetKey(),
                        img.getLocation(),
                        img.getAlt(),
                        img.getDisplayOrder()))
            .toList();

    return new DiscoveryCountry(
        saved.getId(),
        saved.getCountryCode(),
        saved.getRegion().getId(),
        saved.getDisplayName(),
        saved.getTagline(),
        saved.getDescription(),
        saved.getDisplayOrder(),
        saved.isActive(),
        images);
  }
}
