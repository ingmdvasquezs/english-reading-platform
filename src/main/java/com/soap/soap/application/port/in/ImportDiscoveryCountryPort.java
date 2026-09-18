package com.soap.soap.application.port.in;

import com.soap.soap.domain.model.DiscoveryCountry;

public interface ImportDiscoveryCountryPort {
  DiscoveryCountry importCountry(String regionKey, DiscoveryCountry country);
}
