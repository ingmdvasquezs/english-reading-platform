package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.port.in.ImportDiscoveryRegionPort;
import com.soap.soap.application.port.out.DiscoveryRegionRepositoryPort;
import com.soap.soap.domain.model.DiscoveryRegion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ImportDiscoveryRegionUseCase implements ImportDiscoveryRegionPort {

  private final DiscoveryRegionRepositoryPort repository;

  @Override
  @Transactional
  public DiscoveryRegion importRegion(DiscoveryRegion region) {
    if (region == null) {
      throw new InvalidApplicationArgumentException("Region must not be null");
    }
    if (region.key() == null || region.key().isBlank()) {
      throw new InvalidApplicationArgumentException("Region key must not be blank");
    }
    if (region.displayName() == null || region.displayName().isBlank()) {
      throw new InvalidApplicationArgumentException("Region display name must not be blank");
    }
    return repository.saveRegion(region);
  }
}
