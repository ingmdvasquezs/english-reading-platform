package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.DiscoveryCountry;
import com.soap.soap.domain.model.DiscoveryRegion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscoveryRegionRepositoryPort {
  Optional<DiscoveryRegion> findActiveRegionByKey(String regionKey);

  List<DiscoveryCountry> findActiveCountriesByRegionId(UUID regionId);

  DiscoveryRegion saveRegion(DiscoveryRegion region);

  DiscoveryCountry saveCountry(DiscoveryCountry country);
}
