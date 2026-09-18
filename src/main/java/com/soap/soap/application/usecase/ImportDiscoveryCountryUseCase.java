package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.DiscoveryRegionNotFoundException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.port.in.ImportDiscoveryCountryPort;
import com.soap.soap.application.port.out.DiscoveryRegionRepositoryPort;
import com.soap.soap.domain.model.DiscoveryCountry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ImportDiscoveryCountryUseCase implements ImportDiscoveryCountryPort {

  private final DiscoveryRegionRepositoryPort repository;

  @Override
  @Transactional
  public DiscoveryCountry importCountry(String regionKey, DiscoveryCountry country) {
    if (regionKey == null || regionKey.isBlank()) {
      throw new InvalidApplicationArgumentException("Region key must not be blank");
    }
    if (country == null) {
      throw new InvalidApplicationArgumentException("Country must not be null");
    }
    var region =
        repository
            .findActiveRegionByKey(regionKey.trim())
            .orElseThrow(() -> new DiscoveryRegionNotFoundException(regionKey.trim()));

    var toSave =
        new DiscoveryCountry(
            country.id(),
            country.countryCode(),
            region.id(),
            country.displayName(),
            country.tagline(),
            country.description(),
            country.displayOrder(),
            country.active(),
            country.heroImages());

    return repository.saveCountry(toSave);
  }
}
