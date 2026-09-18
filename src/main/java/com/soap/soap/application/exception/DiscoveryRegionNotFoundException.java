package com.soap.soap.application.exception;

public class DiscoveryRegionNotFoundException extends RuntimeException {
  public DiscoveryRegionNotFoundException(String regionKey) {
    super("Discovery region not found: " + regionKey);
  }
}
