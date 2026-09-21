package com.soap.soap.application.port.in;

import com.soap.soap.application.model.DiscoveryHomeResult;
import com.soap.soap.application.model.GetDiscoveryHomeQuery;

public interface GetDiscoveryHomePort {
  DiscoveryHomeResult getDiscoveryHome(GetDiscoveryHomeQuery query);
}
