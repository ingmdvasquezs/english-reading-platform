package com.soap.soap.application.port.in;

import com.soap.soap.domain.model.DiscoveryRegion;

public interface ImportDiscoveryRegionPort {
  DiscoveryRegion importRegion(DiscoveryRegion region);
}
