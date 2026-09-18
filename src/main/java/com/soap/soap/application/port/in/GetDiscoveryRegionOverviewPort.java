package com.soap.soap.application.port.in;

import com.soap.soap.application.model.DiscoveryRegionOverviewResult;

public interface GetDiscoveryRegionOverviewPort {
  DiscoveryRegionOverviewResult getOverview(String regionKey);
}
