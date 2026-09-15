package com.soap.soap.domain.model;

/**
 * Access tier for platform readings. Required on PLATFORM readings, strictly null on USER readings.
 */
public enum AccessTier {
  FREE,
  PREMIUM
}
